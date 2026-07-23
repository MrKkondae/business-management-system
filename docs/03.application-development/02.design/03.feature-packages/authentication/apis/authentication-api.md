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

API ID, Method·Path, operationId, 기능·화면·권한·오류 연결은 `../../../02.catalog/apis.csv`를 원본으로 한다. 본 문서는 인증 API의 처리 순서와 필드별 보안 기준만 보완한다.

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
  "absoluteSessionExpiresAt": "2026-07-23T09:00:00Z"
}
```

- `passwordChangeRequired=true`이면 `menus`는 빈 배열이다.
- `passwordChangeRequired=true`인 제한 세션은 `idleTimeoutSeconds=600`과 로그인 후 30분·임시 비밀번호 만료 중 빠른 `absoluteSessionExpiresAt`을 반환한다. 일반 세션은 `idleTimeoutSeconds=900`과 로그인 후 8시간의 절대 만료일시를 반환한다.
- 역할과 메뉴는 로그인 시점 스냅샷이며 자동 갱신하지 않는다. DB 보안버전이 세션 버전과 달라지면 다음 보호 요청에서 세션을 종료한다.
- 응답과 함께 세션 쿠키를 발급하고 세션ID를 교체한다.

### 오류

로그인 API의 오류코드와 HTTP 상태 연결은 `../../../02.catalog/apis.csv`를 따른다. 속도 제한 응답에는 `Retry-After: 60`을 포함하며 자격 증명을 검증하거나 `LOGIN_FAIL_CNT`를 변경하지 않는다.

## 4. 로그아웃

`POST /auth/logout`은 본문을 받지 않는다. 유효 세션이 있으면 접속로그를 `MANUAL`로 종료한 뒤 세션을 무효화하고 `204 No Content`를 반환한다. 세션이 없거나 만료되면 `401 AUTH_AUTHENTICATION_REQUIRED`를 반환한다.

## 5. 현재 사용자 조회

`GET /auth/me`는 로그인 성공 응답과 같은 `CurrentUserResponse`를 반환한다. 유효 세션이 없으면 `401 AUTH_AUTHENTICATION_REQUIRED`를 반환한다. 역할과 메뉴 표시정보는 데이터베이스에서 다시 조회하지 않고 세션 스냅샷을 사용한다. 계정의 활성·삭제 상태는 보호 API 공통 처리에서 별도로 조회하며 비활성 또는 삭제 상태이면 현재 세션을 만료하고 `401 AUTH_AUTHENTICATION_REQUIRED`를 반환한다.

## 6. 사용자 활동 갱신

`POST /auth/activity`는 본문을 받지 않는다. 계정 상태와 세션 유효성을 확인한 뒤 별도 `lastUserActivityAt`을 갱신하고 `204 No Content`를 반환한다. 다른 API 요청과 `HttpSession` 컨테이너 접근시각은 이 값을 변경하지 않는다. 제한 세션에서도 실제 사용자 활동에 한해 호출할 수 있지만 `absoluteSessionExpiresAt`은 연장하지 않는다.

초기 비밀번호 사용자의 제한 세션은 `/account/initial-registration` 화면에서 `GET /auth/csrf`, `GET /auth/me`, `POST /auth/activity`, `POST /users/me/initial-registration`, `POST /auth/logout`만 호출할 수 있다. 다른 API는 `403 AUTH_PASSWORD_CHANGE_REQUIRED`로 차단한다.

## 7. 중요 작업 재인증

`POST /auth/reauthenticate`는 다음 요청으로 현재 비밀번호를 검증한다.

```json
{
  "password": "plain-text-only-in-transit"
}
```

- 일반 세션과 유효한 CSRF 토큰이 필요하다.
- 성공하면 세션의 `reauthenticatedAt`을 현재시각으로 갱신하고 `204 No Content`를 반환한다.
- 비밀번호 불일치는 세션을 유지한 채 `403 AUTH_REAUTHENTICATION_FAILED`를 반환하고 계정 로그인실패건수를 증가시키지 않는다. 재인증 요청은 세션별 분당 5회로 제한한다.
- 시스템관리자 역할·메뉴권한 변경, 다른 사용자 비밀번호 초기화와 계정 활성·비활성 등 중요 작업은 최근 로그인 또는 재인증 후 10분 안에서만 허용한다.
- 중요 작업 시각 조건을 충족하지 않으면 `403 AUTH_REAUTHENTICATION_REQUIRED`를 반환한다.
- 비밀번호, 재인증 응답과 보안로그에는 `Cache-Control: no-store` 및 비밀정보 제외 정책을 적용한다.

## 8. CSRF 토큰 조회

`GET /auth/csrf`는 로그인 전 호출 가능하며 다음 값을 반환한다.

```json
{
  "headerName": "X-CSRF-TOKEN",
  "token": "opaque-token"
}
```

토큰은 상태 변경 요청의 헤더로만 전송하며 로그·분석 이벤트·영구 저장소에 저장하지 않는다. 이 응답은 캐시하지 않는다.

## 9. 추적성과 권한

API별 기능·화면·권한 연결은 `../../../02.catalog/apis.csv`, 권한코드의 의미는 `../../../02.catalog/permissions.csv`를 원본으로 한다.
