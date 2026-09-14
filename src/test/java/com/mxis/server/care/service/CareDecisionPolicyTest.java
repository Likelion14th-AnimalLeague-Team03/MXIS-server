package com.mxis.server.care.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mxis.server.care.dto.AiCareSummaryResponse;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.common.enums.CareConditionGrade;
import com.mxis.server.sensor.dto.SensorAggregate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CareDecisionPolicyTest {
    private final CareDecisionPolicy policy = new CareDecisionPolicy(new CareRuleEngine());
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Test
    void humidity75CannotBeExcellentInLiveFallbackWhileStoredPolicyRecommendsCare() {
        AiCareSummaryResponse summary = fallback(75, sufficient());
        CareConditionGrade grade = policy.grade(summary, null, null);
        assertThat(summary.productCondition().label()).isEqualTo("Needs Attention");
        assertThat(summary.productCondition().score()).isEqualTo(50);
        assertThat(grade).isEqualTo(CareConditionGrade.LIGHT_CARE);
        assertThat(policy.needsSuggestion("SUFFICIENT", policy.fallbackCareNeed(grade), "NONE", grade)).isTrue();
        assertThat(policy.careCycleMonths("SUFFICIENT", "MEDIUM", "NONE", grade)).isEqualTo(3);
    }

    @Test
    void mediumCareCreatesTheSameCareNeedForReportAndSuggestion() {
        AiCareSummaryResponse summary = fallback(50, sufficient());
        CareConditionGrade grade = policy.grade(summary, "MEDIUM", "NONE");
        assertThat(grade).isEqualTo(CareConditionGrade.LIGHT_CARE);
        assertThat(policy.needsSuggestion("SUFFICIENT", "MEDIUM", "NONE", grade)).isTrue();
    }

    @Test
    void insufficientAndUnknownCannotCreateScoreNormalGradeOrVisitRecommendation() {
        for (String status : new String[] {"NO_DATA", "INSUFFICIENT_DATA", "STALE_DATA", "UNRECOGNIZED"}) {
            AiCareSummaryResponse summary = fallback(50,
                    new AiCareSummaryResponse.DataSufficiency(status, null, 2, 1.0, now, now));
            CareConditionGrade grade = policy.grade(summary, "HIGH", "REQUIRED");
            assertThat(summary.productCondition().score()).isNull();
            assertThat(grade).isEqualTo(CareConditionGrade.COLLECTING_DATA);
            assertThat(policy.needsSuggestion(status, "HIGH", "REQUIRED", grade)).isFalse();
            assertThat(policy.careCycleMonths(status, "HIGH", "REQUIRED", grade)).isZero();
        }
    }

    @Test
    void minimumCountCoverageAndFreshnessAreShared() {
        assertThat(policy.dataSufficiency(23, now.minusDays(2), now, now, now).status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(policy.dataSufficiency(24, now.minusHours(23), now, now, now).status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(policy.dataSufficiency(24, now.minusDays(5), now.minusDays(4), now, now).status()).isEqualTo("STALE_DATA");
        assertThat(sufficient().status()).isEqualTo("SUFFICIENT");
    }

    private AiCareSummaryResponse fallback(double humidity, AiCareSummaryResponse.DataSufficiency sufficiency) {
        return policy.fallback(1L, SensorPeriod.THIRTY_DAYS, now,
                new SensorAggregate(20.0, BigDecimal.valueOf(20), BigDecimal.valueOf(20), humidity, 24L, 0L, 0L), sufficiency);
    }

    private AiCareSummaryResponse.DataSufficiency sufficient() {
        return policy.dataSufficiency(24, now.minusHours(24), now, now, now);
    }
}
