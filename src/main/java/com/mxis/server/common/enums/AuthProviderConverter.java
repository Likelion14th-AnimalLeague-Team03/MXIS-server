package com.mxis.server.common.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * ERD의 auth_provider ENUM 값('local', 'kakao')을 그대로 DB에 저장하기 위한 컨버터.
 * Java enum 상수명(LOCAL, KAKAO)과 DB 저장값(소문자)을 분리한다.
 */
@Converter(autoApply = true)
public class AuthProviderConverter implements AttributeConverter<AuthProvider, String> {

    @Override
    public String convertToDatabaseColumn(AuthProvider attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public AuthProvider convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AuthProvider.fromValue(dbData);
    }
}
