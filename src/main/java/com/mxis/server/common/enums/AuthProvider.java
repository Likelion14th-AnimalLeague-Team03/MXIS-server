package com.mxis.server.common.enums;

public enum AuthProvider {
    LOCAL("local"),
    KAKAO("kakao");

    private final String value;

    AuthProvider(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AuthProvider fromValue(String value) {
        for (AuthProvider provider : values()) {
            if (provider.value.equals(value)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown auth_provider value: " + value);
    }
}
