# MXIS API 명세서 (통합본)

> 2026-08-17에 한 번 Device/Product/Sensor/Care/Store/Reservation 8개 도메인 섹션을 통째로 삭제했었으나(당시 note: "git 히스토리에서 찾을 수 있다"), 실제로는 api-spec.md가 애초에 `.gitignore` 대상이라 git에 커밋된 적이 없어 예전 버전은 존재하지 않았다. 같은 날 프로젝트 전체 컨트롤러/서비스/DTO를 다시 읽어 5~10장으로 전부 복원했다 — 지금 문서는 실제 코드(2026-08-17 기준) 그대로다.
> 로그인 2개(카카오 로그인·회원가입) 엔드포인트는 현재 코드에서 비활성화 상태다.

---

## 상태 요약

| 도메인 | 상태 | 비고 |
| --- | --- | --- |
| Auth (로그인) | ✅ 개발 완료 (일부 비활성) | 로그인/토큰갱신/로그아웃만 활성. 카카오 로그인·회원가입은 라우트 주석 처리로 비활성화 |
| Member (마이페이지) | ✅ 개발 완료 | 내 정보, 동의, 알림 설정 |
| Home (메인 홈) | ✅ 개발 완료 (2026-08-17 신규) | Figma 화면 단위 설계로 신규 확정 |
| Notification (알림) | ✅ 개발 완료 (2026-08-17 신규) | 설정 저장은 Member API 재사용 + 알림 목록/읽음 처리 REST API 5개 + 내부 발송 트리거 |
| Device (기기) | ✅ 개발 완료 | 등록/조회/상태갱신/삭제 + BLE 정책 + 기기관리 요약 |
| Product (제품) | ✅ 개발 완료 | DPP 인식(스텁)/등록/조회/대표지정/삭제 + 제품-기기 N:M 연결 |
| Sensor (센서) | ✅ 개발 완료 | 배치 동기화. 저장 시 진단 재계산·환경알림 트리거 동반 |
| Care (진단·제안·AI·가이드) | ✅ 개발 완료 | 규칙엔진 기반 진단 자동생성, AI 설명은 OpenAI 연동(옵션, 미설정 시 폴백 문구) |
| Store (매장) | ✅ 개발 완료 | 목록(거리순)/예약 가능 시간 |
| Reservation (예약) | ✅ 개발 완료 | 생성/조회/변경/취소. FREE·PAID 구분 필드 있음(PENDING_APPROVAL 상태는 정의만, 미사용) |

---

## 목차

1. [Auth](#1-auth) — 로그인
2. [Member](#2-member) — 마이페이지
3. [Home](#3-home) — 메인 홈
4. [Notification](#4-notification) — 알림
5. [Device](#5-device) — 기기
6. [Product](#6-product) — 제품
7. [Sensor](#7-sensor) — 센서
8. [Care](#8-care) — 진단·제안·AI·가이드
9. [Store](#9-store) — 매장
10. [Reservation](#10-reservation) — 예약

---

## 1. Auth

### 1-1. 카카오 로그인 (⚠️ 2026-08-17부터 비활성화)

> 로그인 화면은 우선 이메일/비밀번호(1-2)만 구현하기로 확정 — `POST /api/v1/auth/kakao/login` 라우트를 `AuthController`에서 주석 처리해 비활성화함. `AuthService.kakaoLogin`/`KakaoLoginRequest`는 그대로 남아 있어 주석만 해제하면 재활성화됨. 아래 명세는 재활성화 시 참고용으로 유지. (휴대전화번호 수집 필드 부재, 기존 계정 연결 플로우 부재 등 이전에 논의된 갭도 재활성화 시점에 다시 검토 필요.)

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 카카오 로그인 |
| Endpoint | /api/v1/auth/kakao/login |
| Method | POST |
| 권한 | Guest (인증 불필요) |
| 설명 | Android Kakao SDK에서 발급받은 Kakao Access Token을 이용해 사용자를 인증하고 MXIS Access/Refresh Token을 발급한다. |

### Request
---
**Header**
인증 헤더 불필요 (공개 엔드포인트)

**Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| accessToken | `String` | 접근 토큰 | dkdfjf972ddh2 |

**Example**
```yaml
POST /api/v1/auth/kakao/login
Content-Type: application/json

{
  "accessToken": "accesstokeninput"
}
```

**처리 흐름**
백엔드는 전달받은 Kakao Access Token을 그대로 신뢰하지 않고 카카오 API를 통해 검증한 뒤 사용자 정보를 조회. 카카오 API는 사용자 액세스 토큰을 `Authorization: Bearer ${ACCESS_TOKEN}` 형식으로 사용.

```
Android App -> Kakao SDK 로그인 -> Kakao Access Token 발급
  -> POST /api/v1/auth/kakao/login -> MXIS Backend
  -> Kakao Access Token 검증 -> Kakao 사용자 정보 조회
  -> provider = kakao, providerUid = Kakao User ID 조회
  -> 기존 회원? YES: 로그인 / NO: 신규 회원 생성
  -> MXIS JWT Access Token + Refresh Token 발급
```

### Response
---
**요청 성공 (200)**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "tokenType": "Bearer"
  },
  "error": null
}
```

**Response Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| accessToken | String | MXIS API 호출에 사용하는 JWT Access Token | eyJhbGciOi... |
| refreshToken | String | Access Token 재발급에 사용하는 MXIS Refresh Token | eyJhbGciOi... |
| tokenType | String | 인증 타입. 고정값 | Bearer |

**요청 실패**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_KAKAO_TOKEN",
    "message": "유효하지 않은 카카오 인증 정보입니다."
  }
}
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message | 발생 조건 |
| --- | --- | --- | --- |
| 400 | INVALID_INPUT | 요청 값이 올바르지 않습니다. | accessToken 누락 또는 빈 값 |
| 401 | INVALID_KAKAO_TOKEN | 유효하지 않은 카카오 인증 정보입니다. | 만료·변조·유효하지 않은 Kakao Access Token |
| 401 | KAKAO_AUTH_FAILED | 카카오 인증에 실패했습니다. | 카카오 사용자 정보 조회 실패 |
| 409 | SOCIAL_ACCOUNT_CONFLICT | 이미 다른 로그인 방식으로 가입된 계정입니다. | 동일 이메일의 local 계정과 정책상 충돌하는 경우 |
| 502 | KAKAO_API_ERROR | 카카오 서비스와 통신하는 중 오류가 발생했습니다. | Kakao API 장애 또는 비정상 응답 |
| 500 | INTERNAL_ERROR | 서버 내부 오류가 발생했습니다. | 처리되지 않은 서버 오류 |

---

### 1-2. 로그인

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 로그인 |
| Endpoint | /api/v1/auth/login |
| Method | POST |
| 권한 | Guest (인증 불필요) |
| 설명 | 이메일/비밀번호로 로그인하고 액세스/리프레시 토큰을 발급한다. |

### Request
---
**Header**
인증 헤더 불필요 (공개 엔드포인트)

**Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| email | `String` | 이메일 | user@mxis.com |
| password | `String` | 비밀번호 | password1 |

**Example**
```yaml
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@mxis.com",
  "password": "password1"
}
```

### Response
---
**요청 성공 (200)**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "user": {
      "id": 1,
      "email": "user@mxis.com",
      "name": "홍길동",
      "phone": "01012345678",
      "provider": "LOCAL",
      "createdAt": "2026-08-10T13:09:27.147307"
    }
  }
}
```

**Response Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| accessToken | `String` | API 호출용 액세스 토큰 (유효기간 1시간) | eyJhbGciOi... |
| refreshToken | `String` | 재발급용 리프레시 토큰 (유효기간 14일) | eyJhbGciOi... |
| tokenType | `String` | 토큰 타입 (고정값) | Bearer |
| user | `Object` | 로그인한 유저 프로필 (`GET /users/me`와 동일한 형태). 로그인 직후 화면에서 이름/이메일/휴대전화번호 확인용으로 별도 호출 없이 바로 사용 (2026-08-17 추가) | - |

**요청 실패**
```json
{
  "success": false,
  "error": { "code": "INVALID_CREDENTIALS", "message": "이메일 또는 비밀번호가 올바르지 않습니다." }
}
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 400 | INVALID_INPUT | 요청 값이 올바르지 않습니다. |
| 401 | INVALID_CREDENTIALS | 이메일 또는 비밀번호가 올바르지 않습니다. |
| 500 | INTERNAL_ERROR | 서버 내부 오류가 발생했습니다. |

---

### 1-3. 로그아웃

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 로그아웃 |
| Endpoint | /api/v1/auth/logout |
| Method | POST |
| 권한 | User |
| 설명 | 로그아웃 처리 (서버는 Stateless JWT라 형식적 응답만 반환). |

> ⚠️ 서버에 토큰 저장소(블랙리스트/리프레시 테이블)가 없는 완전한 Stateless JWT 구조라, 이 API는 서버 측에서 토큰을 실효시키지 못함. 요청이 여기까지 도달했다는 것 자체가 SecurityConfig에서 유효한 access token 검증을 통과했다는 뜻이며, 실제 로그아웃은 클라이언트가 보관 중인 토큰을 폐기하는 것으로 처리.

### Request
---
**Header**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| Authorization | `String` | JWT Access Token | Bearer <Token> |

**Body**
없음

**Example**
```yaml
POST /api/v1/auth/logout
Authorization: Bearer <Token>
```

### Response
---
**요청 성공 (200)**
```json
{ "success": true }
```

**Response Body**
반환 데이터 없음 (data 필드 없음)

**요청 실패**
```json
{
  "success": false,
  "error": { "code": "UNAUTHENTICATED", "message": "인증이 필요합니다." }
}
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 401 | UNAUTHENTICATED | 인증이 필요합니다. (토큰 없음/만료/위조) |
| 500 | INTERNAL_ERROR | 서버 내부 오류가 발생했습니다. |

---

### 1-4. 토큰 갱신

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 토큰 갱신 |
| Endpoint | /api/v1/auth/refresh |
| Method | POST |
| 권한 | Guest (Authorization 헤더 대신 refreshToken을 Body로 전달) |
| 설명 | 리프레시 토큰으로 액세스/리프레시 토큰을 재발급한다. |

### Request
---
**Header**
인증 헤더 불필요 (refreshToken을 Body에 담아 전달)

**Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| refreshToken | `String` | 로그인 시 발급받은 리프레시 토큰 | eyJhbGciOi... |

**Example**
```yaml
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOi..."
}
```

### Response
---
**요청 성공 (200)**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...(new)",
    "refreshToken": "eyJhbGciOi...(new)",
    "tokenType": "Bearer",
    "user": {
      "id": 1,
      "email": "user@mxis.com",
      "name": "홍길동",
      "phone": "01012345678",
      "provider": "LOCAL",
      "createdAt": "2026-08-10T13:09:27.147307"
    }
  }
}
```

**Response Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| accessToken | `String` | 새로 발급된 액세스 토큰 | eyJhbGciOi... |
| refreshToken | `String` | 새로 발급된 리프레시 토큰 | eyJhbGciOi... |
| tokenType | `String` | 토큰 타입 (고정값) | Bearer |
| user | `Object` | 로그인한 유저 프로필 (`TokenResponse` 공통 필드, 2026-08-17 추가) | - |

**요청 실패**
```json
{
  "success": false,
  "error": { "code": "INVALID_TOKEN", "message": "유효하지 않거나 만료된 토큰입니다." }
}
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 400 | INVALID_INPUT | 요청 값이 올바르지 않습니다. (refreshToken 누락) |
| 401 | INVALID_TOKEN | 유효하지 않거나 만료된 토큰입니다. (access 타입 토큰 전달, 만료, 위조 등) |
| 500 | INTERNAL_ERROR | 서버 내부 오류가 발생했습니다. |

---

### 1-5. 회원가입 (⚠️ 2026-08-17부터 비활성화 — 아래 참고)

> Figma 로그인 화면 확정(스플래시 → 카카오 온보딩/기존 계정 연결 → MCM 이메일 로그인)에는 별도 이메일/비밀번호 자체 회원가입 화면이 없어, `POST /api/v1/auth/signup` 라우트를 `AuthController`에서 주석 처리해 비활성화함. 회원가입은 추후 별도 기획으로 확장 예정 — 그때 코드는 그대로 남아 있으니 주석만 해제하면 됨. 아래 명세는 재활성화 시 참고용으로 유지.

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 회원가입 |
| Endpoint | /api/v1/auth/signup |
| Method | POST |
| 권한 | Guest (인증 불필요) |
| 설명 | 이메일/비밀번호로 신규 회원을 등록한다. |

### Request
---
**Header**
인증 헤더 불필요 (공개 엔드포인트)

**Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| email | `String` | 이메일 (로그인 ID, 형식 검증) | user@mxis.com |
| password | `String` | 비밀번호. 8~64자, 영문+숫자 포함 필수 | password1 |
| name | `String` | 이름 (필수, ≤50자) | 홍길동 |
| phone | `String` | 전화번호 (선택, ≤20자) | 01012345678 |

**Example**
```yaml
POST /api/v1/auth/signup
Content-Type: application/json

{
  "email": "user@mxis.com",
  "password": "password1",
  "name": "홍길동",
  "phone": "01012345678"
}
```

### Response
---
**요청 성공 (201)**
```json
{
  "success": true,
  "data": { "userId": 1, "email": "user@mxis.com", "name": "홍길동" }
}
```

**Response Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| userId | `Long` | 생성된 회원 ID | 1 |
| email | `String` | 가입한 이메일 | user@mxis.com |
| name | `String` | 가입한 이름 | 홍길동 |

**요청 실패**
```json
{
  "success": false,
  "error": { "code": "EMAIL_ALREADY_EXISTS", "message": "이미 가입된 이메일입니다." }
}
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 400 | INVALID_INPUT | 요청 값이 올바르지 않습니다. (이메일 형식, 비밀번호 규칙 등) |
| 409 | EMAIL_ALREADY_EXISTS | 이미 가입된 이메일입니다. |
| 500 | INTERNAL_ERROR | 서버 내부 오류가 발생했습니다. |

---

## 2. Member

### 2-1. 내 정보

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 내 정보 조회 |
| Endpoint | /api/v1/users/me |
| Method | GET |
| 권한 | User |
| 설명 | 로그인한 사용자 본인의 프로필 정보를 조회한다. |

### Request
---
**Header**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| Authorization | `String` | JWT Access Token | Bearer <Token> |

**Example**
```yaml
GET /api/v1/users/me
Authorization: Bearer <Token>
```

### Response
---
**요청 성공 (200)**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "user@mxis.com",
    "name": "홍길동",
    "phone": "01012345678",
    "provider": "LOCAL",
    "createdAt": "2026-08-10T13:09:27.147307"
  }
}
```

**Response Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| id | `Long` | 회원 ID | 1 |
| email | `String` | 이메일 | user@mxis.com |
| name | `String` | 이름 | 홍길동 |
| phone | `String` | 전화번호 | 01012345678 |
| provider | `String` | 가입 경로 (LOCAL · KAKAO) | LOCAL |
| createdAt | `LocalDateTime` | 가입 일시 | 2026-08-10T13:09:27 |

**요청 실패**
```json
{
  "success": false,
  "error": { "code": "UNAUTHENTICATED", "message": "인증이 필요합니다." }
}
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 401 | UNAUTHENTICATED | 인증이 필요합니다. |
| 500 | INTERNAL_ERROR | 서버 내부 오류가 발생했습니다. |

---

### 2-2. 동의·철회

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 약관 동의·철회 |
| Endpoint | /api/v1/users/me/consents |
| Method | POST |
| 권한 | User |
| 설명 | 약관 동의 또는 철회 이벤트를 1건 이상 기록한다. |

### Request
---
**Header**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| Authorization | `String` | JWT Access Token | Bearer <Token> |

**Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| consents | `List<Object>` | 동의 이벤트 목록 (1개 이상 필수) | - |
| consents[].consentType | `String` | TERMS_OF_SERVICE · PRIVACY · SENSOR_DATA · MARKETING | TERMS_OF_SERVICE |
| consents[].action | `String` | AGREED · REVOKED | AGREED |
| consents[].termsVersion | `String` | 약관 버전 (필수) | 1.0 |

**Example**
```yaml
POST /api/v1/users/me/consents
Authorization: Bearer <Token>
Content-Type: application/json

{
  "consents": [
    { "consentType": "TERMS_OF_SERVICE", "action": "AGREED", "termsVersion": "1.0" },
    { "consentType": "PRIVACY", "action": "AGREED", "termsVersion": "1.0" },
    { "consentType": "SENSOR_DATA", "action": "AGREED", "termsVersion": "1.0" }
  ]
}
```

### Response
---
**요청 성공 (200)**
```json
{
  "success": true,
  "data": [
    { "consentType": "TERMS_OF_SERVICE", "agreed": true, "termsVersion": "1.0", "occurredAt": "2026-08-10T12:00:00" },
    { "consentType": "PRIVACY", "agreed": true, "termsVersion": "1.0", "occurredAt": "2026-08-10T12:00:00" },
    { "consentType": "SENSOR_DATA", "agreed": true, "termsVersion": "1.0", "occurredAt": "2026-08-10T12:00:00" }
  ]
}
```

**Response Body**
요청 후 갱신된 전체 동의 상태 목록. 필드는 `GET /users/me/consents`와 동일 (`consentType`, `agreed`, `termsVersion`, `occurredAt`).

**요청 실패**
```json
{
  "success": false,
  "error": { "code": "INVALID_INPUT", "message": "consents: 비어 있을 수 없습니다." }
}
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 400 | INVALID_INPUT | 요청 값이 올바르지 않습니다. (consents 비어있음, consentType/action 누락 등) |
| 401 | UNAUTHENTICATED | 인증이 필요합니다. |
| 500 | INTERNAL_ERROR | 서버 내부 오류가 발생했습니다. |

---

### 2-3. 동의 상태

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 약관 동의 상태 조회 |
| Endpoint | /api/v1/users/me/consents |
| Method | GET |
| 권한 | User |
| 설명 | 약관 종류별 최신 동의 상태를 조회한다. |

> ℹ️ 동의 기록은 append-only 이벤트 로그(consents 테이블)이며, 이 API는 `(user_id, consent_type)`별 가장 최근 행만 골라 "현재 상태"로 반환.

### Request
---
**Header**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| Authorization | `String` | JWT Access Token | Bearer <Token> |

**Example**
```yaml
GET /api/v1/users/me/consents
Authorization: Bearer <Token>
```

### Response
---
**요청 성공 (200)**
```json
{
  "success": true,
  "data": [
    {
      "consentType": "TERMS_OF_SERVICE",
      "agreed": true,
      "termsVersion": "1.0",
      "occurredAt": "2026-08-10T12:00:00"
    }
  ]
}
```

**Response Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| [].consentType | `String` | 약관 종류 (TERMS_OF_SERVICE · PRIVACY · SENSOR_DATA · MARKETING) | TERMS_OF_SERVICE |
| [].agreed | `Boolean` | 현재 동의 여부 | true |
| [].termsVersion | `String` | 동의한 약관 버전 | 1.0 |
| [].occurredAt | `LocalDateTime` | 동의/철회가 발생한 일시 | 2026-08-10T12:00:00 |

**요청 실패**
```json
{
  "success": false,
  "error": { "code": "UNAUTHENTICATED", "message": "인증이 필요합니다." }
}
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 401 | UNAUTHENTICATED | 인증이 필요합니다. |
| 500 | INTERNAL_ERROR | 서버 내부 오류가 발생했습니다. |

---

### 2-4. 알림 설정

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 알림 설정 조회 |
| Endpoint | /api/v1/users/me/notification-settings |
| Method | GET |
| 권한 | User |
| 설명 | 현재 알림 수신 설정을 조회한다. |

### Request
---
**Header**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| Authorization | `String` | JWT Access Token | Bearer <Token> |

**Example**
```yaml
GET /api/v1/users/me/notification-settings
Authorization: Bearer <Token>
```

### Response
---
**요청 성공 (200)**
```json
{
  "success": true,
  "data": {
    "careTimingEnabled": true,
    "reservationEnabled": true,
    "deviceStatusEnabled": true,
    "marketingEnabled": false,
    "environmentAlertEnabled": true,
    "pushPermissionGranted": true
  }
}
```

**Response Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| careTimingEnabled | `Boolean` | 케어 시점 알림 수신 여부 | true |
| reservationEnabled | `Boolean` | 예약 관련 알림 수신 여부 | true |
| deviceStatusEnabled | `Boolean` | 기기 상태 알림 수신 여부 | true |
| marketingEnabled | `Boolean` | 마케팅 알림 수신 여부 | false |
| environmentAlertEnabled | `Boolean` | 보관 환경(온습도) 알림 수신 여부 | true |
| pushPermissionGranted | `Boolean` | 단말 푸시 권한 허용 여부 | true |

**요청 실패**
```json
{
  "success": false,
  "error": { "code": "UNAUTHENTICATED", "message": "인증이 필요합니다." }
}
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 401 | UNAUTHENTICATED | 인증이 필요합니다. |
| 500 | INTERNAL_ERROR | 서버 내부 오류가 발생했습니다. |

---

### 2-5. 알림 변경

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 알림 설정 변경 |
| Endpoint | /api/v1/users/me/notification-settings |
| Method | PATCH |
| 권한 | User |
| 설명 | 알림 설정을 부분 변경한다 (전달된 필드만 변경). |

### Request
---
**Header**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| Authorization | `String` | JWT Access Token | Bearer <Token> |

**Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| careTimingEnabled | `Boolean` | 선택. null이면 미변경 | false |
| reservationEnabled | `Boolean` | 선택. null이면 미변경 | null |
| deviceStatusEnabled | `Boolean` | 선택. null이면 미변경 | null |
| marketingEnabled | `Boolean` | 선택. null이면 미변경 | null |
| environmentAlertEnabled | `Boolean` | 선택. null이면 미변경 | null |
| pushPermissionGranted | `Boolean` | 선택. null이면 미변경 | null |
| pushToken | `String` | 선택. 단말 푸시 토큰 갱신 | null |

**Example**
```yaml
PATCH /api/v1/users/me/notification-settings
Authorization: Bearer <Token>
Content-Type: application/json

{
  "careTimingEnabled": false
}
```

### Response
---
**요청 성공 (200)**
```json
{
  "success": true,
  "data": {
    "careTimingEnabled": false,
    "reservationEnabled": true,
    "deviceStatusEnabled": true,
    "marketingEnabled": false,
    "environmentAlertEnabled": true,
    "pushPermissionGranted": true
  }
}
```

**Response Body**
변경 후 전체 알림 설정. 필드는 `GET /users/me/notification-settings`와 동일.

**요청 실패**
```json
{
  "success": false,
  "error": { "code": "UNAUTHENTICATED", "message": "인증이 필요합니다." }
}
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 401 | UNAUTHENTICATED | 인증이 필요합니다. |
| 500 | INTERNAL_ERROR | 서버 내부 오류가 발생했습니다. |

---

## 3. Home

> local-rules.md의 "화면 단위 API 설계" 원칙에 따라, CM-100 메인 홈 화면 스크린샷 4종(연결끊김·기본·업데이트대기·첫등록직후)을 사용자와 함께 뜯어보고 확정한 API. `care`/`device`/`product`/`reservation` 여러 도메인 데이터를 화면이 그대로 쓸 수 있는 모양으로 조합해서 내려준다 — 하단 탭에서 "홈"과 "케어"가 별개 화면이라 `care-dashboard`(7-1)를 확장하지 않고 별도 도메인으로 분리함.

### 3-1. 메인 홈

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 메인 홈 |
| Endpoint | /api/v1/products/{id}/home |
| Method | GET |
| 권한 | User (본인 소유 제품만) |
| 설명 | CM-100 메인 홈 화면에 필요한 데이터를 한 번에 조회한다. |

### Request
---
**Header**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| Authorization | `String` | JWT Access Token | Bearer <Token> |

**Path Variable**

| Key | Type | Description |
| --- | --- | --- |
| id | `Long` | 제품 ID |

### Response
---
**요청 성공 (200) — 정상(NORMAL) 상태 예시**
```json
{
  "success": true,
  "data": {
    "userName": "홍길동",
    "productImageUrl": "https://.../bag.png",
    "productState": "NORMAL",
    "score": 100,
    "headline": "안정적인 상태입니다.",
    "daysTogether": 182,
    "upcomingReservation": {
      "reservationId": 12,
      "dDay": 3,
      "reservedDate": "2026-08-20",
      "reservedTime": "16:00",
      "storeName": "MCM 청담 플래그십"
    },
    "charmNeedsReconnect": false
  }
}
```

**Response Body**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| userName | `String` | 인사말에 쓰는 사용자 이름 | 홍길동 |
| productImageUrl | `String` | 제품 이미지 | https://.../bag.png |
| productState | `String` | `COLLECTING`(진단 리포트 없음, 등록 직후) · `NEEDS_UPDATE`(리포트는 있으나 기기 최근 동기화 없음) · `NORMAL`(정상 진단) 중 하나 | NORMAL |
| score | `Integer` | 0~100 점수. `productState`가 `NORMAL`일 때만 값이 있고, 그 외엔 `null`. 등급(STABLE/BALANCED/LIGHT_CARE/EXPERT_CHECK)을 100/75/50/25로 균등 매핑 (실측 점수가 없어 임시로 정한 값, ponytail 캘리브레이션) | 100 |
| headline | `String` | 화면 상단 안내 문구. `COLLECTING`/`NEEDS_UPDATE`는 고정 문구, `NORMAL`은 활성 제안 메시지(있으면)나 등급 요약 문구. **AI가 생성한 문구로 교체 예정(파이썬 AI 서비스 연동 Phase, 아직 미착수) — 지금은 룰 기반 플레이스홀더** | 안정적인 상태입니다. |
| daysTogether | `int` | 제품과 함께한 일수 (구매일, 없으면 등록일 기준) | 182 |
| upcomingReservation | `Object` \| `null` | 가장 가까운 확정 예약 1건. 없으면 `null` (화면은 "예정된 예약이 없어요"로 표시) | - |
| upcomingReservation.dDay | `int` | 오늘부터 예약일까지 남은 일수 | 3 |
| upcomingReservation.storeName | `String` | 매장명 | MCM 청담 플래그십 |
| charmNeedsReconnect | `boolean` | true면 "MXIS Charm 재연결이 필요해요" 모달을 띄운다 (연결된 대표 기기가 없거나 `connectionStatus != CONNECTED`) | false |

**요청 실패**
```json
{
  "success": false,
  "error": { "code": "PRODUCT_NOT_OWNED", "message": "본인 소유의 제품이 아닙니다." }
}
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 401 | UNAUTHENTICATED | 인증이 필요합니다. |
| 403 | PRODUCT_NOT_OWNED | 본인 소유의 제품이 아닙니다. |
| 404 | PRODUCT_NOT_FOUND | 제품 정보를 찾을 수 없습니다. |
| 500 | INTERNAL_ERROR | 서버 내부 오류가 발생했습니다. |

**구현 시 확정한 세부 사항**
- 기기 동기화 정체("업데이트 필요") 판정 기준: `lastSyncedAt`이 3일 이상 지났으면 `NEEDS_UPDATE`. ponytail 캘리브레이션 값, 실측 후 조정 필요.
- `CareSuggestion`은 `LIGHT_CARE` 이상 등급에서만 생성되므로, `STABLE`/`BALANCED`처럼 정상 등급인데 활성 제안이 없는 경우 `CareRuleEngine.summaryText(grade)`로 폴백한다.
- `charmNeedsReconnect`는 `productState`와 독립적으로 계산 — 스크린샷의 "업데이트 필요 + 재연결 모달"이 동시에 뜨는 조합을 그대로 지원.

---
## 4. Notification

> 마이페이지 "알림 설정" 화면(5개 토글: 케어 시점/예약 리마인드/기기 연결·배터리/마케팅/환경 변화)에서 "설정대로 실제 동작하게 해달라"는 요청으로 2026-08-17에 신규 구현. 토글 저장/조회 자체는 [2. Member](#2-member)의 `GET`/`PATCH /users/me/notification-settings`(2-4/2-5)를 그대로 쓴다. **알림 목록 조회/읽음 처리는 별도 `NotificationController` REST API로 존재한다 (2026-08-17 최신화 시 확인 — 최초 작성 당시 "새 엔드포인트 없음"으로 잘못 기재됐던 부분 정정).**

### 4-1. 알림 목록 조회

| 항목 | 내용 |
| --- | --- |
| API 명 | 알림 목록 조회 |
| Endpoint | /api/v1/notifications |
| Method | GET |
| 권한 | User |
| 설명 | 본인의 알림을 최신순(`createdAt DESC`)으로 페이지네이션 조회한다. |

**Query Parameter**

| Key | Type | Description | Example |
| --- | --- | --- | --- |
| type | `String` | 선택. `CARE_TIMING` · `RESERVATION_REMINDER` · `DEVICE_STATUS` · `ENVIRONMENT_ALERT` 중 하나로 필터. 생략 시 전체 | CARE_TIMING |
| unreadOnly | `Boolean` | 선택. `true`면 안 읽은 알림만. 기본 `false` | false |
| page | `Integer` | 선택. 0부터 시작. 기본 `0` | 0 |
| size | `Integer` | 선택. 기본 `20`, 최대 `100`(초과 요청 시 서버가 100으로 clamp) | 20 |

**Example**
```yaml
GET /api/v1/notifications?type=CARE_TIMING&unreadOnly=true&page=0&size=20
Authorization: Bearer <Token>
```

**요청 성공 (200)**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 101,
        "type": "CARE_TIMING",
        "title": "케어가 필요한 시점이에요",
        "message": "습도 변화가 감지되어 전문가 확인을 추천드려요.",
        "deepLink": "/care/products/1/summary",
        "payload": { "productId": 1, "careSuggestionId": 5 },
        "relatedIds": {
          "productId": 1,
          "deviceId": null,
          "reservationId": null,
          "careReportId": 9,
          "careSuggestionId": 5
        },
        "isRead": false,
        "readAt": null,
        "sentAt": null,
        "createdAt": "2026-08-17T09:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false,
    "unreadCount": 1
  }
}
```

**Response Body**

| Key | Type | Description |
| --- | --- | --- |
| content[].id | `Long` | 알림 ID |
| content[].type | `String` | `CARE_TIMING` · `RESERVATION_REMINDER` · `DEVICE_STATUS` · `ENVIRONMENT_ALERT` |
| content[].title / message | `String` | 알림 제목/본문 |
| content[].deepLink | `String` | 클라이언트 이동 경로 |
| content[].payload | `Object` | 알림 종류별 부가 데이터(JSON) |
| content[].relatedIds | `Object` | 연관된 productId/deviceId/reservationId/careReportId/careSuggestionId (해당 없으면 각 필드 null) |
| content[].isRead / readAt | `Boolean` / `LocalDateTime` | 읽음 여부 및 읽은 시각 |
| content[].sentAt | `LocalDateTime` | FCM 실제 발송 시각 (미발송이면 null) |
| content[].createdAt | `LocalDateTime` | 알림 생성 시각 |
| page / size / totalElements / totalPages / hasNext | - | 표준 페이지 정보 |
| unreadCount | `long` | 현재 페이지와 무관한, 전체 안 읽은 알림 수 |

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 401 | UNAUTHENTICATED | 인증이 필요합니다. |
| 500 | INTERNAL_ERROR | 서버 내부 오류가 발생했습니다. |

---

### 4-2. 안 읽은 알림 수

| 항목 | 내용 |
| --- | --- |
| Endpoint | /api/v1/notifications/unread-count |
| Method | GET |
| 권한 | User |

```json
{ "success": true, "data": { "unreadCount": 3 } }
```

---

### 4-3. 알림 상세 조회

| 항목 | 내용 |
| --- | --- |
| Endpoint | /api/v1/notifications/{id} |
| Method | GET |
| 권한 | User (본인 소유 알림만) |

Response Body는 4-1의 `content[]` 항목 하나와 동일한 형태.

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 401 | UNAUTHENTICATED | 인증이 필요합니다. |
| 404 | NOTIFICATION_NOT_FOUND | 알림 정보를 찾을 수 없습니다. (본인 소유가 아니거나 존재하지 않음 — 소유권 없음도 404로 통일, 별도 403 없음) |
| 500 | INTERNAL_ERROR | 서버 내부 오류가 발생했습니다. |

---

### 4-4. 알림 읽음 처리

| 항목 | 내용 |
| --- | --- |
| Endpoint | /api/v1/notifications/{id}/read |
| Method | PATCH |
| 권한 | User (본인 소유 알림만) |

```json
{ "success": true, "data": { "id": 101, "isRead": true, "readAt": "2026-08-17T10:00:00" } }
```

오류 코드는 4-3과 동일 (`NOTIFICATION_NOT_FOUND` 포함).

---

### 4-5. 전체 읽음 처리

| 항목 | 내용 |
| --- | --- |
| Endpoint | /api/v1/notifications/read-all |
| Method | PATCH |
| 권한 | User |
| 설명 | 본인의 안 읽은 알림을 전부 읽음 처리한다. |

```json
{ "success": true, "data": { "unreadCount": 0 } }
```

---

### 발송 조건 (FCM 푸시)

목록 API(4-1~4-5)는 알림 종류/설정 토글과 무관하게 항상 서버에 쌓인 레코드를 보여준다. 실제 **FCM 푸시 발송**만 아래 3가지가 모두 만족해야 이뤄진다 (레코드 자체는 이 조건과 무관하게 항상 생성됨).
1. 해당 알림 종류의 토글이 `true`
2. `pushPermissionGranted == true` (OS 푸시 권한, `PATCH .../notification-settings`로 클라이언트가 보고)
3. `pushToken`이 등록되어 있음 (마찬가지로 `PATCH .../notification-settings`로 등록)

> 위 3가지 검증은 실제로는 `FirebaseMessagingClient`가 아니라 알림 레코드 생성 전 `NotificationSetting` 토글 체크(`NotificationService`)에서 이뤄진다 — 토글이 꺼져 있으면 레코드 자체를 만들지 않는다.

### 4개 트리거 → 토글 매핑

| 트리거 지점 | 알림 종류 | 발생 조건 |
| --- | --- | --- |
| `CareDiagnosisService.createSuggestion()` | `CARE_TIMING` (케어 시점 알림) | 새 진단 리포트 생성 시마다가 아니라, **등급이 `LIGHT_CARE` 이상이라 `CareSuggestion`이 실제로 새로 생성될 때만** |
| `ReservationReminderScheduler` (매일 09:00, `@Scheduled`) | `RESERVATION_REMINDER` (예약 리마인드) | 내일 방문 예정인 `CONFIRMED` 예약이 있을 때. **예약 생성 시점에는 발송하지 않음** (기존 문서에 있던 "예약 생성 직후 발송"은 실제 코드에 없어 삭제) |
| `DeviceService.updateStatus()` | `DEVICE_STATUS` (기기 연결·배터리 안내) | `connectionStatus`가 `DISCONNECTED`/`ERROR`이거나 `batteryLevel <= 20`. 동일 기기·종류로 최근 12시간 내 발송 이력 있으면 스킵(dedup) |
| `SensorReadingService.syncBatch()` (내부적으로 `createEnvironmentAlertIfNeeded`) | `ENVIRONMENT_ALERT` (환경 변화 감지) | 배치 내 측정치 중 습도≥65 또는 온도≥30 또는 습도<30(건조) 또는 최대충격≥1.5 중 하나라도 감지되면 (검사 순서대로 첫 매치만 사용). 동일 제품·종류로 최근 24시간 내 발송 이력 있으면 스킵(dedup) |

각 트리거는 조건을 만족해도 해당 알림 종류 토글이 꺼져 있으면 레코드를 아예 생성하지 않는다.

### FCM 연동

- `notification/client/FirebaseMessagingClient`가 실제 발송을 담당. `fcm.credentials-path`(`FCM_CREDENTIALS_PATH` 환경변수) 설정 시 Firebase Admin SDK로 초기화되고, **미설정이면 발송을 조용히 스킵**한다(로컬 개발 환경에서 자격증명 없이도 앱이 정상 기동해야 하므로).
- 발송 실패는 예외로 전파하지 않고 로그만 남긴다 — 알림은 부가 기능이라 핵심 트랜잭션(센서 동기화, 예약 생성 등)을 절대 깨면 안 되기 때문.
- 실제 자격증명은 사용자가 Firebase 콘솔에서 프로젝트를 만들고 서비스 계정 키(JSON)를 발급해야 한다 — Claude가 대신 계정을 만들 수 없음.

### 남은 미해결 사항

- CM-130 "알림 권한 안내" 화면에서 OS 푸시 권한을 받은 뒤 `pushToken`을 실제로 `PATCH` 호출해 서버에 등록하는 클라이언트 연동 시점 확인 필요.
- 실기기 대상 FCM 발송 테스트는 아직 안 함(자격증명 없음). 자격증명 확보되면 실제 발송 검증 필요.

---

## 5. Device

기기(Smart Charm) 등록·조회·상태 갱신·삭제. `DeviceController`(`/api/v1/devices`) + `DeviceManagementController`(`/api/v1/device-management`).

### 5-1. 기기 등록

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 기기 등록 |
| Endpoint | /api/v1/devices |
| Method | POST |
| 권한 | User |
| 설명 | 새 Smart Charm을 계정에 등록한다. |

### Request
---
**Header**
`Authorization: Bearer {accessToken}` 필수

**Body**

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| serialNumber | `String` | Y | 기기 일련번호 (최대 100자, 유니크). 이미 등록된 번호면 409 |
| deviceName | `String` | N | 사용자가 보는 기기 이름 (최대 50자) |
| macAddress | `String` | N | BLE MAC 주소 (최대 50자) |
| firmwareVersion | `String` | N | 펌웨어 버전 (최대 20자) |
| deviceImageUrl | `String` | N | 기기 이미지 URL (최대 500자) |

### Response
---
**요청 성공 (201)**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "serialNumber": "MXIS-CHARM-0001",
    "deviceName": "Smart Charm 01",
    "macAddress": "AA:BB:CC:DD:EE:FF",
    "firmwareVersion": "1.0.0",
    "deviceImageUrl": null,
    "batteryLevel": null,
    "connectionStatus": "DISCONNECTED",
    "lastSyncedAt": null,
    "registeredAt": "2026-08-17T10:00:00"
  }
}
```

**Response Body**

| Key | Type | Description |
| --- | --- | --- |
| batteryLevel | `Integer` | 마지막 확인 배터리 잔량(0~100). 등록 직후는 null |
| connectionStatus | `String` | `CONNECTED`\|`DISCONNECTED`\|`SYNCING`\|`ERROR` |
| lastSyncedAt | `DateTime` | 마지막 센서 배치 동기화 완료 시각 |

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 400 | INVALID_INPUT | 요청 값이 올바르지 않습니다. |
| 409 | DEVICE_ALREADY_REGISTERED | 이미 등록된 기기입니다. |

---

### 5-2. 내 기기 목록

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 내 기기 목록 |
| Endpoint | /api/v1/devices |
| Method | GET |
| 권한 | User |
| 설명 | 소프트 삭제되지 않은 본인 기기 전체를 반환한다. |

### Response
---
`data`는 5-1 응답과 동일한 형태의 객체 배열.

---

### 5-3. BLE 연결 정책 조회

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | BLE 연결 정책 |
| Endpoint | /api/v1/devices/connection-policy |
| Method | GET |
| 권한 | Guest (공개, `SecurityConfig.PUBLIC_ENDPOINTS`) |
| 설명 | 페어링 화면에서 앱이 스캔할 서비스 UUID·타임아웃 값을 조회한다. |

### Response
---
```json
{
  "success": true,
  "data": {
    "allowedServiceUuids": ["0000fff0-0000-1000-8000-00805f9b34fb"],
    "scanTimeoutSeconds": 10,
    "connectTimeoutSeconds": 15
  }
}
```
값은 `application.yml`의 `mxis.device.ble.*` 설정에서 온다 (`MXIS_BLE_ALLOWED_SERVICE_UUIDS`/`MXIS_BLE_SCAN_TIMEOUT_SECONDS`/`MXIS_BLE_CONNECT_TIMEOUT_SECONDS` 환경변수로 오버라이드 가능).

---

### 5-4. 일련번호로 기기 조회 (페어링 전 확인)

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 일련번호 조회 |
| Endpoint | /api/v1/devices/lookup |
| Method | GET |
| 권한 | User |
| 설명 | QR/NFC로 읽은 일련번호가 등록 가능한지 페어링 전에 확인한다. |

### Request
---
**Query**

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| serialNumber | `String` | Y | 확인할 일련번호 |

### Response
---
```json
{ "success": true, "data": { "serialNumber": "MXIS-CHARM-0002", "registrable": true } }
```

> ⚠️ 스키마에 "출고된 정품 목록" 재고 테이블이 없어, `registrable=true`는 "아직 아무도 등록하지 않은 번호"라는 뜻일 뿐 정품 여부를 보증하지 않는다 (`DeviceService.lookup` 주석 참고).

---

### 5-5. 기기 상세 조회

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 기기 상세 |
| Endpoint | /api/v1/devices/{id} |
| Method | GET |
| 권한 | User (본인 소유만) |
| 설명 | 기기 단건 조회. |

### Response
---
`data`는 5-1과 동일 형태.

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 404 | DEVICE_NOT_FOUND | 기기 정보를 찾을 수 없습니다. |
| 403 | DEVICE_NOT_OWNED | 본인 소유의 기기가 아닙니다. |

---

### 5-6. 기기 상태 갱신

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 기기 상태 갱신 |
| Endpoint | /api/v1/devices/{id}/status |
| Method | PATCH |
| 권한 | User (본인 소유만) |
| 설명 | 앱이 확인한 BLE 연결 상태·배터리 잔량을 서버에 반영한다. |

### Request
---
**Body**

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| connectionStatus | `String` | N | `CONNECTED`\|`DISCONNECTED`\|`SYNCING`\|`ERROR` |
| batteryLevel | `Integer` | N | 0~100 |

### Response
---
`data`는 5-1과 동일 형태(갱신된 값 반영).

> 상태가 `DISCONNECTED`/`ERROR`이거나 배터리 20% 이하가 되면 `DEVICE_STATUS` 알림 트리거가 발동한다 (4개 트리거 표 참고, 동일 기기·종류 12시간 내 중복 스킵).

---

### 5-7. 기기 삭제

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 기기 삭제 |
| Endpoint | /api/v1/devices/{id} |
| Method | DELETE |
| 권한 | User (본인 소유만) |
| 설명 | 소프트 삭제(`deleted_at`). 연결돼 있던 모든 활성 product_devices 링크도 함께 해제(`detached_at` 기록)한다. |

### Response
---
**204 No Content** (본문 없음)

---

### 5-8. 기기 관리 요약 (홈/기기관리 화면)

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 기기 관리 요약 |
| Endpoint | /api/v1/device-management/summary |
| Method | GET |
| 권한 | User |
| 설명 | 내 제품 이미지 목록 + 대표 제품 요약 + 대표 제품 누적 외출 횟수 + 대표 센서 기기 상태 + 최신 측정 환경값을 한 번에 반환. |

### Response
---
```json
{
  "success": true,
  "data": {
    "products": [{ "productId": 1, "productImageUrl": "https://..." }],
    "primaryProduct": {
      "productId": 1, "productImageUrl": "https://...", "productName": "MCM Aren Shopper",
      "materialId": "canvas", "materialDisplayName": "Visetos Canvas", "color": "Cognac",
      "modelCode": "AREN-SHP-001", "dppCode": "MCM-DPP-0001"
    },
    "totalOutingCount": 12,
    "primaryDevice": {
      "deviceId": 1, "serialNumber": "MXIS-CHARM-0001", "deviceImageUrl": null,
      "connectionStatus": "CONNECTED", "batteryLevel": 82, "lastSyncedAt": "2026-08-17T09:00:00"
    },
    "currentEnvironment": { "temperature": 22.5, "humidity": 45.0, "measuredAt": "2026-08-17T09:00:00" }
  }
}
```

**Response Body**

| Key | Type | Description |
| --- | --- | --- |
| products | `Array` | 내 제품 전체의 `{productId, productImageUrl}` 목록 |
| primaryProduct | `Object` \| `null` | 대표 제품 없거나 삭제됐으면 null (이때 아래 필드도 전부 null) |
| totalOutingCount | `long` | 대표 제품의 누적 외출 세션 수 (전체 기간, `sensor_readings` 기준 실시간 집계) |
| primaryDevice | `Object` \| `null` | 대표 제품에 연결된 PRIMARY_SENSOR 기기. 없으면 null |
| currentEnvironment | `Object` \| `null` | 대표 제품의 최신 센서 측정값. 측정 이력 없으면 null |

> `totalOutingCount`는 CLAUDE.md가 언급한 "products에 누적 outing_count 컬럼 없음" 갭을 스키마 변경 없이 `sensor_readings` 실시간 집계(`countTotalOutingSessions`)로 우회한 것 — 저장된 컬럼이 아니라 매 호출 계산값이다.

---

## 6. Product

제품(가방) 등록·조회와 제품-기기(N:M) 연결 관리. `ProductController`(`/api/v1/products`) + `ProductDeviceController`(`/api/v1/products/{productId}/devices`).

### 6-1. DPP 인식

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | DPP 코드 인식 |
| Endpoint | /api/v1/products/recognize |
| Method | POST |
| 권한 | User |
| 설명 | MCM Digital Product Passport 코드로 제품 카탈로그 정보를 조회한다 (등록 전 프리필용). |

> ⚠️ 실제 MCM DPP 조회 API(Aura Blockchain Consortium 연동) 대신, 인메모리 데모 카탈로그 2건(`MCM-DPP-0001`, `MCM-DPP-0002`)으로 대체된 스텁이다 (`DppCatalogService`). 실 연동 시 이 클래스만 교체하면 됨.

### Request
---
**Body**

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| dppCode | `String` | Y | DPP 코드 |

### Response
---
**요청 성공 (200)**
```json
{
  "success": true,
  "data": {
    "dppCode": "MCM-DPP-0001",
    "productName": "MCM Aren Shopper",
    "modelCode": "AREN-SHP-001",
    "materialId": "canvas",
    "materialDisplayName": "Visetos Canvas",
    "materialSubtypes": ["coated_canvas"],
    "color": "Cognac",
    "productImageUrl": "https://static.mcmworldwide.com/demo/aren-shopper.jpg"
  }
}
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 404 | DPP_NOT_RECOGNIZED | DPP 코드를 인식할 수 없습니다. |

---

### 6-2. 제품 등록

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 제품 등록 |
| Endpoint | /api/v1/products |
| Method | POST |
| 권한 | User |
| 설명 | 제품을 계정에 등록한다. DPP 인식 결과를 그대로 넣거나 수동 입력 가능. |

### Request
---
**Body**

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| dppCode | `String` | N | 최대 100자 |
| productName | `String` | Y | 최대 100자 |
| modelCode | `String` | N | 최대 50자 |
| materialId | `String` | Y | 최대 50자 (케어 규칙엔진/가이드 매칭 키) |
| materialDisplayName | `String` | N | 최대 100자 |
| materialSubtypes | `String[]` | N | 각 최대 50자 |
| color | `String` | N | 최대 30자 |
| productImageUrl | `String` | N | 최대 500자 |
| purchasedAt | `Date` | N | 구매일 (YYYY-MM-DD) |

### Response
---
**요청 성공 (201)**
```json
{
  "success": true,
  "data": {
    "id": 1, "dppCode": "MCM-DPP-0001", "productName": "MCM Aren Shopper",
    "modelCode": "AREN-SHP-001", "materialId": "canvas", "materialDisplayName": "Visetos Canvas",
    "materialSubtypes": ["coated_canvas"], "color": "Cognac",
    "productImageUrl": "https://static.mcmworldwide.com/demo/aren-shopper.jpg",
    "purchasedAt": "2026-01-15", "registeredAt": "2026-08-17T10:00:00", "isPrimary": true
  }
}
```

**Response Body**

| Key | Type | Description |
| --- | --- | --- |
| isPrimary | `boolean` | 이 제품이 현재 대표 제품인지. 계정의 첫 제품이라도 자동으로 대표 지정되지 않으며, 등록 API 자체는 항상 `isPrimary` 여부를 그대로 계산해 보여줄 뿐이다 — 대표 지정은 6-4(대표 제품 지정)에서만 일어난다 |

---

### 6-3. 내 제품 목록

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 내 제품 목록 |
| Endpoint | /api/v1/products |
| Method | GET |
| 권한 | User |
| 설명 | 소프트 삭제되지 않은 본인 제품 전체. |

### Response
---
`data`는 6-2 응답과 동일 형태의 객체 배열.

---

### 6-4. 대표 제품 조회

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 대표 제품 조회 |
| Endpoint | /api/v1/products/primary |
| Method | GET |
| 권한 | User |
| 설명 | 현재 대표 제품(`users.primary_product_id`, ERD 원본에는 없고 화면단위 설계 과정에서 추가된 컬럼)을 반환한다. |

### Response
---
대표 제품이 없거나 삭제된 상태면 `"data": null` (에러 아님, 200). 있으면 6-2와 동일 형태(`isPrimary: true` 고정).

---

### 6-5. 제품 상세 조회

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 제품 상세 |
| Endpoint | /api/v1/products/{id} |
| Method | GET |
| 권한 | User (본인 소유만) |
| 설명 | 제품 단건 조회. |

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 404 | PRODUCT_NOT_FOUND | 제품 정보를 찾을 수 없습니다. |
| 403 | PRODUCT_NOT_OWNED | 본인 소유의 제품이 아닙니다. |

---

### 6-6. 대표 제품 지정

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 대표 제품 지정 |
| Endpoint | /api/v1/products/{id}/primary |
| Method | PATCH |
| 권한 | User (본인 소유만) |
| 설명 | 지정한 제품을 대표 제품으로 바꾼다 (계정당 최대 1개, 홈/기기관리 화면이 이 값을 기준으로 요약을 보여줌). |

### Response
---
`data`는 6-2와 동일 형태(`isPrimary: true`).

---

### 6-7. 제품 삭제

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 제품 삭제 |
| Endpoint | /api/v1/products/{id} |
| Method | DELETE |
| 권한 | User (본인 소유만) |
| 설명 | 소프트 삭제. 연결된 모든 활성 기기 링크를 해제하고, 이 제품이 대표 제품이었다면 대표 지정도 함께 해제한다. |

### Response
---
**204 No Content**

---

### 6-8. 제품에 기기 연결

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 제품-기기 연결 |
| Endpoint | /api/v1/products/{productId}/devices |
| Method | POST |
| 권한 | User (제품·기기 모두 본인 소유) |
| 설명 | 기기를 제품에 연결한다. 기기는 한 번에 하나의 제품에만 활성 연결될 수 있다. |

### Request
---
**Body**

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| deviceId | `Long` | Y | 연결할 기기 ID |
| role | `String` | N | `PRIMARY_SENSOR`\|`SECONDARY`. 생략 시 `SECONDARY` |

### Response
---
**요청 성공 (201)**
```json
{
  "success": true,
  "data": {
    "id": 1, "deviceId": 1, "serialNumber": "MXIS-CHARM-0001", "deviceName": "Smart Charm 01",
    "role": "SECONDARY", "attachedAt": "2026-08-17T10:00:00", "detachedAt": null
  }
}
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 409 | DEVICE_ALREADY_LINKED | 이미 해당 제품에 연결된 기기입니다. (또는 "이미 다른 제품에 연결된 기기입니다.") |
| 409 | CONFLICT | 이미 대표 센서가 지정되어 있습니다. 변경하려면 대표 센서 변경 API를 사용하세요. (role=PRIMARY_SENSOR로 요청했는데 이미 대표 센서가 있을 때) |

---

### 6-9. 연결된 기기 목록

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 연결된 기기 목록 |
| Endpoint | /api/v1/products/{productId}/devices |
| Method | GET |
| 권한 | User (제품 본인 소유) |
| 설명 | 해당 제품에 현재 활성 연결된(`detached_at IS NULL`) 기기 목록. |

### Response
---
`data`는 6-8 응답과 동일 형태의 배열.

---

### 6-10. 대표 센서로 승격

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 대표 센서 변경 |
| Endpoint | /api/v1/products/{productId}/devices/{deviceId} |
| Method | PATCH |
| 권한 | User (제품 본인 소유) |
| 설명 | 지정한 연결을 `PRIMARY_SENSOR`로 승격한다. 기존 대표 센서가 있으면 먼저 `SECONDARY`로 강등한 뒤 승격한다 (제품당 활성 PRIMARY_SENSOR는 항상 최대 1개, DB unique index로 최종 보장). |

### Response
---
`data`는 6-8과 동일 형태(`role: "PRIMARY_SENSOR"`).

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 404 | PRODUCT_DEVICE_LINK_NOT_FOUND | 제품-기기 연결 정보를 찾을 수 없습니다. |

---

### 6-11. 기기 연결 해제

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 기기 연결 해제 |
| Endpoint | /api/v1/products/{productId}/devices/{deviceId} |
| Method | DELETE |
| 권한 | User (제품 본인 소유) |
| 설명 | 연결을 물리 삭제하지 않고 `detached_at`만 기록한다 (이력 보존). |

### Response
---
**204 No Content**

---

## 7. Sensor

Smart Charm이 수집한 원시 측정치를 배치 동기화한다. `SensorReadingController`.

### 7-1. 센서 데이터 배치 동기화

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 센서 데이터 배치 동기화 |
| Endpoint | /api/v1/devices/{deviceId}/sensor-readings/batch |
| Method | POST |
| 권한 | User (기기 본인 소유) |
| 설명 | BLE로 쌓인 측정치를 앱이 배치로 업로드한다. 저장과 동시에 진단 리포트를 재계산하고 환경 알림 여부를 검사한다. |

### Request
---
**Body**

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| readings | `Array` | Y (1개 이상) | 아래 항목 배열 |
| readings[].sequenceNumber | `Long` | Y | 기기 내부 측정 순번. (deviceId, sequenceNumber) 유니크 — 중복 재전송은 저장 전 걸러냄(멱등) |
| readings[].temperature | `Decimal` | N | ℃ |
| readings[].humidity | `Decimal` | N | % |
| readings[].maxShockLevel | `Decimal` | N | 해당 구간 최대 충격량(g). ERD 컬럼 그대로 |
| readings[].motionCount | `Integer` | N | ERD에는 없는 확장 필드 — 해당 구간 움직임 감지 횟수 |
| readings[].isOuting | `boolean` | Y (default false) | 측정 시점 외출/사용 상태 |
| readings[].measuredAt | `DateTime` | Y | 실제 측정 시각 |

### Response
---
**요청 성공 (201)**
```json
{
  "success": true,
  "data": {
    "receivedCount": 12,
    "savedCount": 10,
    "duplicateCount": 2,
    "lastSyncedAt": "2026-08-17T10:00:00"
  }
}
```

**Response Body**

| Key | Type | Description |
| --- | --- | --- |
| receivedCount | `int` | 요청에 담긴 총 건수 |
| savedCount | `int` | 실제 저장된(중복 제외) 건수 |
| duplicateCount | `int` | sequenceNumber 중복으로 스킵된 건수 |
| lastSyncedAt | `DateTime` | 이번 동기화 완료 시각(= `devices.last_synced_at` 갱신값) |

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 404 | DEVICE_NOT_FOUND | 기기 정보를 찾을 수 없습니다. |
| 403 | DEVICE_NOT_OWNED | 본인 소유의 기기가 아닙니다. |
| 409 | DEVICE_NOT_LINKED_TO_PRODUCT | 기기가 아직 제품에 연결되지 않아 센서 데이터를 저장할 수 없습니다. |

**부수 효과**
- 저장 직후 같은 트랜잭션에서 `CareDiagnosisService.regenerate()`를 호출해 진단 리포트를 재계산한다 (활성 `care_algorithm`이 없거나 분석 기간 내 데이터가 없으면 조용히 스킵, 예외 없음).
- `NotificationService.createEnvironmentAlertIfNeeded()`로 `ENVIRONMENT_ALERT` 알림 트리거를 검사한다 (조건: 4-1~4-5 절 "4개 트리거 → 토글 매핑" 표 참고).
- ponytail: 위 두 부수 효과는 현재 요청과 같은 트랜잭션에서 동기 처리된다. 배치가 커져 응답이 느려지면 `@Async`로 분리 예정 (`SensorReadingService` 주석).

---

## 8. Care

진단(Diagnosis)·케어 제안(Suggestion)·AI 설명·가이드. 4개 컨트롤러로 나뉘어 있다:
`CareDiagnosisController`(`/api/v1/products/{id}/...`, 초기 구현) · `CareBaseController`(`/api/v1/care/products/{productId}/...`, 2026-08-17 화면 단위 재설계로 추가) · `CareSuggestionController`(`/api/v1/care-suggestions`) · `CareAiController`(`/api/v1/care`).

### 진단 규칙 엔진 (모든 하위 API 공통 근거)

`CareRuleEngine`이 AI 없이 산출하는 결정론적 등급이며, 아직 실 LLM 미연동이라 등급별 고정 문구를 그대로 저장/반환한다 (8-8 AI 설명 API만 예외 — 활성화 시 OpenAI가 이 등급을 문구로만 다듬는다).

**습도 등급** (평균 습도 %)

| 구간 | 등급 | 라벨 | severity |
| --- | --- | --- | --- |
| < 30 | DRY_RISK | 건조 환경 노출 | 2 |
| 30~40 미만 | SLIGHTLY_DRY | 다소 건조한 환경 | 1 |
| 40~60 | IDEAL | 이상적입니다 | 0 |
| 60 초과~70 | SLIGHTLY_HUMID | 다소 습한 환경 | 1 |
| 70 초과 | HUMID_RISK | 습한 환경 주의 | 2 |

**충격 등급** (30일 강한 충격 횟수, 강한 충격 = maxShockLevel ≥ 5.0)

| 횟수 | 등급 | 라벨 | severity |
| --- | --- | --- | --- |
| 0~3 | LOW | 낮음 | 0 |
| 4~10 | MEDIUM | 보통 | 1 |
| 11+ | HIGH | 높음 | 2 |

**종합 등급** = worse-of-two(두 severity 중 큰 값), 단 두 축이 모두 severity 2면 한 단계 더 올려 `EXPERT_CHECK`.

| severity max | 종합 등급 |
| --- | --- |
| 0 | STABLE |
| 1 | BALANCED |
| 2 (한쪽만 2) | LIGHT_CARE |
| 2+2 (둘 다 2) | EXPERT_CHECK |

`LIGHT_CARE` 이상일 때만 `CareSuggestion`이 생성된다 (ERD의 CareReport:CareSuggestion = 1:0..1 근거). 진단은 센서 배치 동기화 직후(`SensorReadingService.syncBatch`) 자동 재계산되며, 최근 30일 데이터를 스냅샷으로 저장한다.

---

### 8-1. 케어진단 홈 (초기 대시보드)

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 케어 대시보드 |
| Endpoint | /api/v1/products/{id}/care-dashboard |
| Method | GET |
| 권한 | User (제품 본인 소유) |
| 설명 | 제품 요약 + 대표 기기 연결 상태 + 최신 진단 등급/문구 + 30일 환경 요약 + 활성 제안을 한 화면 분량으로 반환. |

### Response
---
```json
{
  "success": true,
  "data": {
    "product": { "id": 1, "productName": "MCM Aren Shopper", "materialId": "canvas", "materialDisplayName": "Visetos Canvas", "color": "Cognac", "productImageUrl": "https://..." },
    "device": { "connectionStatus": "CONNECTED", "lastSyncedAt": "2026-08-17T09:00:00" },
    "conditionGrade": "BALANCED",
    "conditionSummary": "균형 있게 유지되고 있습니다.",
    "conditionDescription": "최근 환경과 사용 기록이 안정적인 범위에 있습니다.",
    "environmentSummary": {
      "periodLabel": "최근 30일 동안의 평균이에요",
      "temperature": { "value": 22.5, "label": "이상적입니다" },
      "humidity": { "value": 45.0, "label": "이상적입니다" },
      "outingCount30d": 8,
      "shockLevelLabel": "낮음"
    },
    "activeSuggestion": { "id": 3, "message": "이번 계절이 지나기 전 가벼운 컨디션 점검을 제안드려요.", "reasonSummary": "최근 사용 환경과 누적 기록을 고려해 안내드립니다." }
  }
}
```

**Response Body 참고**
- `device`: 대표 센서(PRIMARY_SENSOR) 기준. 연결된 기기가 없으면 `connectionStatus`/`lastSyncedAt` 모두 null
- 충격은 정성 라벨(`shockLevelLabel`)만 노출하고 원시 횟수는 내려주지 않는다
- `activeSuggestion`: 활성 제안이 없으면 null

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 409 | NO_DIAGNOSIS_DATA | 아직 진단 데이터가 없습니다. (아직 한 번도 진단이 생성되지 않음 — 최소 1회 센서 배치 동기화 필요) |
| 404 | PRODUCT_NOT_FOUND / 403 PRODUCT_NOT_OWNED | 공통 |

---

### 8-2. 최신 상태 리포트

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 최신 상태 리포트 |
| Endpoint | /api/v1/products/{id}/care-reports/latest |
| Method | GET |
| 권한 | User (제품 본인 소유) |
| 설명 | 가장 최근 진단 리포트 상세. 평균 온·습도는 저장된 30일 스냅샷, 건조노출(7일)·함께한시간은 조회 시점 실시간 계산이라 필드별 집계 기간이 다르다. |

### Response
---
```json
{
  "success": true,
  "data": {
    "conditionGrade": "BALANCED",
    "conditionSummary": "균형 있게 유지되고 있습니다.",
    "conditionDescription": "최근 환경과 사용 기록이 안정적인 범위에 있습니다.",
    "environmentSummary": {
      "avgHumidity": { "value": 45.0, "period": "최근 30일", "note": "이상적입니다" },
      "avgTemperature": { "value": 22.5, "period": "최근 30일", "note": "이상적입니다" },
      "dryExposure": { "period": "최근 7일", "label": "보통" }
    },
    "usagePattern": { "timeTogether": "1년 3개월", "outingCount30d": 8, "strongShockCount30d": 1 },
    "recommendationText": "지금의 보관 습관을 유지하시면 좋겠습니다.",
    "createdAt": "2026-08-17T09:00:00"
  }
}
```
`usagePattern.timeTogether`는 `products.purchased_at`(없으면 등록일) 기준 "N년 M개월".

---

### 8-3. 환경 데이터 상세 (7D/30D/1Y)

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 환경 데이터 상세 |
| Endpoint | /api/v1/products/{id}/sensor-summary |
| Method | GET |
| 권한 | User (제품 본인 소유) |
| 설명 | `care_reports`를 거치지 않고 `sensor_readings`를 직접 집계하는 라이브 조회 (저장 안 함). |

### Request
---
**Query**

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| period | `String` | Y | `7D`\|`30D`\|`1Y`. 그 외 값이면 400 |

### Response
---
```json
{
  "success": true,
  "data": {
    "period": "30D",
    "humidityTrend": [{ "date": "2026-07-19", "value": 44.5 }],
    "avgTemperature": 22.5,
    "avgHumidity": 45.0,
    "outingCount": 9,
    "shockCount": 2,
    "outingCountMonthlyAvg": null,
    "shockCountMonthlyAvg": null,
    "comparisonText": "이전 기간보다 습도 변화는 크지 않았고, 충격 감지는 안정적인 수준이었습니다.",
    "insufficientHistory": false
  }
}
```

**샘플링 규칙 (확정 사항)**
- `7D`: 일별 그대로 7개
- `30D`: 변곡점(turning point) 추출 — 양 끝은 항상 포함, 중간은 직전·다음 구간과 증감 방향이 바뀌는 지점만 남김 (`CareQueryService.extractTurningPoints`, 순수 함수 단위테스트 존재)
- `1Y`: 월별 평균 최대 12개
- `outingCount`/`shockCount`는 7D·30D만 채워지고 1Y는 null 대신 `outingCountMonthlyAvg`/`shockCountMonthlyAvg`(월평균)가 채워짐 — 필드는 `@JsonInclude(NON_NULL)`이라 해당 안 되는 쪽은 응답에서 아예 빠진다
- `insufficientHistory`: 직전 동일 기간 데이터가 전혀 없으면 true, `comparisonText`도 그에 맞는 안내 문구로 대체

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 400 | INVALID_INPUT | period는 7D·30D·1Y 중 하나여야 합니다. |

---

### 8-4. 활성 케어 제안 조회

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 활성 제안 조회 |
| Endpoint | /api/v1/products/{id}/care-suggestions/active |
| Method | GET |
| 권한 | User (제품 본인 소유) |
| 설명 | 해당 제품의 현재 `ACTIVE` 상태 제안 1건 (없으면 `data: null`, 에러 아님). |

### Response
---
```json
{
  "success": true,
  "data": {
    "id": 3, "productId": 1,
    "message": "이번 계절이 지나기 전 가벼운 컨디션 점검을 제안드려요.",
    "reasonText": "최근 사용 환경과 누적 기록을 고려해 안내드립니다.",
    "recommendedService": "가벼운 점검",
    "recommendedVisitFrom": "2026-08-21", "recommendedVisitTo": "2026-09-21",
    "status": "ACTIVE", "isRead": false, "createdAt": "2026-08-17T09:00:00"
  }
}
```

---

### 8-5. 케어 제안 상세 조회

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 케어 제안 상세 |
| Endpoint | /api/v1/care-suggestions/{id} |
| Method | GET |
| 권한 | User (제안이 속한 제품 본인 소유) |
| 설명 | 제안 단건 조회. **호출 자체가 읽음 확인으로 간주되어 자동으로 `isRead=true` 처리된다** (별도 읽음 API를 호출하지 않아도 됨). |

### Response
---
`data`는 8-4와 동일 형태(`isRead: true`).

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 404 | CARE_SUGGESTION_NOT_FOUND | 제안 정보를 찾을 수 없습니다. |
| 403 | CARE_SUGGESTION_NOT_OWNED | 본인 제품의 제안이 아닙니다. |

---

### 8-6. 케어 제안 읽음 처리

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 제안 읽음 처리 |
| Endpoint | /api/v1/care-suggestions/{id}/read |
| Method | PATCH |
| 권한 | User (제안이 속한 제품 본인 소유) |
| 설명 | 상세를 열지 않고 목록/푸시에서 바로 읽음만 표시하는 보조 경로 (8-5와 별개 엔드포인트). |

### Response
---
```json
{ "success": true, "data": { "id": 3, "isRead": true } }
```

---

### 8-7. AI 케어 요약 (제품 상태 스코어)

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | AI 케어 요약 |
| Endpoint | /api/v1/care/products/{productId}/summary |
| Method | GET |
| 권한 | User (제품 본인 소유) |
| 설명 | 규칙 기반 스트레스 점수(0~100) + 데이터 충분성 판정 + 설명 문구. `OPENAI_API_KEY`/`MXIS_USE_OPENAI` 설정 시에만 문구를 실제 OpenAI로 다듬고, 미설정이거나 실패 시 결정론적 폴백 문구를 그대로 사용한다. |

### Request
---
**Query**

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| period | `String` | N (default `7D`) | `7D`\|`30D`\|`1Y` |

### Response
---
```json
{
  "success": true,
  "data": {
    "productId": 1, "generatedAt": "2026-08-17T10:00:00", "analysisWindowDays": 7,
    "dataSufficiency": { "status": "SUFFICIENT", "reason": null, "validReadingCount": 48, "coverageHours": 30.5, "lastMeasuredAt": "2026-08-17T09:50:00", "lastSyncedAt": "2026-08-17T09:51:00" },
    "productCondition": { "label": "Standard", "score": 84, "primaryFactor": "humidity", "summary": "최근 습도가 안정 범위를 벗어난 시간이 있어 보관 환경 조정이 권장됩니다." },
    "stressLabels": { "humidity": "CAUTION", "temperatureHeat": "LOW", "dryness": "LOW", "handling": "LOW", "usageRest": "LOW", "uvLight": "UNKNOWN" },
    "explanation": { "short": "최근 습도가 안정 범위를 벗어난 시간이 있어 보관 환경 조정이 권장됩니다.", "reasonBullets": ["..."], "sensorLimitations": ["MVP 센서는 UV/light를 직접 측정하지 않습니다.", "표면 손상, 곰팡이, 균열은 센서만으로 확정하지 않습니다."] },
    "copyGeneration": { "source": "deterministic_fallback", "model": null, "error": null }
  }
}
```

**데이터 충분성 판정** (`dataSufficiency.status`)

| status | 조건 |
| --- | --- |
| NO_DATA | 조회 기간 내 유효 측정치 0건 |
| INSUFFICIENT_DATA | 유효 측정치 24건 미만, 또는 첫~마지막 측정 시각 간격이 24시간 미만 |
| SUFFICIENT | 위 조건을 모두 만족 |

`SUFFICIENT`가 아니면 `productCondition.label`은 `"Collecting Data"`, `score`/`primaryFactor`는 null로 고정된다.

**스코어 산출**: 100점에서 시작해 4개 스트레스 축(humidity/temperatureHeat/dryness/handling)의 `CAUTION`마다 -8점(그 외 등급 `ELEVATED` -18, `HIGH` -35, `INSPECTION_REQUIRED` -50 감점 룰도 존재하나 현재 판정 로직은 `LOW`/`CAUTION`/`ELEVATED`/`UNKNOWN`만 실사용). 85점 이상 `Excellent`, 60점 이상 `Standard`, 그 미만 `Needs Attention`.

**`copyGeneration.source`**: `"deterministic_fallback"`(OpenAI 미사용 또는 실패) 또는 `"openai"`(실제 생성 성공). OpenAI 호출 실패 시 `explanation`은 폴백 문구가 그대로 유지되고 `copyGeneration.error`에 실패 사유(300자 제한)가 채워진다. OpenAI가 생성한 문구는 금칙어(손상되었습니다/곰팡이가 생겼습니다/확률/수리비/보증 등) 포함 시 서버가 거부하고 폴백으로 되돌아간다.

---

### 8-8. 환경 데이터 (AI 화면용, 통계 포함)

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 환경 데이터 (AI) |
| Endpoint | /api/v1/care/products/{productId}/environment |
| Method | GET |
| 권한 | User (제품 본인 소유) |
| 설명 | 8-3과 별개로 화면 단위 재설계에서 추가된 버전. 스트레스 라벨 + 기간별 포인트(온도/습도 평균) + 안내 문구를 함께 반환한다. |

### Request
---
**Query**: period (동일, default `7D`)

### Response
---
```json
{
  "success": true,
  "data": {
    "productId": 1, "period": "7D", "generatedAt": "2026-08-17T10:00:00",
    "dataSufficiency": { "status": "SUFFICIENT", "...": "8-7과 동일 구조" },
    "environmentSummary": { "avgTemperature": 22.5, "avgHumidity": 45.0, "humidityStress": "LOW", "temperatureHeatStress": "LOW", "drynessStress": "LOW", "handlingStress": "LOW", "uvLightStress": "UNKNOWN" },
    "points": [{ "label": "2026-08-11", "from": "2026-08-11", "to": "2026-08-11", "avgTemperature": 22.1, "avgHumidity": 44.0, "readingCount": 12 }],
    "copy": { "short": "그래프의 순간값보다 안정 범위를 벗어난 누적 시간이 관리 판단에 더 중요합니다.", "bullets": ["7D는 일일 평균 7개, 30D는 3일 평균 10개, 1Y는 월 평균 12개로 구성됩니다.", "현재 센서는 UV/light와 표면 증상을 직접 측정하지 않습니다."] }
  }
}
```

**points 구성** (8-3과 다른 버킷 단위임에 주의)

| period | 포인트 수 | 버킷 |
| --- | --- | --- |
| 7D | 7 | 일별 |
| 30D | 10 | 3일 단위 |
| 1Y | 12 | 월별 (이번 달 포함 최근 12개월) |

값이 없는 버킷도 포인트 자체는 생성되고 평균값만 null로 채워진다(빈 구간도 그래프 x축에서 비지 않게).

---

### 8-9. 케어진단 홈 (화면 단위 버전)

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 진단 홈 (화면 단위) |
| Endpoint | /api/v1/care/products/{productId}/diagnosis-home |
| Method | GET |
| 권한 | User (제품 본인 소유) |
| 설명 | 8-1과 별개로 Figma 화면(DG-1xx) 단위로 새로 설계된 버전. `ScreenProductSummary`(product/device 도메인과 공유하는 DTO) + 누적 외출 수 + 30일 환경 요약. |

### Response
---
```json
{
  "success": true,
  "data": {
    "product": { "productId": 1, "productImageUrl": "https://...", "productName": "MCM Aren Shopper", "materialId": "canvas", "materialDisplayName": "Visetos Canvas", "color": "Cognac", "modelCode": "AREN-SHP-001", "dppCode": "MCM-DPP-0001" },
    "totalOutingCount": 12,
    "condition": { "summary": "균형 있게 유지되고 있습니다.", "description": "최근 환경과 사용 기록이 안정적인 범위에 있습니다." },
    "environment30d": { "avgTemperature": 22.5, "temperatureDescription": "이상적입니다", "avgHumidity": 45.0, "humidityDescription": "이상적입니다", "shockLevelLabel": "낮음", "outingCount": 8 }
  }
}
```

---

### 8-10. 상태 리포트 (화면 단위 버전)

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 상태 리포트 (화면 단위) |
| Endpoint | /api/v1/care/products/{productId}/report |
| Method | GET |
| 권한 | User (제품 본인 소유) |
| 설명 | 최신 리포트를 화면(DG-120 등) 기준 필드로 재구성. 다음 권장 케어 시점까지 계산해 내려준다. |

### Response
---
```json
{
  "success": true,
  "data": {
    "careReportId": 5, "generatedAt": "2026-08-17T09:00:00",
    "condition": { "summary": "균형 있게 유지되고 있습니다.", "detail": "최근 환경과 사용 기록이 안정적인 범위에 있습니다." },
    "environment30d": { "avgTemperature": 22.5, "temperatureDescription": "이상적입니다", "avgHumidity": 45.0, "humidityDescription": "이상적입니다", "shockLevelLabel": "낮음", "outingCount": 8 },
    "interpretation": "지금의 보관 습관을 유지하시면 좋겠습니다.",
    "careNeeded": false, "careCycleMonths": 6, "nextCareRecommendedAt": "2027-02-17"
  }
}
```

**careCycleMonths / nextCareRecommendedAt 산출**

| conditionGrade | careCycleMonths |
| --- | --- |
| STABLE, BALANCED | 6 |
| LIGHT_CARE | 3 |
| EXPERT_CHECK | 1 |

`nextCareRecommendedAt` = 이번 리포트 분석 기간 종료일(`period_end`) + `careCycleMonths`. `careNeeded`는 `conditionGrade`가 `LIGHT_CARE` 이상인지(=제안 생성 조건과 동일).

---

### 8-11. 환경 데이터 개요 (7D/30D/1Y 한번에)

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 환경 데이터 개요 |
| Endpoint | /api/v1/care/products/{productId}/environment/overview |
| Method | GET |
| 권한 | User (제품 본인 소유) |
| 설명 | 8-8을 세 기간(7D/30D/1Y) 모두 내부 호출해 한 번에 묶어 반환 (period 쿼리 없음). |

### Response
---
```json
{
  "success": true,
  "data": {
    "sevenDays": { "period": "7D", "temperaturePoints": [{ "label": "2026-08-11", "value": 22.1 }], "humidityPoints": [{ "label": "2026-08-11", "value": 44.0 }], "avgTemperature": 22.5, "avgHumidity": 45.0, "outingCount": 8, "shockCount": 1, "interpretation": "최근 7일 동안 온도는 이상적입니다, 습도는 이상적입니다 수준이었고 외출 8회, 충격 정도는 낮음으로 기록되었습니다." },
    "thirtyDays": { "period": "30D", "...": "동일 구조" },
    "oneYear": { "period": "1Y", "...": "동일 구조" }
  }
}
```
해당 기간 데이터가 전혀 없으면 `interpretation`은 "아직 해당 기간의 환경 데이터가 충분하지 않습니다."로 고정.

---

### 8-12. 소재별 케어 가이드

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 케어 가이드 |
| Endpoint | /api/v1/care/products/{productId}/guide |
| Method | GET |
| 권한 | User (제품 본인 소유) |
| 설명 | `care_guides`(ERD 원본에 없는 신규 테이블)에서 소재 기준 관리 가이드를 조회한다. |

### Response
---
```json
{
  "success": true,
  "data": {
    "productId": 1, "materialId": "canvas", "materialDisplayName": "Visetos Canvas",
    "guideImageUrl": "https://...", "title": "캔버스 소재 관리법",
    "description": "...", "steps": ["직사광선을 피해 보관하세요.", "..."], "tip": "..."
  }
}
```

**매칭 우선순위**: `product.materialSubtypes`에 있는 세부 소재(subtype) 각각을 `materialId`+`materialSubtype` 조합으로 먼저 찾고, 못 찾으면 `materialId`만으로 `materialSubtype IS NULL`인 공통 가이드를 찾는다. 둘 다 없으면 404.

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 404 | NOT_FOUND | 관리 가이드를 찾을 수 없습니다. |

---

### 8-13. OpenAI 연동 상태 확인

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | OpenAI 상태 |
| Endpoint | /api/v1/care/openai-status |
| Method | GET |
| 권한 | User (인증만 필요, productId 등 소유권 검사 없음 — 공개 엔드포인트 목록에는 없어 토큰은 필요) |
| 설명 | 서버의 OpenAI 연동 설정 상태를 확인한다 (디버그/운영 확인용). |

### Response
---
```json
{ "success": true, "data": { "enabled": false, "apiKeyConfigured": false, "model": "gpt-5-mini", "timeoutSeconds": 45 } }
```
값은 `application.yml`의 `mxis.ai.openai.*`(`MXIS_USE_OPENAI`/`OPENAI_API_KEY`/`OPENAI_MODEL`/`OPENAI_TIMEOUT_SECONDS` 환경변수)에서 온다. `enabled=true`이고 `apiKeyConfigured=true`일 때만 8-7에서 실제 OpenAI 호출을 시도한다.

---

## 9. Store

매장 목록·예약 가능 시간 조회. `StoreController`(`/api/v1/stores`). 예약 생성/변경/조회는 10장 Reservation 참고.

### 9-1. 매장 목록

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 매장 목록 |
| Endpoint | /api/v1/stores |
| Method | GET |
| 권한 | User |
| 설명 | 운영 중(`is_active=true`)인 매장 목록. 위경도를 주면 거리순 정렬. |

### Request
---
**Query**

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| lat | `Decimal` | N | 위도. lng와 둘 다 있어야 거리 계산 |
| lng | `Decimal` | N | 경도 |

### Response
---
```json
{
  "success": true,
  "data": [
    { "id": 1, "storeName": "MCM 청담", "address": "서울 강남구 ...", "phone": "02-...", "latitude": 37.5, "longitude": 127.0, "openingHours": "매일 11:00-20:00", "distanceKm": 1.2 }
  ]
}
```
`distanceKm`: lat/lng 미제공 시, 또는 매장 좌표가 없으면 null (이 경우 정렬은 id 오름차순). 계산 시 Haversine 공식, 소수점 1자리 반올림. ponytail: 매장 수가 MVP 기준 수십 개라 애플리케이션에서 계산 — 수천 개 규모가 되면 DB 공간 함수(`ST_Distance_Sphere`)로 이관 예정(`StoreService` 주석).

---

### 9-2. 예약 가능 시간 조회

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 예약 가능 시간 |
| Endpoint | /api/v1/stores/{id}/available-times |
| Method | GET |
| 권한 | User |
| 설명 | 매장 운영시간(`open_time`~`close_time`) 기준 30분 단위 슬롯과 각 슬롯의 예약 가능 여부. |

### Request
---
**Query**

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| date | `Date` | Y | 조회 날짜 (YYYY-MM-DD) |

### Response
---
```json
{
  "success": true,
  "data": {
    "storeId": 1, "date": "2026-08-20",
    "slots": [{ "time": "11:00", "available": true }, { "time": "11:30", "available": false }]
  }
}
```
- 슬롯 구간·개수는 매장마다 다르다 (`opening_hours` 문구와 실제 `open_time`/`close_time` 컬럼을 일치시켜 둠, 2026-08-11 결정 — 매장 운영시간을 정답으로 삼고 예약 정책을 거기 맞춤. 요일 제한 없음, 매일 동일 슬롯).
- 과거 날짜도 그대로 계산해서 반환한다 — 조회는 부작용이 없어 막지 않고, 예약 생성/변경(10-1/10-3)에서만 과거 날짜를 막는다.
- `available=false`는 해당 슬롯에 `CONFIRMED` 예약이 있다는 뜻 (`CANCELLED`는 슬롯을 점유하지 않음).

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 404 | STORE_NOT_FOUND | 매장 정보를 찾을 수 없습니다. |

---

## 10. Reservation

케어 컨시어지 예약 생성·조회·변경·취소. `ReservationController`(`/api/v1/reservations`).

> `reservationType`(`FREE`\|`PAID`)는 CLAUDE.md가 명시했던 스키마 갭("무상 정기 케어 vs 유상 AS 구분 필드 없음")을 메운 신규 컬럼이다. `ReservationStatus.PENDING_APPROVAL`도 ERD 갱신(2026-08-17)으로 추가됐지만, 실제 `Reservation` 엔티티는 생성자에서 항상 `CONFIRMED`로 시작하도록 고정돼 있어 **현재 코드상 PENDING_APPROVAL로 전이되는 경로가 없다** — 향후 유상 AS 승인 플로우를 위해 값만 미리 정의해 둔 상태.

### 10-1. 예약 생성

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 예약 생성 |
| Endpoint | /api/v1/reservations |
| Method | POST |
| 권한 | User |
| 설명 | 매장에 예약을 생성한다. `careSuggestionId`를 주면 케어 제안에서 이어진 예약으로 취급해 해당 제안 상태를 `RESERVED`로 전환한다. |

### Request
---
**Body**

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| productId | `Long` | Y | 본인 소유 제품 |
| storeId | `Long` | Y | 운영 중인 매장 |
| careSuggestionId | `Long` | N | 제안에서 이어진 예약이면 지정. 해당 제품의 제안이 아니면 403 |
| serviceType | `String` | N | 최대 100자 |
| reservationType | `String` | Y | `FREE`\|`PAID` |
| reservedDate | `Date` | Y | YYYY-MM-DD. 과거 날짜면 400 |
| reservedTime | `String` | Y | `HH:mm`. 매장 운영시간 내 30분 단위가 아니면 400 |
| customerNote | `String` | N | 자유 텍스트 |

### Response
---
**요청 성공 (201)**
```json
{
  "success": true,
  "data": {
    "id": 10, "productId": 1, "productName": "MCM Aren Shopper",
    "storeId": 1, "storeName": "MCM 청담", "storeAddress": "서울 강남구 ...", "storePhone": "02-...",
    "careSuggestionId": 3, "serviceType": "가벼운 점검", "reservationType": "FREE",
    "reservedDate": "2026-08-20", "reservedTime": "14:00", "customerNote": null,
    "status": "CONFIRMED", "cancelledAt": null, "completedAt": null,
    "createdAt": "2026-08-17T10:00:00", "updatedAt": "2026-08-17T10:00:00"
  }
}
```
ponytail: 명세상 생성 응답은 매장 주소·연락처를 포함하지 않지만, 필드가 더 있는 게 클라이언트에 무해해 상세 응답과 DTO를 하나로 합쳤다 (10-1/10-3/10-4 응답 형태 동일).

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 400 | INVALID_INPUT | 지난 날짜로는 예약할 수 없습니다. / {매장명} 예약은 {open}~{close} 사이 30분 단위로만 가능합니다. |
| 404 | STORE_NOT_FOUND | 매장 정보를 찾을 수 없습니다. |
| 404 | CARE_SUGGESTION_NOT_FOUND | 제안 정보를 찾을 수 없습니다. |
| 403 | CARE_SUGGESTION_NOT_OWNED | 본인 제품의 제안이 아닙니다. |
| 409 | SLOT_ALREADY_RESERVED | 이미 예약된 시간대입니다. (사전 검사 + DB unique 제약 위반 시에도 동일 코드로 변환) |

---

### 10-2. 내 예약 목록

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 내 예약 목록 |
| Endpoint | /api/v1/reservations |
| Method | GET |
| 권한 | User |
| 설명 | 본인 예약 전체(축약형). |

### Request
---
**Query**

| Key | Type | Required | Description |
| --- | --- | --- | --- |
| status | `String` | N | `PENDING_APPROVAL`\|`CONFIRMED`\|`CANCELLED`\|`COMPLETED`. 생략 시 전체 |

### Response
---
```json
{
  "success": true,
  "data": [
    { "id": 10, "productId": 1, "productName": "MCM Aren Shopper", "storeId": 1, "storeName": "MCM 청담", "reservationType": "FREE", "reservedDate": "2026-08-20", "reservedTime": "14:00", "status": "CONFIRMED" }
  ]
}
```

---

### 10-3. 예약 상세

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 예약 상세 |
| Endpoint | /api/v1/reservations/{id} |
| Method | GET |
| 권한 | User (본인 예약만) |
| 설명 | 예약 단건 조회. |

### Response
---
`data`는 10-1과 동일 형태.

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 404 | RESERVATION_NOT_FOUND | 예약 정보를 찾을 수 없습니다. |
| 403 | RESERVATION_NOT_OWNED | 본인의 예약이 아닙니다. |

---

### 10-4. 예약 변경

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 예약 변경 |
| Endpoint | /api/v1/reservations/{id} |
| Method | PATCH |
| 권한 | User (본인 예약만) |
| 설명 | 날짜/시간/요청사항 변경. `CONFIRMED` 상태에서만 가능. 변경 이력은 별도 저장하지 않고 기존 Row를 그대로 갱신한다. |

### Request
---
**Body** (전부 선택 — null이면 해당 항목 유지)

| Key | Type | Description |
| --- | --- | --- |
| reservedDate | `Date` | 변경할 날짜 |
| reservedTime | `String` | 변경할 시간 (`HH:mm`) |
| customerNote | `String` | 변경할 요청사항 |

### Response
---
`data`는 10-1과 동일 형태(갱신된 값).

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 409 | RESERVATION_NOT_MODIFIABLE | 취소되었거나 완료된 예약은 변경할 수 없습니다. |
| 400 | INVALID_INPUT | 10-1과 동일한 슬롯 검증 오류 |
| 409 | SLOT_ALREADY_RESERVED | 이미 예약된 시간대입니다. (자기 자신은 슬롯 점유 판정에서 제외) |

---

### 10-5. 예약 취소

### API 기본 정보
---

| 항목 | 내용 |
| --- | --- |
| API 명 | 예약 취소 |
| Endpoint | /api/v1/reservations/{id} |
| Method | DELETE |
| 권한 | User (본인 예약만) |
| 설명 | 물리 삭제가 아니라 `status=CANCELLED` + `cancelledAt` 기록. 취소된 슬롯은 곧바로 다른 사용자가 예약 가능해진다. |

> 다른 도메인의 소프트 삭제(204 No Content)와 달리, 취소 결과를 화면에 즉시 반영해야 하는 UX라 **200 + body**로 반환한다 (확정 사항).

### Response
---
**요청 성공 (200)**
```json
{ "success": true, "data": { "id": 10, "status": "CANCELLED", "cancelledAt": "2026-08-17T11:00:00" } }
```

**발생할 수 있는 오류 코드**

| HTTP Status | code | message |
| --- | --- | --- |
| 409 | RESERVATION_NOT_MODIFIABLE | 취소되었거나 완료된 예약은 변경할 수 없습니다. |
