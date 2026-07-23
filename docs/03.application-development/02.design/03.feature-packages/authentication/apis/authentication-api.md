# 인증 API 상세설계 초안

## 1. 공통 계약

- 기본 경로: `/api/v1`
- 미디어 타입: `application/json`
- 오류: 공통 `Problem` 구조
- 날짜·시간: ISO 8601 UTC
- 세션 쿠키는 응답 본문에 포함하지 않는다.
- 상태 변경 요청은 `X-CSRF-TOKEN`을 검증한다.

기계 판독 가능한 계약의 원본은 `../../../04.openapi/openapi.yaml`이다.

## 2. API 목록

| API ID | Method·Path | operationId | 인증 |
| --- | --- | --- | --- |
| `API-COM-AUTH-001` | `POST /auth/login` | `commonLogin` | 세션 불필요, CSRF 필요 |
| `API-COM-AUTH-002` | `POST /auth/logout` | `commonLogout` | 세션·CSRF |
| `API-COM-AUTH-003` | `GET /auth/me` | `commonGetCurrentUser` | 세션 |
| `API-COM-AUTH-004` | `POST /auth/activity` | `commonRefreshActivity` | 세션·CSRF |
| `API-COM-AUTH-005` | `GET /auth/csrf` | `commonGetCsrfToken` | 불필요 |

## 3. 로그인

### 요청

```json
{
  "loginId": "staff01",
  "password": "plain-text-only-in-transit"
}
```

| 필드 | 타입 | 필수 | 기준 |
| --- | --- | :---: | --- |
| `loginId` | string | Y | 앞뒤 공백 제거 후 영문자를 소문자로 변환, 1~100자 입력 허용 후 정규화 조회 |
| `password` | string | Y | 1~256자 입력 허용; 로그인에서는 신규 비밀번호 복잡도 검증을 하지 않음 |

로그인 요청의 비밀번호 길이 검증은 과도한 입력만 차단한다. 현재 저장된 비밀번호와의 비교가 목적이므로 신규 비밀번호 정책 오류를 별도로 노출하지 않는다.

### 성공 응답 `200 OK`

```json
{
  "user": {
    "userId": "01K...",
    "loginId": "staff01",
    "displayName": "홍길동"
  },
  "roles": [
    {"roleId": "01K...", "roleName": "시스템관리자"}
  ],
  "menus": [
    {
      "menuId": "01K...",
      "parentMenuId": null,
      "menuName": "사용자관리",
      "menuUrl": "/system/users",
      "sortOrder": 10
    }
  ],
  "passwordChangeRequired": false,
  "idleTimeoutSeconds": 900,
  "absoluteSessionExpiresAt": null
}
```

- `passwordChangeRequired=true`이면 `menus`는 빈 배열이다.
- `passwordChangeRequired=true`인 제한 세션은 `idleTimeoutSeconds=600`과 `absoluteSessionExpiresAt`을 반환한다. 일반 세션은 `idleTimeoutSeconds=900`, `absoluteSessionExpiresAt=null`을 반환한다.
- 역할과 메뉴는 로그인 시점 스냅샷이며 세션 중 자동 갱신하지 않는다.
- 응답과 함께 세션 쿠키를 발급하고 세션ID를 교체한다.

### 오류

| HTTP | 코드 | 조건 |
| :---: | --- | --- |
| 400 | `COMMON_VALIDATION_FAILED` | 필수값 누락 또는 허용 입력 크기 초과 |
| 401 | `AUTH_LOGIN_FAILED` | 사용자 없음, 비밀번호 오류 또는 비활성 계정 |
| 403 | `AUTH_FORBIDDEN` | CSRF 토큰 없음 또는 불일치 |
| 429 | `AUTH_TOO_MANY_ATTEMPTS` | 로그인ID·IP 또는 IP 전체 요청 제한 초과 |
| 500 | `COMMON_INTERNAL_ERROR` | 처리할 수 없는 서버 오류 |

`429` 응답에는 `Retry-After: 60` 헤더를 포함한다. 이 요청은 자격 증명을 검증하지 않고 `LOGIN_FAIL_CNT`를 변경하지 않는다.

## 4. 로그아웃

`POST /auth/logout`은 본문을 받지 않는다. 유효 세션이 있으면 접속로그를 `MANUAL`로 종료한 뒤 세션을 무효화하고 `204 No Content`를 반환한다. 세션이 없거나 만료되면 `401 AUTH_AUTHENTICATION_REQUIRED`를 반환한다.

## 5. 현재 사용자 조회

`GET /auth/me`는 로그인 성공 응답과 같은 `CurrentUserResponse`를 반환한다. 유효 세션이 없으면 `401 AUTH_AUTHENTICATION_REQUIRED`를 반환한다. 역할과 메뉴 표시정보는 데이터베이스에서 다시 조회하지 않고 세션 스냅샷을 사용한다. 계정의 활성·삭제 상태는 보호 API 공통 처리에서 별도로 조회하며 비활성 또는 삭제 상태이면 현재 세션을 만료하고 `401 AUTH_AUTHENTICATION_REQUIRED`를 반환한다.

## 6. 사용자 활동 갱신

`POST /auth/activity`는 본문을 받지 않는다. 계정 상태와 세션 유효성을 확인한 뒤 별도 `lastUserActivityAt`을 갱신하고 `204 No Content`를 반환한다. 다른 API 요청과 `HttpSession` 컨테이너 접근시각은 이 값을 변경하지 않는다. 제한 세션에서도 실제 사용자 활동에 한해 호출할 수 있지만 `absoluteSessionExpiresAt`은 연장하지 않는다.

초기 비밀번호 사용자의 제한 세션은 `/account/initial-registration` 화면에서 `GET /auth/csrf`, `GET /auth/me`, `POST /auth/activity`, `POST /users/me/initial-registration`, `POST /auth/logout`만 호출할 수 있다. 다른 API는 `403 AUTH_PASSWORD_CHANGE_REQUIRED`로 차단한다.

## 7. CSRF 토큰 조회

`GET /auth/csrf`는 로그인 전 호출 가능하며 다음 값을 반환한다.

```json
{
  "headerName": "X-CSRF-TOKEN",
  "token": "opaque-token"
}
```

토큰은 상태 변경 요청의 헤더로만 전송하며 로그·분석 이벤트·영구 저장소에 저장하지 않는다. 이 응답은 캐시하지 않는다.

## 8. 추적성과 권한

| API ID | 기능 ID | 화면 ID | 권한 |
| --- | --- | --- | --- |
| `API-COM-AUTH-001` | `BFD-02-05-01-01` | `COM-001` | 공개 |
| `API-COM-AUTH-002` | `BFD-02-05-01-02` | `COM-001` | 로그인 세션 |
| `API-COM-AUTH-003` | `BFD-02-05-01-03` | `COM-001` | 로그인 또는 제한 세션 |
| `API-COM-AUTH-004` | `BFD-02-05-01-03` | `COM-001` | 일반 또는 제한 세션의 실제 사용자 활동 |
| `API-COM-AUTH-005` | `BFD-02-05-01-01` | `COM-001` | 공개 |
