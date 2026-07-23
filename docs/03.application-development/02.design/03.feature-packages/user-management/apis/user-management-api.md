# 사용자 관리 1차 단위 API 상세설계 초안

## 1. 공통 계약

- 기본 경로: `/api/v1`
- 모든 API는 유효한 세션과 `SYS-001` 메뉴 권한을 요구한다.
- `POST` 요청은 `X-CSRF-TOKEN`을 검증한다.
- 오류는 공통 `Problem` 구조를 사용한다.
- 식별자는 문자열, 날짜·시간은 ISO 8601 UTC로 반환한다.

기계 판독 가능한 계약의 원본은 `../../../04.openapi/openapi.yaml`이다.

## 2. API 목록

API ID, Method·Path, operationId, 기능·화면·권한·오류 연결은 `../../../02.catalog/apis.csv`를 원본으로 한다. 본 문서는 사용자 관리 API의 처리 순서와 필드 기준만 보완한다.

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
  "temporaryPassword": "initial-registration-value",
  "temporaryPasswordExpiresAt": "2026-07-23T08:00:00Z"
}
```

- `Location: /api/v1/users/{userId}`를 반환한다.
- `Cache-Control: no-store`를 반환한다.
- `temporaryPassword`는 이 응답 이후 어떤 조회 API에서도 제공하지 않는다.
- `temporaryPasswordExpiresAt`은 서버가 `TEMP_PWD_EXPIRE_DTM`에 저장한 발급 후 24시간 만료일시와 동일하다.

### 오류

사용자 등록 API의 오류코드와 HTTP 상태 연결은 `../../../02.catalog/apis.csv`를 따른다. 중복과 삭제된 기준정보 오류가 발생하면 직원·사용자·역할 관계를 모두 롤백한다.

## 6. 권한과 추적성

API별 기능·화면·권한 연결은 `../../../02.catalog/apis.csv`, `SYS-001` 권한 정의는 `../../../02.catalog/permissions.csv`를 원본으로 한다.
