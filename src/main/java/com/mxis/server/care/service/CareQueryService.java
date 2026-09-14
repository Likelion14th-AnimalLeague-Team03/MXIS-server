package com.mxis.server.care.service;

import com.mxis.server.care.dto.AiCareSummaryResponse;
import com.mxis.server.care.dto.AnalysisWindow;
import com.mxis.server.care.dto.CareDashboardResponse;
import com.mxis.server.care.dto.CareEnvironmentResponse;
import com.mxis.server.care.dto.CareReportResponse;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.dto.SensorSummaryResponse;
import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.entity.CareSuggestion;
import com.mxis.server.care.repository.CareReportRepository;
import com.mxis.server.care.repository.CareSuggestionRepository;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.device.entity.Device;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.sensor.dto.SensorAggregate;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareQueryService {

    private static final int DRY_EXPOSURE_DAYS = 7;
    private static final int MONTHS_PER_YEAR = 12;
    private static final int SNAPSHOT_MAX_AGE_MINUTES = 5;

    private final ProductRepository productRepository;
    private final ProductDeviceRepository productDeviceRepository;
    private final CareReportRepository careReportRepository;
    private final CareSuggestionRepository careSuggestionRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final CareRuleEngine ruleEngine;
    private final CareDecisionPolicy decisionPolicy;
    private final CareDiagnosisJobService diagnosisJobs;
    private final AiResponseMapper aiResponseMapper;
    private final Clock clock;

    /** Reads a completed snapshot; expensive AI work is always queued outside the HTTP request. */
    @Transactional
    public AiCareSummaryResponse getAiCareSummary(Long userId, Long productId, SensorPeriod period) {
        getOwnedProduct(userId, productId);
        LocalDateTime now = LocalDateTime.now(clock);
        AnalysisWindow window = AnalysisWindow.rolling(period, now);
        ReadingStats stats = readingStats(productId, window.from(), window.to());
        AiCareSummaryResponse.DataSufficiency sufficiency = dataSufficiency(stats, now);
        CareReport report = careReportRepository
                .findFirstByProductIdAndAnalysisWindowDaysOrderByPeriodEndDescIdDesc(productId, period.days())
                .orElse(null);
        if (report != null && report.getSensorRevision() != null && report.getSensorRevision() == stats.revision()
                && !report.getPeriodEnd().isBefore(now.minusMinutes(SNAPSHOT_MAX_AGE_MINUTES))
                && !report.getPeriodEnd().isAfter(now)) {
            try {
                MxisAiClient.CareSummaryResult result = aiResponseMapper.read(productId, period, report.getAiOutput());
                AiCareSummaryResponse saved = result.summary();
                long inputCount = result.root().path("backendSnapshot").path("readingCount")
                        .asLong(saved.dataSufficiency().validReadingCount());
                if (inputCount == stats.rawReadingCount()
                        && !("STALE_DATA".equals(sufficiency.status())
                             && "SUFFICIENT".equals(saved.dataSufficiency().status()))) return saved;
            } catch (AiServiceException ex) {
                log.warn("Stored care snapshot requires regeneration. reportId={}", report.getId());
            }
        }
        diagnosisJobs.request(productId, period, now);
        return decisionPolicy.fallback(productId, period, now,
                aggregate(productId, window.from(), window.to()), sufficiency);
    }

    public CareEnvironmentResponse getCareEnvironment(Long userId, Long productId, SensorPeriod period) {
        getOwnedProduct(userId, productId);
        return environmentSnapshot(productId, period, LocalDateTime.now(clock)).response();
    }

    /** Caller must validate ownership; screen aggregation reuses these exact bounds and measurements. */
    EnvironmentSnapshot environmentSnapshot(Long productId, SensorPeriod period, LocalDateTime now) {
        AnalysisWindow window = AnalysisWindow.environment(period, now);
        SensorAggregate aggregate = aggregate(productId, window.from(), window.to());
        AiCareSummaryResponse.DataSufficiency sufficiency = dataSufficiency(
                readingStats(productId, window.from(), window.to()), now);
        AiCareSummaryResponse.StressLabels stress = decisionPolicy
                .fallback(productId, period, now, aggregate, sufficiency).stressLabels();
        CareEnvironmentResponse response = new CareEnvironmentResponse(
                productId, period, now, sufficiency,
                new CareEnvironmentResponse.EnvironmentSummary(scale(aggregate.avgTemperature()),
                        scale(aggregate.avgHumidity()), stress.humidity(), stress.temperatureHeat(),
                        stress.dryness(), stress.handling(), stress.uvLight()),
                environmentPoints(productId, period, window),
                new CareEnvironmentResponse.EnvironmentCopy(
                        "그래프의 순간값보다 안정 범위를 벗어난 누적 시간이 관리 판단에 더 중요합니다.",
                        List.of("7D는 일일 평균 7개, 30D는 3일 평균 10개, 1Y는 월 평균 12개로 구성됩니다.",
                                "현재 센서는 UV/light와 표면 증상을 직접 측정하지 않습니다.")));
        return new EnvironmentSnapshot(response, aggregate, window);
    }

    record EnvironmentSnapshot(CareEnvironmentResponse response, SensorAggregate aggregate, AnalysisWindow window) { }

    public CareDashboardResponse getDashboard(Long userId, Long productId) {
        Product product = getOwnedProduct(userId, productId);
        CareReport report = latestReport(productId);

        Device primaryDevice = productDeviceRepository.findActivePrimaryByProductId(productId)
                .map(ProductDevice::getDevice)
                .orElse(null);

        CareSuggestion suggestion = careSuggestionRepository.findLatestActiveByProductId(productId).orElse(null);

        return new CareDashboardResponse(
                new CareDashboardResponse.ProductSummary(
                        product.getId(), product.getProductName(), product.getMaterialId(),
                        product.getMaterialDisplayName(),
                        product.getColor(), product.getProductImageUrl()),
                new CareDashboardResponse.DeviceSummary(
                        primaryDevice == null ? null : primaryDevice.getConnectionStatus(),
                        primaryDevice == null ? null : primaryDevice.getLastSyncedAt()),
                report.getConditionGrade(),
                report.getSummaryText(),
                report.getAnalysisText(),
                new CareDashboardResponse.EnvironmentSummary(
                        "최근 %d일 동안의 평균이에요".formatted(CareDiagnosisService.REPORT_PERIOD_DAYS),
                        new CareDashboardResponse.Measure(
                                report.getAvgTemperature(), ruleEngine.temperatureLabel(report.getAvgTemperature())),
                        new CareDashboardResponse.Measure(
                                report.getAvgHumidity(),
                                ruleEngine.humidityGrade(report.getAvgHumidity()).label()),
                        orZero(report.getOutingCount()),
                        ruleEngine.shockGrade(orZero(report.getShockCount())).label()),
                suggestion == null ? null : new CareDashboardResponse.ActiveSuggestion(
                        suggestion.getId(), suggestion.getMessage(), suggestion.getReasonText()));
    }

    /**
     * 저장된 30일 스냅샷에, 7일 건조노출·함께한시간을 조회 시점에 실시간 계산해 합쳐 반환한다.
     * (care_reports 스키마를 늘리지 않기 위한 의도적 선택)
     */
    public CareReportResponse getLatestReport(Long userId, Long productId) {
        Product product = getOwnedProduct(userId, productId);
        CareReport report = latestReport(productId);

        LocalDateTime now = LocalDateTime.now(clock);
        SensorAggregate recent = sensorReadingRepository.aggregate(
                productId, now.minusDays(DRY_EXPOSURE_DAYS), now,
                CareRuleEngine.DRY_THRESHOLD, CareRuleEngine.STRONG_SHOCK_THRESHOLD);

        String period = "최근 %d일".formatted(CareDiagnosisService.REPORT_PERIOD_DAYS);

        return new CareReportResponse(
                report.getConditionGrade(),
                report.getSummaryText(),
                report.getAnalysisText(),
                new CareReportResponse.EnvironmentSummary(
                        new CareReportResponse.PeriodMeasure(
                                report.getAvgHumidity(), period,
                                ruleEngine.humidityGrade(report.getAvgHumidity()).label()),
                        new CareReportResponse.PeriodMeasure(
                                report.getAvgTemperature(), period,
                                ruleEngine.temperatureLabel(report.getAvgTemperature())),
                        new CareReportResponse.DryExposure(
                                "최근 %d일".formatted(DRY_EXPOSURE_DAYS),
                                ruleEngine.dryExposureLabel(recent.dryRatio()))),
                new CareReportResponse.UsagePattern(
                        timeTogether(product),
                        orZero(report.getOutingCount()),
                        orZero(report.getShockCount())),
                report.getRecommendationText(),
                report.getCreatedAt());
    }

    /** sensor_readings를 직접 집계하는 라이브 조회. care_reports를 거치지 않고 저장도 하지 않는다. */
    public SensorSummaryResponse getSensorSummary(Long userId, Long productId, SensorPeriod period) {
        getOwnedProduct(userId, productId);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime from = now.minusDays(period.days());
        LocalDateTime previousFrom = from.minusDays(period.days());

        SensorAggregate current = aggregate(productId, from, now);
        SensorAggregate previous = aggregate(productId, previousFrom, from);

        int outing = (int) sensorReadingRepository.countOutingSessions(productId, from, now);
        int shock = current.shockCountAsInt();
        boolean insufficientHistory = previous.isEmpty();

        return new SensorSummaryResponse(
                period,
                humidityTrend(period, productId, from, now),
                scale(current.avgTemperature()),
                scale(current.avgHumidity()),
                period.isYear() ? null : outing,
                period.isYear() ? null : shock,
                period.isYear() ? outing / MONTHS_PER_YEAR : null,
                period.isYear() ? shock / MONTHS_PER_YEAR : null,
                comparisonText(period, current, previous, insufficientHistory),
                insufficientHistory);
    }

    private SensorAggregate aggregate(Long productId, LocalDateTime from, LocalDateTime to) {
        return sensorReadingRepository.aggregate(
                productId, from, to, CareRuleEngine.DRY_THRESHOLD, CareRuleEngine.STRONG_SHOCK_THRESHOLD);
    }

    private ReadingStats readingStats(Long productId, LocalDateTime from, LocalDateTime to) {
        Object[] row = sensorReadingRepository.findReadingStats(productId, from, to);
        if (row.length == 1 && row[0] instanceof Object[] nested) row = nested;
        return new ReadingStats(row[0] == null ? 0 : ((Number) row[0]).longValue(),
                toLocalDateTime(row[1]), toLocalDateTime(row[2]), toLocalDateTime(row[3]),
                row.length < 5 || row[4] == null ? 0 : ((Number) row[4]).longValue(),
                row.length < 6 ? ((Number) row[0]).longValue() : ((Number) row[5]).longValue());
    }

    private AiCareSummaryResponse.DataSufficiency dataSufficiency(ReadingStats stats, LocalDateTime now) {
        return decisionPolicy.dataSufficiency(stats.validReadingCount(), stats.firstMeasuredAt(),
                stats.lastMeasuredAt(), stats.lastSyncedAt(), now);
    }

    private List<CareEnvironmentResponse.EnvironmentPoint> environmentPoints(
            Long productId, SensorPeriod period, AnalysisWindow window) {
        if (period == SensorPeriod.SEVEN_DAYS) {
            return dailyEnvironmentPoints(sensorReadingRepository.findDailyEnvironment(
                    productId, window.from(), window.to()), window.from().toLocalDate());
        }
        if (period == SensorPeriod.THIRTY_DAYS) {
            return threeDayEnvironmentPoints(sensorReadingRepository.findThreeDayEnvironment(
                    productId, window.from(), window.to()), window.from().toLocalDate());
        }
        return monthlyEnvironmentPoints(sensorReadingRepository.findMonthlyEnvironment(
                productId, window.from(), window.to()), window.from().toLocalDate());
    }

    private List<CareEnvironmentResponse.EnvironmentPoint> dailyEnvironmentPoints(List<Object[]> rows, LocalDate start) {
        Map<LocalDate, EnvAggregate> byDate = new HashMap<>();
        for (Object[] row : rows) {
            byDate.put(((Date) row[0]).toLocalDate(), envAggregate(row[1], row[2], row[3]));
        }

        List<CareEnvironmentResponse.EnvironmentPoint> points = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = start.plusDays(i);
            EnvAggregate aggregate = byDate.getOrDefault(day, EnvAggregate.empty());
            points.add(new CareEnvironmentResponse.EnvironmentPoint(
                    day.toString(), day, day, aggregate.avgTemperature(), aggregate.avgHumidity(), aggregate.readingCount()));
        }
        return points;
    }

    private List<CareEnvironmentResponse.EnvironmentPoint> threeDayEnvironmentPoints(List<Object[]> rows, LocalDate start) {
        Map<Integer, EnvAggregate> byBucket = new HashMap<>();
        for (Object[] row : rows) {
            int bucket = ((Number) row[0]).intValue();
            byBucket.put(bucket, envAggregate(row[1], row[2], row[3]));
        }

        List<CareEnvironmentResponse.EnvironmentPoint> points = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            LocalDate from = start.plusDays((long) i * 3);
            LocalDate to = from.plusDays(2);
            EnvAggregate aggregate = byBucket.getOrDefault(i, EnvAggregate.empty());
            points.add(new CareEnvironmentResponse.EnvironmentPoint(
                    "%s~%s".formatted(from, to), from, to,
                    aggregate.avgTemperature(), aggregate.avgHumidity(), aggregate.readingCount()));
        }
        return points;
    }

    private List<CareEnvironmentResponse.EnvironmentPoint> monthlyEnvironmentPoints(List<Object[]> rows, LocalDate start) {
        Map<LocalDate, EnvAggregate> byMonth = new HashMap<>();
        for (Object[] row : rows) {
            byMonth.put(((Date) row[0]).toLocalDate(), envAggregate(row[1], row[2], row[3]));
        }

        List<CareEnvironmentResponse.EnvironmentPoint> points = new ArrayList<>();
        for (int i = 0; i < MONTHS_PER_YEAR; i++) {
            LocalDate month = start.plusMonths(i);
            LocalDate to = month.plusMonths(1).minusDays(1);
            EnvAggregate aggregate = byMonth.getOrDefault(month, EnvAggregate.empty());
            points.add(new CareEnvironmentResponse.EnvironmentPoint(
                    YearMonth.from(month).toString(), month, to,
                    aggregate.avgTemperature(), aggregate.avgHumidity(), aggregate.readingCount()));
        }
        return points;
    }

    private EnvAggregate envAggregate(Object avgTemperature, Object avgHumidity, Object readingCount) {
        return new EnvAggregate(
                avgTemperature == null ? null : BigDecimal.valueOf(((Number) avgTemperature).doubleValue()).setScale(1, RoundingMode.HALF_UP),
                avgHumidity == null ? null : BigDecimal.valueOf(((Number) avgHumidity).doubleValue()).setScale(1, RoundingMode.HALF_UP),
                readingCount == null ? 0L : ((Number) readingCount).longValue());
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return null;
    }

    /**
     * 기간별로 그래프 포인트 개수·간격을 다르게 낸다 (api-spec.md "환경 데이터 상세" 샘플링 규칙 확정):
     * 7D는 일별 그대로, 30D는 변곡점만 추려서 오르내림 폭이 잘 보이게, 1Y는 월별 평균.
     */
    private List<SensorSummaryResponse.HumidityPoint> humidityTrend(
            SensorPeriod period, Long productId, LocalDateTime from, LocalDateTime to) {
        if (period.isYear()) {
            return toHumidityPoints(sensorReadingRepository.findMonthlyHumidity(productId, from, to));
        }

        List<SensorSummaryResponse.HumidityPoint> daily =
                toHumidityPoints(sensorReadingRepository.findDailyHumidity(productId, from, to));
        return period == SensorPeriod.THIRTY_DAYS ? extractTurningPoints(daily) : daily;
    }

    private List<SensorSummaryResponse.HumidityPoint> toHumidityPoints(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new SensorSummaryResponse.HumidityPoint(
                        ((Date) row[0]).toLocalDate(),
                        BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(1, RoundingMode.HALF_UP)))
                .toList();
    }

    /**
     * 변곡점(turning point) 추출: 양 끝은 항상 남기고, 중간은 추세가 꺾이는 지점(직전 구간과 다음 구간의
     * 증감 방향이 다른 지점)만 남긴다. 값이 계속 오르거나 내리기만 하는 구간은 양 끝만 남아 점이 줄고,
     * 오르내림이 잦은 구간은 점이 그만큼 남아 상승·하강 폭이 그래프에 그대로 드러난다.
     */
    /** package-private for direct unit testing (순수 함수, DB 접근 없음). */
    List<SensorSummaryResponse.HumidityPoint> extractTurningPoints(
            List<SensorSummaryResponse.HumidityPoint> points) {
        if (points.size() <= 2) {
            return points;
        }

        List<SensorSummaryResponse.HumidityPoint> result = new ArrayList<>();
        result.add(points.get(0));

        for (int i = 1; i < points.size() - 1; i++) {
            BigDecimal prev = points.get(i - 1).value();
            BigDecimal curr = points.get(i).value();
            BigDecimal next = points.get(i + 1).value();

            int risingIntoI = curr.compareTo(prev);
            int risingFromI = next.compareTo(curr);
            boolean isTurningPoint = risingIntoI != 0 && risingFromI != 0
                    && Integer.signum(risingIntoI) != Integer.signum(risingFromI);

            if (isTurningPoint) {
                result.add(points.get(i));
            }
        }

        result.add(points.get(points.size() - 1));
        return result;
    }

    /** 직전 동일 기간 대비 비교 문구. 규칙 기반 템플릿이며 AI를 쓰지 않는다. */
    private String comparisonText(SensorPeriod period, SensorAggregate current, SensorAggregate previous,
                                  boolean insufficientHistory) {
        if (insufficientHistory) {
            return period.isYear()
                    ? "MXIS와 함께한 지 아직 1년이 되지 않았습니다. 시간이 쌓일수록 계절의 흐름 속에서 변화해온 모습을 온전히 보여드릴 수 있습니다."
                    : "비교할 이전 기간의 데이터가 아직 충분하지 않습니다.";
        }
        if (current.avgHumidity() == null || previous.avgHumidity() == null) {
            return "이전 기간과 비교할 습도 데이터가 충분하지 않습니다.";
        }

        double delta = current.avgHumidity() - previous.avgHumidity();
        String humidityTrend = delta > 5 ? "습도가 다소 높아졌지만" : delta < -5 ? "습도가 다소 낮아졌지만" : "습도 변화는 크지 않았고";
        String shockTrend = current.shockCountAsInt() > previous.shockCountAsInt()
                ? "충격 감지는 이전보다 늘었습니다." : "충격 감지는 안정적인 수준이었습니다.";

        return "이전 기간보다 %s, %s".formatted(humidityTrend, shockTrend);
    }

    /** products.purchased_at(없으면 등록일) 기준 "N년 M개월". */
    private String timeTogether(Product product) {
        LocalDate since = product.getPurchasedAt() != null
                ? product.getPurchasedAt()
                : product.getRegisteredAt().toLocalDate();
        Period elapsed = Period.between(since, LocalDate.now());

        if (elapsed.isNegative()) {
            return "0개월";
        }
        if (elapsed.getYears() == 0) {
            return "%d개월".formatted(elapsed.getMonths());
        }
        return "%d년 %d개월".formatted(elapsed.getYears(), elapsed.getMonths());
    }

    private CareReport latestReport(Long productId) {
        return careReportRepository.findFirstByProductIdOrderByCreatedAtDesc(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_DIAGNOSIS_DATA));
    }

    private Product getOwnedProduct(Long userId, Long productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_OWNED);
        }
        return product;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal scale(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private record ReadingStats(long validReadingCount, LocalDateTime firstMeasuredAt,
                                LocalDateTime lastMeasuredAt, LocalDateTime lastSyncedAt, long revision, long rawReadingCount) { }

    private record EnvAggregate(
            BigDecimal avgTemperature,
            BigDecimal avgHumidity,
            long readingCount
    ) {
        private static EnvAggregate empty() {
            return new EnvAggregate(null, null, 0);
        }
    }
}
