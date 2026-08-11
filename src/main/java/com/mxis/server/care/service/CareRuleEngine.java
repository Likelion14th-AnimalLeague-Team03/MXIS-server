package com.mxis.server.care.service;

import com.mxis.server.common.enums.CareConditionGrade;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 진단 규칙 엔진. 습도·충격 임계값 판정과 종합 등급 산출을 담당하며, AI는 여기에 관여하지 않는다.
 * (AI는 이 엔진이 낸 등급·라벨을 받아 문구만 다듬는 역할이며, 아직 연동 전이라 폴백 고정 문구를 쓴다.)
 *
 * 임계값은 MCM 공식 케어 가이드(건조·서늘 보관, 마찰·충격 주의)를 수치로 옮긴 초기값이다.
 * 실제 참(charm) 센서의 측정 특성에 맞춰 조정해야 하는 캘리브레이션 값이므로 상수로 모아 둔다.
 */
@Component
public class CareRuleEngine {

    // 습도 구간 (%)
    private static final BigDecimal DRY_RISK_MAX = BigDecimal.valueOf(30);
    private static final BigDecimal IDEAL_MIN = BigDecimal.valueOf(40);
    private static final BigDecimal IDEAL_MAX = BigDecimal.valueOf(60);
    private static final BigDecimal HUMID_RISK_MIN = BigDecimal.valueOf(70);

    /** 건조 노출 판정 기준 습도. 이 값 미만인 측정을 "건조 환경 노출"로 센다. */
    public static final BigDecimal DRY_THRESHOLD = IDEAL_MIN;

    /**
     * "강한 충격"으로 집계할 최소 충격량(g). V2__seed_data.sql의 rule_config.shock_threshold와 맞춘 값이다.
     * ponytail: 하드웨어 실측 전 초기값. 실제 참의 가속도 센서 특성에 맞춰 조정해야 하는 캘리브레이션 값이며,
     * 바꿀 때는 시드의 rule_config도 함께 갱신해 둘이 어긋나지 않게 한다.
     */
    public static final BigDecimal STRONG_SHOCK_THRESHOLD = BigDecimal.valueOf(5.0);

    // 30일 기준 강한 충격 횟수 구간
    private static final int SHOCK_LOW_MAX = 3;
    private static final int SHOCK_MEDIUM_MAX = 10;

    // 온도 쾌적 구간 (°C)
    private static final BigDecimal TEMP_IDEAL_MIN = BigDecimal.valueOf(15);
    private static final BigDecimal TEMP_IDEAL_MAX = BigDecimal.valueOf(28);

    // 7일 건조 노출 비율 구간
    private static final double DRY_SHORT_MAX = 0.2;
    private static final double DRY_MODERATE_MAX = 0.5;

    public enum HumidityGrade {
        DRY_RISK("건조 환경 노출", 2),
        SLIGHTLY_DRY("다소 건조한 환경", 1),
        IDEAL("이상적입니다", 0),
        SLIGHTLY_HUMID("다소 습한 환경", 1),
        HUMID_RISK("습한 환경 주의", 2);

        private final String label;
        private final int severity;

        HumidityGrade(String label, int severity) {
            this.label = label;
            this.severity = severity;
        }

        public String label() {
            return label;
        }
    }

    public enum ShockGrade {
        LOW("낮음", 0),
        MEDIUM("보통", 1),
        HIGH("높음", 2);

        private final String label;
        private final int severity;

        ShockGrade(String label, int severity) {
            this.label = label;
            this.severity = severity;
        }

        public String label() {
            return label;
        }
    }

    public HumidityGrade humidityGrade(BigDecimal avgHumidity) {
        if (avgHumidity == null) {
            return HumidityGrade.IDEAL;
        }
        if (avgHumidity.compareTo(DRY_RISK_MAX) < 0) {
            return HumidityGrade.DRY_RISK;
        }
        if (avgHumidity.compareTo(IDEAL_MIN) < 0) {
            return HumidityGrade.SLIGHTLY_DRY;
        }
        if (avgHumidity.compareTo(IDEAL_MAX) <= 0) {
            return HumidityGrade.IDEAL;
        }
        if (avgHumidity.compareTo(HUMID_RISK_MIN) <= 0) {
            return HumidityGrade.SLIGHTLY_HUMID;
        }
        return HumidityGrade.HUMID_RISK;
    }

    public ShockGrade shockGrade(int strongShockCount) {
        if (strongShockCount <= SHOCK_LOW_MAX) {
            return ShockGrade.LOW;
        }
        if (strongShockCount <= SHOCK_MEDIUM_MAX) {
            return ShockGrade.MEDIUM;
        }
        return ShockGrade.HIGH;
    }

    /**
     * 종합 등급 = 더 나쁜 쪽을 따라간다(worse-of-two). 한쪽이 완벽해도 다른 쪽이 나쁘면 상쇄되지 않는다.
     * 두 축이 모두 최악(위험 습도 + 높은 충격)일 때만 EXPERT_CHECK로 한 단계 더 올린다.
     */
    public CareConditionGrade conditionGrade(HumidityGrade humidity, ShockGrade shock) {
        int severity = Math.max(humidity.severity, shock.severity);
        if (humidity.severity == 2 && shock.severity == 2) {
            return CareConditionGrade.EXPERT_CHECK;
        }
        return switch (severity) {
            case 0 -> CareConditionGrade.STABLE;
            case 1 -> CareConditionGrade.BALANCED;
            default -> CareConditionGrade.LIGHT_CARE;
        };
    }

    public String temperatureLabel(BigDecimal avgTemperature) {
        if (avgTemperature == null) {
            return "측정된 데이터가 없습니다";
        }
        if (avgTemperature.compareTo(TEMP_IDEAL_MIN) < 0) {
            return "다소 낮은 환경";
        }
        if (avgTemperature.compareTo(TEMP_IDEAL_MAX) > 0) {
            return "다소 높은 환경";
        }
        return "이상적입니다";
    }

    /** 최근 7일 건조 노출 정성 등급. */
    public String dryExposureLabel(double dryRatio) {
        if (dryRatio < DRY_SHORT_MAX) {
            return "짧음";
        }
        if (dryRatio < DRY_MODERATE_MAX) {
            return "보통";
        }
        return "잦음";
    }

    // --- 등급별 고정 문구 (AI 폴백) ---------------------------------------------------
    // 실제 LLM 연동 전까지는 이 문구가 그대로 저장되고, 연동 후에도 호출 실패 시 이 값이 남는다.

    public String summaryText(CareConditionGrade grade) {
        return switch (grade) {
            case STABLE -> "안정적인 상태입니다.";
            case BALANCED -> "균형 있게 유지되고 있습니다.";
            case LIGHT_CARE -> "가벼운 관리가 권장됩니다.";
            case EXPERT_CHECK -> "전문가의 확인을 제안드립니다.";
        };
    }

    public String analysisText(CareConditionGrade grade) {
        return switch (grade) {
            case STABLE -> "최근 환경과 사용 기록이 권장 범위 안에 있습니다.";
            case BALANCED -> "최근 환경과 사용 기록이 안정적인 범위에 있습니다.";
            case LIGHT_CARE -> "최근 사용 환경에서 관리가 필요할 수 있는 신호가 확인되었습니다.";
            case EXPERT_CHECK -> "최근 환경과 사용 기록에서 전문가 확인이 필요할 수 있는 신호가 확인되었습니다.";
        };
    }

    public String recommendationText(CareConditionGrade grade) {
        return switch (grade) {
            case STABLE -> "현재는 안정적으로 유지되고 있으나, 다음 계절 전 가벼운 점검을 권장합니다.";
            case BALANCED -> "지금의 보관 습관을 유지하시면 좋겠습니다.";
            case LIGHT_CARE -> "이번 계절이 지나기 전 가벼운 컨디션 점검을 권장합니다.";
            case EXPERT_CHECK -> "가까운 매장에서 전문가 점검을 받아보시길 권장합니다.";
        };
    }

    public String suggestionMessage(CareConditionGrade grade) {
        return grade == CareConditionGrade.EXPERT_CHECK
                ? "가까운 매장에서 전문가 컨디션 점검을 받아보시길 제안드려요."
                : "이번 계절이 지나기 전 가벼운 컨디션 점검을 제안드려요.";
    }

    public String suggestionReason(CareConditionGrade grade) {
        return "최근 사용 환경과 누적 기록을 고려해 안내드립니다.";
    }

    public String recommendedService(CareConditionGrade grade) {
        return grade == CareConditionGrade.EXPERT_CHECK ? "전문 케어" : "가벼운 점검";
    }

    /** LIGHT_CARE 이상일 때만 케어 제안을 생성한다 (ERD의 CareReport 1:0..1 관계 근거). */
    public boolean needsSuggestion(CareConditionGrade grade) {
        return grade == CareConditionGrade.LIGHT_CARE || grade == CareConditionGrade.EXPERT_CHECK;
    }
}
