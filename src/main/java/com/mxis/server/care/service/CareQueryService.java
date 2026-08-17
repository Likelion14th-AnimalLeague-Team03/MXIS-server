package com.mxis.server.care.service;

import com.mxis.server.care.dto.AiCareSummaryResponse;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareQueryService {

    private static final int DRY_EXPOSURE_DAYS = 7;
    private static final int MONTHS_PER_YEAR = 12;
    private static final long MIN_VALID_READING_COUNT = 24;
    private static final double MIN_COVERAGE_HOURS = 24.0;

    private final ProductRepository productRepository;
    private final ProductDeviceRepository productDeviceRepository;
    private final CareReportRepository careReportRepository;
    private final CareSuggestionRepository careSuggestionRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final CareRuleEngine ruleEngine;

    public AiCareSummaryResponse getAiCareSummary(Long userId, Long productId, SensorPeriod period) {
        getOwnedProduct(userId, productId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusDays(period.days());
        SensorAggregate aggregate = aggregate(productId, from, now);
        ReadingStats stats = readingStats(productId, from, now);
        AiCareSummaryResponse.DataSufficiency dataSufficiency = dataSufficiency(stats);

        StressDecision stress = stressDecision(aggregate);
        boolean sufficient = "SUFFICIENT".equals(dataSufficiency.status());
        Integer score = sufficient ? score(stress) : null;
        String label = sufficient ? conditionLabel(score) : "Collecting Data";
        String primaryFactor = sufficient ? primaryFactor(stress) : null;
        String summary = sufficient
                ? summaryText(stress)
                : "제품 상태 분석을 위해 데이터를 수집하고 있습니다.";

        return new AiCareSummaryResponse(
                productId,
                now,
                period.days(),
                dataSufficiency,
                new AiCareSummaryResponse.ProductCondition(label, score, primaryFactor, summary),
                new AiCareSummaryResponse.StressLabels(
                        stress.humidity(),
                        stress.temperatureHeat(),
                        stress.dryness(),
                        stress.handling(),
                        "LOW",
                        "UNKNOWN"),
                new AiCareSummaryResponse.Explanation(
                        summary,
                        explanationBullets(sufficient, stress),
                        List.of(
                                "MVP 센서는 UV/light를 직접 측정하지 않습니다.",
                                "표면 손상, 곰팡이, 균열은 센서만으로 확정하지 않습니다.")),
                new AiCareSummaryResponse.CopyGeneration(
                        "deterministic_fallback",
                        null,
                        null));
    }

    public CareEnvironmentResponse getCareEnvironment(Long userId, Long productId, SensorPeriod period) {
        getOwnedProduct(userId, productId);

        LocalDateTime now = LocalDateTime.now();
        Window window = environmentWindow(period, now);
        SensorAggregate aggregate = aggregate(productId, window.from(), window.to());
        AiCareSummaryResponse.DataSufficiency dataSufficiency =
                dataSufficiency(readingStats(productId, window.from(), window.to()));
        StressDecision stress = stressDecision(aggregate);

        return new CareEnvironmentResponse(
                productId,
                period,
                now,
                dataSufficiency,
                new CareEnvironmentResponse.EnvironmentSummary(
                        scale(aggregate.avgTemperature()),
                        scale(aggregate.avgHumidity()),
                        stress.humidity(),
                        stress.temperatureHeat(),
                        stress.dryness(),
                        stress.handling(),
                        "UNKNOWN"),
                environmentPoints(productId, period, window),
                new CareEnvironmentResponse.EnvironmentCopy(
                        "그래프의 순간값보다 안정 범위를 벗어난 누적 시간이 관리 판단에 더 중요합니다.",
                        List.of(
                                "7D는 일일 평균 7개, 30D는 3일 평균 10개, 1Y는 월 평균 12개로 구성됩니다.",
                                "현재 센서는 UV/light와 표면 증상을 직접 측정하지 않습니다.")));
    }

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

        LocalDateTime now = LocalDateTime.now();
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

        LocalDateTime now = LocalDateTime.now();
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
        long count = row[0] == null ? 0L : ((Number) row[0]).longValue();
        LocalDateTime firstMeasuredAt = toLocalDateTime(row[1]);
        LocalDateTime lastMeasuredAt = toLocalDateTime(row[2]);
        LocalDateTime lastSyncedAt = toLocalDateTime(row[3]);
        Double coverageHours = firstMeasuredAt == null || lastMeasuredAt == null
                ? 0.0
                : ChronoUnit.MINUTES.between(firstMeasuredAt, lastMeasuredAt) / 60.0;
        return new ReadingStats(count, coverageHours, lastMeasuredAt, lastSyncedAt);
    }

    private AiCareSummaryResponse.DataSufficiency dataSufficiency(ReadingStats stats) {
        String status = "SUFFICIENT";
        String reason = null;
        if (stats.validReadingCount() == 0) {
            status = "NO_DATA";
            reason = "NO_VALID_READING";
        } else if (stats.validReadingCount() < MIN_VALID_READING_COUNT) {
            status = "INSUFFICIENT_DATA";
            reason = "MIN_READING_COUNT_NOT_MET";
        } else if (stats.coverageHours() < MIN_COVERAGE_HOURS) {
            status = "INSUFFICIENT_DATA";
            reason = "MIN_COVERAGE_HOURS_NOT_MET";
        }
        return new AiCareSummaryResponse.DataSufficiency(
                status,
                reason,
                stats.validReadingCount(),
                BigDecimal.valueOf(stats.coverageHours()).setScale(1, RoundingMode.HALF_UP).doubleValue(),
                stats.lastMeasuredAt(),
                stats.lastSyncedAt());
    }

    private StressDecision stressDecision(SensorAggregate aggregate) {
        BigDecimal avgHumidity = scale(aggregate.avgHumidity());
        BigDecimal avgTemperature = scale(aggregate.avgTemperature());
        String humidity = avgHumidity == null ? "UNKNOWN"
                : avgHumidity.compareTo(BigDecimal.valueOf(80)) >= 0 ? "ELEVATED"
                : avgHumidity.compareTo(BigDecimal.valueOf(65)) >= 0 ? "CAUTION"
                : "LOW";
        String temperatureHeat = avgTemperature == null ? "UNKNOWN"
                : avgTemperature.compareTo(BigDecimal.valueOf(30)) >= 0 ? "CAUTION"
                : "LOW";
        String dryness = aggregate.dryRatio() >= 0.2 ? "CAUTION" : "LOW";
        String handling = aggregate.shockCountAsInt() >= 3 ? "CAUTION" : "LOW";
        return new StressDecision(humidity, temperatureHeat, dryness, handling);
    }

    private Integer score(StressDecision stress) {
        int score = 100;
        score -= stressPenalty(stress.humidity());
        score -= stressPenalty(stress.temperatureHeat());
        score -= stressPenalty(stress.dryness());
        score -= stressPenalty(stress.handling());
        return Math.max(score, 0);
    }

    private int stressPenalty(String stress) {
        return switch (stress) {
            case "CAUTION" -> 8;
            case "ELEVATED" -> 18;
            case "HIGH" -> 35;
            case "INSPECTION_REQUIRED" -> 50;
            default -> 0;
        };
    }

    private String conditionLabel(int score) {
        if (score >= 85) {
            return "Excellent";
        }
        if (score >= 60) {
            return "Standard";
        }
        return "Needs Attention";
    }

    private String primaryFactor(StressDecision stress) {
        if (!"LOW".equals(stress.humidity()) && !"UNKNOWN".equals(stress.humidity())) {
            return "humidity";
        }
        if (!"LOW".equals(stress.temperatureHeat()) && !"UNKNOWN".equals(stress.temperatureHeat())) {
            return "temperature_heat";
        }
        if (!"LOW".equals(stress.dryness())) {
            return "dryness";
        }
        if (!"LOW".equals(stress.handling())) {
            return "handling";
        }
        return null;
    }

    private String summaryText(StressDecision stress) {
        String primaryFactor = primaryFactor(stress);
        if (primaryFactor == null) {
            return "현재 제공된 센서 데이터 기준으로 보관 환경은 대체로 안정적입니다.";
        }
        return switch (primaryFactor) {
            case "humidity" -> "최근 습도가 안정 범위를 벗어난 시간이 있어 보관 환경 조정이 권장됩니다.";
            case "temperature_heat" -> "최근 온도 노출이 높게 감지되어 열원과의 거리를 확인하는 것이 좋습니다.";
            case "dryness" -> "건조 노출이 누적되어 과도한 제습이나 건조한 보관 환경을 피하는 것이 좋습니다.";
            case "handling" -> "움직임 또는 충격 노출이 일부 감지되어 보관 위치를 확인하는 것이 좋습니다.";
            default -> "현재 센서 데이터 기준으로 예방 관리가 권장됩니다.";
        };
    }

    private List<String> explanationBullets(boolean sufficient, StressDecision stress) {
        if (!sufficient) {
            return List.of(
                    "최소 분석 기준을 채우려면 유효한 센서 데이터가 더 필요합니다.",
                    "데이터가 충분히 쌓이면 온습도와 움직임 노출을 함께 해석합니다.");
        }
        List<String> bullets = new ArrayList<>();
        if (!"LOW".equals(stress.humidity()) && !"UNKNOWN".equals(stress.humidity())) {
            bullets.add("제공된 센서 데이터 기준으로 습도가 안정 범위를 벗어난 시간이 확인되었습니다.");
        }
        if (!"LOW".equals(stress.temperatureHeat()) && !"UNKNOWN".equals(stress.temperatureHeat())) {
            bullets.add("온도 노출은 손상 확정이 아니라 보관 환경 점검을 위한 신호로 해석합니다.");
        }
        if (!"LOW".equals(stress.handling())) {
            bullets.add("IMU 데이터는 표면 손상 판단이 아니라 움직임/취급 노출의 참고 신호입니다.");
        }
        if (bullets.isEmpty()) {
            bullets.add("현재 데이터 기준으로 안정 범위를 크게 벗어난 누적 노출은 확인되지 않았습니다.");
        }
        bullets.add("현재 점검이 필요한 표면 증상은 센서만으로 판단하지 않습니다.");
        return bullets;
    }

    private Window environmentWindow(SensorPeriod period, LocalDateTime now) {
        if (period == SensorPeriod.ONE_YEAR) {
            LocalDate firstDayOfThisMonth = YearMonth.from(now).atDay(1);
            LocalDate from = firstDayOfThisMonth.minusMonths(11);
            LocalDate to = firstDayOfThisMonth.plusMonths(1);
            return new Window(from.atStartOfDay(), to.atStartOfDay());
        }

        LocalDate to = now.toLocalDate().plusDays(1);
        LocalDate from = to.minusDays(period.days());
        return new Window(from.atStartOfDay(), to.atStartOfDay());
    }

    private List<CareEnvironmentResponse.EnvironmentPoint> environmentPoints(
            Long productId, SensorPeriod period, Window window) {
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

    private record ReadingStats(
            long validReadingCount,
            double coverageHours,
            LocalDateTime lastMeasuredAt,
            LocalDateTime lastSyncedAt
    ) {
    }

    private record StressDecision(
            String humidity,
            String temperatureHeat,
            String dryness,
            String handling
    ) {
    }

    private record Window(LocalDateTime from, LocalDateTime to) {
    }

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
