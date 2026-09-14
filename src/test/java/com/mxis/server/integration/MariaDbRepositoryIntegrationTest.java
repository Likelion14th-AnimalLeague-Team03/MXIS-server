package com.mxis.server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.mxis.server.care.dto.AiCareSummaryResponse;
import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.repository.CareAlgorithmRepository;
import com.mxis.server.care.repository.CareReportRepository;
import com.mxis.server.care.repository.CareGuideRepository;
import com.mxis.server.common.enums.CareType;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.service.CareDecisionPolicy;
import com.mxis.server.care.service.CareRuleEngine;
import com.mxis.server.common.enums.AuthProvider;
import com.mxis.server.common.enums.CareConditionGrade;
import com.mxis.server.common.enums.ProductDeviceRole;
import com.mxis.server.config.JpaAuditingConfig;
import com.mxis.server.device.entity.Device;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.sensor.entity.SensorReading;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Runs real migrations and repository queries; never connects to the default application database. */
@Tag("integration")
@DataJpaTest
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, com.mxis.server.config.TimeConfig.class})
class MariaDbRepositoryIntegrationTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CareAlgorithmRepository algorithmRepository;
    @Autowired private CareReportRepository reportRepository;
    @Autowired private SensorReadingRepository sensorRepository;
    @Autowired private CareGuideRepository guideRepository;

    @Test
    void migrationsApplyAndJpaValidatesEntireSchema() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'SQL'", Integer.class)).isGreaterThanOrEqualTo(14);
    }

    @Test
    void migrationProvidesAllSevenGuidesWithMatchingImagesAndCompleteCopy() {
        for (CareType type : CareType.values()) {
            var guide = guideRepository.findFirstByCareTypeAndActiveTrue(type.code()).orElseThrow();
            assertThat(guide.getGuideImageUrl()).isEqualTo("http://161.33.38.65:8080/images/" + type.code() + ".png");
            assertThat(guide.getTitle()).isNotBlank();
            assertThat(guide.getDescription()).isNotBlank();
            assertThat(guide.getSteps()).hasSize(3).allSatisfy(step -> assertThat(step).isNotBlank());
            assertThat(guide.getTip()).isNotBlank();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM care_guides WHERE care_type = ? AND is_active = true", Integer.class, type.code()))
                    .isEqualTo(1);
        }
        assertThat(guideRepository.findFirstByCareTypeAndActiveTrue("ventilated_shade_storage").orElseThrow().getTitle())
                .isEqualTo("직사광선을 피해 통풍이 잘되는 곳에 보관하세요.");
    }

    @Test
    void localUserAndProductRoundTripPreserveDatabaseValues() {
        User user = userRepository.saveAndFlush(User.createLocal(
                "repository-test@mxis.example", "encoded-password", "Repository test", null));
        Product product = productRepository.saveAndFlush(new Product(user, "repository-test-dpp", "Test bag",
                null, "natural_leather", "Natural leather", List.of("vachetta"), null,
                null, null, null));
        entityManager.clear();

        assertThat(jdbcTemplate.queryForObject("SELECT provider FROM users WHERE id = ?", String.class, user.getId()))
                .isEqualTo("local");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getMaterialSubtypes()).containsExactly("vachetta");
    }

    @Test
    void persistedLowercaseSocialProviderLoadsIntoEnum() {
        User user = userRepository.saveAndFlush(User.createSocial(
                "social-test@mxis.example", AuthProvider.KAKAO, "repository-test-kakao", "Social test"));
        jdbcTemplate.update("UPDATE users SET provider = 'kakao' WHERE id = ?", user.getId());
        entityManager.clear();

        assertThat(userRepository.findById(user.getId()).orElseThrow().getProvider()).isEqualTo(AuthProvider.KAKAO);
    }

    @Test
    void legacyUppercaseProviderRemainsReadable() {
        User user = userRepository.saveAndFlush(User.createLocal(
                "legacy-test@mxis.example", "encoded-password", "Legacy test", null));
        jdbcTemplate.update("UPDATE users SET provider = 'LOCAL' WHERE id = ?", user.getId());
        entityManager.clear();

        assertThat(userRepository.findById(user.getId()).orElseThrow().getProvider()).isEqualTo(AuthProvider.LOCAL);
    }

    @Test
    void reportRoundTripPreservesActualScoreStatusWindowAndRevision() {
        Product product = createProduct();
        LocalDateTime end = LocalDateTime.of(2026, 9, 14, 12, 0);
        CareReport report = saveReport(product, end, 30, "SUFFICIENT", 83, CareConditionGrade.BALANCED);
        entityManager.clear();

        CareReport persisted = reportRepository.findById(report.getId()).orElseThrow();
        assertThat(persisted.getConditionScore()).isEqualTo(83);
        assertThat(persisted.getConditionLabel()).isEqualTo("Standard");
        assertThat(persisted.getDataStatus()).isEqualTo("SUFFICIENT");
        assertThat(persisted.getAnalysisWindowDays()).isEqualTo(30);
        assertThat(persisted.getPeriodStart()).isEqualTo(end.minusDays(30));
        assertThat(persisted.getPeriodEnd()).isEqualTo(end);
        assertThat(persisted.getSensorRevision()).isEqualTo(42L);
        assertThat(persisted.getCareNeed()).isEqualTo("LOW_MEDIUM");
    }

    @Test
    void insufficientReportPersistsCollectingGradeWithoutScore() {
        Product product = createProduct();
        CareReport report = saveReport(product, LocalDateTime.of(2026, 9, 14, 12, 0),
                30, "INSUFFICIENT_DATA", 92, CareConditionGrade.COLLECTING_DATA);
        entityManager.clear();

        CareReport persisted = reportRepository.findById(report.getId()).orElseThrow();
        assertThat(persisted.getConditionGrade()).isEqualTo(CareConditionGrade.COLLECTING_DATA);
        assertThat(persisted.getConditionScore()).isNull();
        assertThat(persisted.getDataStatus()).isEqualTo("INSUFFICIENT_DATA");
    }

    @Test
    void latestCanonicalReportUsesThirtyDayWindowAndDeterministicIdOrder() {
        Product product = createProduct();
        LocalDateTime end = LocalDateTime.of(2026, 9, 14, 12, 0);
        saveReport(product, end, 30, "SUFFICIENT", 81, CareConditionGrade.BALANCED);
        CareReport expected = saveReport(product, end, 30, "SUFFICIENT", 83, CareConditionGrade.BALANCED);
        saveReport(product, end.plusHours(1), 7, "SUFFICIENT", 99, CareConditionGrade.STABLE);
        entityManager.clear();

        assertThat(reportRepository.findFirstByProductIdOrderByCreatedAtDesc(product.getId()).orElseThrow().getId())
                .isEqualTo(expected.getId());
        assertThat(reportRepository.findAllByProductIdOrderByCreatedAtDesc(product.getId()))
                .hasSize(2).allSatisfy(report -> assertThat(report.getAnalysisWindowDays()).isEqualTo(30));
    }

    @Test
    void incompleteRowsCannotMakeOneEnvironmentReadingSufficient() {
        Product product = createProduct();
        Device device = entityManager.persistAndFlush(new Device(product.getUser(), "environment-test-device",
                "Test device", null, null, null));
        ProductDevice link = entityManager.persistAndFlush(new ProductDevice(product, device, ProductDeviceRole.PRIMARY_SENSOR));
        LocalDateTime end = LocalDateTime.of(2026, 9, 14, 12, 0);
        LocalDateTime first = end.minusHours(26);
        for (int i = 0; i < 24; i++) {
            entityManager.persist(new SensorReading(product, device, link, (long) i,
                    i == 0 ? BigDecimal.valueOf(20) : null,
                    i == 0 ? BigDecimal.valueOf(20) : null,
                    i == 1 ? BigDecimal.valueOf(6) : null, 0, false,
                    first.plusMinutes(i * 65L), end));
        }
        entityManager.flush();
        Object[] stats = sensorRepository.findReadingStats(product.getId(), end.minusDays(7), end);
        if (stats.length == 1 && stats[0] instanceof Object[] nested) stats = nested;
        assertThat(((Number) stats[0]).longValue()).isEqualTo(1);
        assertThat(((Number) stats[5]).longValue()).isEqualTo(24);
        assertThat(stats[4]).isNotNull();

        var aggregate = sensorRepository.aggregate(product.getId(), end.minusDays(7), end,
                CareRuleEngine.DRY_THRESHOLD, CareRuleEngine.STRONG_SHOCK_THRESHOLD);
        assertThat(aggregate.readingCount()).isEqualTo(1);
        assertThat(aggregate.avgTemperature()).isEqualTo(20.0);
        assertThat(aggregate.avgHumidity()).isEqualTo(20.0);
        assertThat(aggregate.dryRatio()).isEqualTo(1.0);
        assertThat(aggregate.shockCountAsInt()).isEqualTo(1);
        assertThat(sensorRepository.findDailyEnvironment(product.getId(), end.minusDays(7), end))
                .hasSize(1).allSatisfy(row -> assertThat(((Number) row[3]).longValue()).isEqualTo(1));

        CareDecisionPolicy policy = new CareDecisionPolicy(new CareRuleEngine());
        var sufficiency = policy.dataSufficiency(((Number) stats[0]).longValue(),
                ((Timestamp) stats[1]).toLocalDateTime(), ((Timestamp) stats[2]).toLocalDateTime(), end, end);
        var summary = policy.fallback(product.getId(), SensorPeriod.SEVEN_DAYS, end, aggregate, sufficiency);
        assertThat(summary.dataSufficiency().status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(summary.productCondition().score()).isNull();
    }

    @Test
    void historicalImpossibleMeasurementsAreExcludedFromEnvironmentAggregates() {
        Product product = createProduct();
        Device device = entityManager.persistAndFlush(new Device(product.getUser(), "invalid-environment-device",
                "Test device", null, null, null));
        ProductDevice link = entityManager.persistAndFlush(new ProductDevice(product, device, ProductDeviceRole.PRIMARY_SENSOR));
        LocalDateTime end = LocalDateTime.of(2026, 9, 14, 12, 0);
        entityManager.persist(new SensorReading(product, device, link, 1L, BigDecimal.valueOf(20),
                BigDecimal.valueOf(101), null, 0, false, end.minusHours(1), end));
        entityManager.persist(new SensorReading(product, device, link, 2L, BigDecimal.valueOf(-274),
                BigDecimal.valueOf(50), null, 0, false, end.minusHours(2), end));
        entityManager.flush();

        var aggregate = sensorRepository.aggregate(product.getId(), end.minusDays(7), end,
                CareRuleEngine.DRY_THRESHOLD, CareRuleEngine.STRONG_SHOCK_THRESHOLD);
        assertThat(aggregate.isEmpty()).isTrue();
        assertThat(aggregate.avgTemperature()).isNull();
        assertThat(aggregate.avgHumidity()).isNull();
        assertThat(sensorRepository.findDailyEnvironment(product.getId(), end.minusDays(7), end)).isEmpty();
        assertThat(sensorRepository.findThreeDayEnvironment(product.getId(), end.minusDays(30), end)).isEmpty();
        assertThat(sensorRepository.findMonthlyEnvironment(product.getId(), end.minusDays(365), end)).isEmpty();
    }

    private Product createProduct() {
        User user = userRepository.saveAndFlush(User.createLocal(
                "report-test@mxis.example", "encoded-password", "Report test", null));
        return productRepository.saveAndFlush(new Product(user, "report-test-dpp", "Test bag",
                null, "natural_leather", "Natural leather", List.of("vachetta"), null,
                null, null, null));
    }

    private CareReport saveReport(Product product, LocalDateTime end, int days, String status,
            Integer score, CareConditionGrade grade) {
        boolean collecting = grade == CareConditionGrade.COLLECTING_DATA;
        AiCareSummaryResponse summary = new AiCareSummaryResponse(product.getId(), end, days,
                new AiCareSummaryResponse.DataSufficiency(status, null, 50, 48.0, end, end),
                new AiCareSummaryResponse.ProductCondition(collecting ? "Collecting Data" : "Standard",
                        score, "humidity", "Test snapshot"), null, null, null);
        return reportRepository.saveAndFlush(new CareReport(product, algorithmRepository.findByIsActiveTrue().orElseThrow(),
                grade, "Test snapshot", "Analysis", "Recommendation", end.minusDays(days), end,
                null, null, null, null, 0, 0, "{\"schemaVersion\":\"integration-test\"}", summary,
                collecting ? "UNKNOWN" : "LOW_MEDIUM", "NONE", 42L));
    }
}
