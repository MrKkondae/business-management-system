# 사용자 관리 1차 단위 API 상세설계 초안

## 1. 공통 계약

- 기본 경로: `/api/v1`
- 모든 API는 유효한 세션과 `SYS-001` 메뉴 권한을 요구한다.
- `POST` 요청은 `X-CSRF-TOKEN`을 검증한다.
- 오류는 공통 `Problem` 구조를 사용한다.
- 식별자는 문자열, 날짜·시간은 ISO 8601 UTC로 반환한다.

기계 판독 가능한 계약의 원본은 `../../../04.openapi/openapi.yaml`이다.

## 2. API 목록

| API ID | Method·Path | operationId | 기능 |
| --- | --- | --- | --- |
| `API-SYS-USER-001` | `GET /users` | `systemSearchUsers` | 사용자 목록 조회 |
| `API-SYS-USER-002` | `POST /users` | `systemCreateUser` | 직원·사용자·역할 동시 등록 |
| `API-SYS-USER-003` | `GET /users/registration-options` | `systemGetUserRegistrationOptions` | 조직·초기 역할 옵션 조회 |

## 3. 사용자 목록 조회

### Query parameters

| 이름 | 타입 | 기본값 | 기준 |
| --- | --- | --- | --- |
| `keyword` | string | 없음 | 로그인ID·사용자명·사원번호 부분 일치 |
| `accountStatusCode` | string | 없음 | `ACTIVE`, `INACTIVE` |
| `organizationId` | string | 없음 | 조직ID 정확 일치 |
| `roleId` | string | 없음 | 역할 보유 사용자 |
| `page` | integer | 0 | 0 이상 |
| `size` | integer | 20 | 1~100 |
| `sort` | string[] | `registeredAt,desc` | 허용 필드와 `asc`, `desc` |

### 성공 응답 `200 OK`

```json
{
  "items": [
    {
      "userId": "01K...",
      "employeeId": "01K...",
      "employeeNumber": "2026-001",
      "loginId": "staff01",
      "userName": "홍길동",
      "organizationId": "01K...",
      "organizationName": "개발팀",
      "roleNames": ["일반사용자"],
      "emailMasked": "ab***@example.com",
      "mobileMasked": "010-****-1234",
      "accountStatusCode": "ACTIVE",
      "passwordChangeRequired": true,
      "lastLoginAt": null,
      "registeredAt": "2026-07-22T08:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "sort": ["registeredAt,desc"]
}
```

## 4. 등록 옵션 조회

`GET /users/registration-options`는 미삭제 조직과 역할만 반환한다.

```json
{
  "organizations": [
    {"organizationId": "01K...", "organizationName": "개발팀"}
  ],
  "roles": [
    {"roleId": "01K...", "roleName": "일반사용자"}
  ]
}
```

조직은 조직명, 역할은 역할명 오름차순으로 정렬한다. 빈 조직 또는 역할 목록은 정상 응답이지만 등록 화면은 저장을 차단하고 기준정보 등록 필요를 안내한다.

## 5. 사용자 등록

### 요청

```json
{
  "employeeNumber": "2026-001",
  "userName": "홍길동",
  "organizationId": "01K...",
  "loginId": "staff01",
  "initialRoleIds": ["01K..."],
  "email": "staff01@example.com",
  "mobileNumber": "010-1234-5678"
}
```

### 성공 응답 `201 Created`

```json
{
  "userId": "01K...",
  "employeeId": "01K...",
  "loginId": "staff01",
  "temporaryPassword": "one-time-value",
  "temporaryPasswordExpiresAt": "2026-07-23T08:00:00Z"
}
```

- `Location: /api/v1/users/{userId}`를 반환한다.
- `Cache-Control: no-store`를 반환한다.
- `temporaryPassword`는 이 응답 이후 어떤 조회 API에서도 제공하지 않는다.

### 오류

| HTTP | 오류코드 | 조건 |
| :---: | --- | --- |
| 400 | `COMMON_VALIDATION_FAILED` | 필수·형식 검증 실패 |
| 401 | `AUTH_AUTHENTICATION_REQUIRED` | 세션 없음·만료 |
| 403 | `AUTH_FORBIDDEN` | SYS-001 권한 없음 또는 CSRF 실패 |
| 404 | `COMMON_RESOURCE_NOT_FOUND` | 존재하지 않는 조직·역할 |
| 409 | `SYS_LOGIN_ID_DUPLICATE` | 로그인ID 중복 |
| 409 | `RES_EMPLOYEE_NUMBER_DUPLICATE` | 사원번호 중복 |
| 409 | `COMMON_INVALID_STATE` | 삭제된 조직·역할 선택 |
| 500 | `COMMON_INTERNAL_ERROR` | 내부 서버 오류 |

## 6. 권한과 추적성

| API ID | 기능 ID | 화면 ID | 권한 |
| --- | --- | --- | --- |
| `API-SYS-USER-001` | `BFD-01-01-01-01` | `SYS-001` | `SYS-001` |
| `API-SYS-USER-002` | `BFD-01-01-01-02` | `SYS-001` | `SYS-001` |
| `API-SYS-USER-003` | `BFD-01-01-01-02` | `SYS-001` | `SYS-001` |

