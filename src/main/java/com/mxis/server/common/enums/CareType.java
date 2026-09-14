package com.mxis.server.common.enums;

/** Public care-guide IDs shared with the AI care-summary contract. */
public enum CareType {
    VENTILATED_SHADE_STORAGE("ventilated_shade_storage"),
    DRY_SOFT_CLOTH_WIPE("dry_soft_cloth_wipe"),
    VENTILATED_HUMIDITY_DRY("ventilated_humidity_dry"),
    AVOID_DRY_STORAGE("avoid_dry_storage"),
    AVOID_HEAT_COOL_DOWN("avoid_heat_cool_down"),
    LONG_TERM_STORAGE_CHECK("long_term_storage_check"),
    SHOCK_IMPACT_CHECK("shock_impact_check");

    private final String code;

    CareType(String code) { this.code = code; }

    public String code() { return code; }

    public static CareType fromCode(String code) {
        for (CareType type : values()) {
            if (type.code.equals(code)) return type;
        }
        return null;
    }
}
