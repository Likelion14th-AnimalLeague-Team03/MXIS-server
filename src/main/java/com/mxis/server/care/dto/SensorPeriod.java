package com.mxis.server.care.dto;

/** 환경 데이터 상세 조회 기간. */
public enum SensorPeriod {
    SEVEN_DAYS("7D", 7),
    THIRTY_DAYS("30D", 30),
    ONE_YEAR("1Y", 365);

    private final String code;
    private final int days;

    SensorPeriod(String code, int days) {
        this.code = code;
        this.days = days;
    }

    public int days() {
        return days;
    }

    public boolean isYear() {
        return this == ONE_YEAR;
    }

    @com.fasterxml.jackson.annotation.JsonValue
    public String code() {
        return code;
    }

    /** "7D" / "30D" / "1Y" 문자열을 enum으로. 그 외 값은 null을 반환해 호출부에서 400 처리한다. */
    public static SensorPeriod fromCode(String value) {
        for (SensorPeriod period : values()) {
            if (period.code.equalsIgnoreCase(value)) {
                return period;
            }
        }
        return null;
    }
}
