package com.mxis.server.care.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mxis.server.care.service.CareRuleEngine.HumidityGrade;
import com.mxis.server.care.service.CareRuleEngine.ShockGrade;
import com.mxis.server.common.enums.CareConditionGrade;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 규칙 엔진은 진단 전체의 근거이므로 등급 경계와 종합 등급 매핑표를 직접 검증한다.
 */
class CareRuleEngineTest {

    private final CareRuleEngine engine = new CareRuleEngine();

    @ParameterizedTest
    @CsvSource({
            "25.0, DRY_RISK",
            "29.9, DRY_RISK",
            "30.0, SLIGHTLY_DRY",
            "39.9, SLIGHTLY_DRY",
            "40.0, IDEAL",
            "60.0, IDEAL",
            "60.1, SLIGHTLY_HUMID",
            "70.0, SLIGHTLY_HUMID",
            "70.1, HUMID_RISK"
    })
    void humidityGrade_boundaries(String humidity, HumidityGrade expected) {
        assertThat(engine.humidityGrade(new BigDecimal(humidity))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0, LOW", "3, LOW", "4, MEDIUM", "10, MEDIUM", "11, HIGH", "50, HIGH"})
    void shockGrade_boundaries(int count, ShockGrade expected) {
        assertThat(engine.shockGrade(count)).isEqualTo(expected);
    }

    /** api-spec.md "종합 등급 매핑" 표를 그대로 옮긴 케이스. */
    @ParameterizedTest
    @CsvSource({
            "IDEAL,           LOW,    STABLE",
            "IDEAL,           MEDIUM, BALANCED",
            "SLIGHTLY_DRY,    LOW,    BALANCED",
            "SLIGHTLY_DRY,    MEDIUM, BALANCED",
            "SLIGHTLY_HUMID,  MEDIUM, BALANCED",
            "SLIGHTLY_DRY,    HIGH,   LIGHT_CARE",
            "SLIGHTLY_HUMID,  HIGH,   LIGHT_CARE",
            "DRY_RISK,        LOW,    LIGHT_CARE",
            "DRY_RISK,        MEDIUM, LIGHT_CARE",
            "HUMID_RISK,      LOW,    LIGHT_CARE",
            "DRY_RISK,        HIGH,   EXPERT_CHECK",
            "HUMID_RISK,      HIGH,   EXPERT_CHECK"
    })
    void conditionGrade_followsSpecMapping(HumidityGrade humidity, ShockGrade shock, CareConditionGrade expected) {
        assertThat(engine.conditionGrade(humidity, shock)).isEqualTo(expected);
    }

    /**
     * 명세 표에 빠져 있는 조합(습도 이상적 + 충격 높음). worse-of-two 원칙상 충격 쪽을 따라가야 하며,
     * 습도가 완벽하다고 충격이 상쇄되면 안 된다.
     */
    @Test
    void conditionGrade_idealHumidityDoesNotCancelHighShock() {
        assertThat(engine.conditionGrade(HumidityGrade.IDEAL, ShockGrade.HIGH))
                .isEqualTo(CareConditionGrade.LIGHT_CARE);
    }

    @Test
    void needsSuggestion_onlyForLightCareAndAbove() {
        assertThat(engine.needsSuggestion(CareConditionGrade.STABLE)).isFalse();
        assertThat(engine.needsSuggestion(CareConditionGrade.BALANCED)).isFalse();
        assertThat(engine.needsSuggestion(CareConditionGrade.LIGHT_CARE)).isTrue();
        assertThat(engine.needsSuggestion(CareConditionGrade.EXPERT_CHECK)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"0.0, 짧음", "0.19, 짧음", "0.2, 보통", "0.49, 보통", "0.5, 잦음", "1.0, 잦음"})
    void dryExposureLabel_boundaries(double ratio, String expected) {
        assertThat(engine.dryExposureLabel(ratio)).isEqualTo(expected);
    }

    @Test
    void humidityGrade_nullTreatedAsIdeal() {
        assertThat(engine.humidityGrade(null)).isEqualTo(HumidityGrade.IDEAL);
    }
}
