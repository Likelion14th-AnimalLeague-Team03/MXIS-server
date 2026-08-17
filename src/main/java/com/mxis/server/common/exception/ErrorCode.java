package com.mxis.server.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // Auth / User
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."),
    REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "필수 약관에 동의해야 합니다."),
    KAKAO_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "카카오 인증에 실패했습니다."),

    // Device
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "기기 정보를 찾을 수 없습니다."),
    DEVICE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 등록된 기기입니다."),
    DEVICE_NOT_OWNED(HttpStatus.FORBIDDEN, "본인 소유의 기기가 아닙니다."),

    // Product
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "제품 정보를 찾을 수 없습니다."),
    PRODUCT_NOT_OWNED(HttpStatus.FORBIDDEN, "본인 소유의 제품이 아닙니다."),
    DPP_NOT_RECOGNIZED(HttpStatus.NOT_FOUND, "DPP 코드를 인식할 수 없습니다."),
    PRODUCT_DEVICE_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "제품-기기 연결 정보를 찾을 수 없습니다."),
    DEVICE_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 해당 제품에 연결된 기기입니다."),

    // Sensor
    DEVICE_NOT_LINKED_TO_PRODUCT(HttpStatus.CONFLICT, "기기가 아직 제품에 연결되지 않아 센서 데이터를 저장할 수 없습니다."),

    // Care (Diagnosis / Suggestion)
    NO_DIAGNOSIS_DATA(HttpStatus.CONFLICT, "아직 진단 데이터가 없습니다."),
    CARE_SUGGESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "제안 정보를 찾을 수 없습니다."),
    CARE_SUGGESTION_NOT_OWNED(HttpStatus.FORBIDDEN, "본인 제품의 제안이 아닙니다."),

    // Store
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "매장 정보를 찾을 수 없습니다."),

    // Reservation
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약 정보를 찾을 수 없습니다."),
    RESERVATION_NOT_OWNED(HttpStatus.FORBIDDEN, "본인의 예약이 아닙니다."),
    RESERVATION_NOT_MODIFIABLE(HttpStatus.CONFLICT, "취소되었거나 완료된 예약은 변경할 수 없습니다."),
    SLOT_ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 예약된 시간대입니다."),

    // Notification
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
