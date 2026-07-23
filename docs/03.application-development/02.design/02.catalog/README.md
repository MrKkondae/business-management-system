# 설계 카탈로그 관리 기준

## 1. 목적

이 디렉터리는 BFD L4 기능과 후속 화면·API·업무규칙·권한·오류·상태전이의 추적성 및 설계·구현 상태를 구조화된 CSV 원본으로 관리한다.

## 2. 기능 카탈로그

`functions.csv`는 현재 상세 업무기능분해도에 정의된 모든 BFD L4 기능을 한 행씩 등록한다.

```text
function_id,function_name,domain,requirement_id,backlog_id,function_type,screen_ids,api_ids,primary_entities,design_status,implementation_status
```

### 2.1 필드 기준

| 필드 | 기준 |
| --- | --- |
| `function_id` | 상세 업무기능분해도의 유일한 BFD L4 ID |
| `domain` | `system`, `common`, `customer`, `sales`, `contract`, `project`, `employee` 등 애플리케이션 모듈명 |
| `requirement_id` | 직접 추적되는 요구사항 ID. 미정이면 빈 값 |
| `backlog_id` | 직접 추적되는 제품 백로그 ID. 미정이면 빈 값 |
| `function_type` | `COMMAND`, `QUERY`, `BATCH`, `INTERFACE` 중 하나 |
| `screen_ids` | 관련 화면 ID. 여러 값은 세미콜론으로 구분하며 미정이면 빈 값 |
| `api_ids` | `apis.csv`에 등록된 API ID. 여러 값은 세미콜론으로 구분 |
| `primary_entities` | 기능이 직접 조회·변경하는 주요 논리 엔터티. 여러 값은 세미콜론으로 구분 |
| `design_status` | 아래 설계 상태 사용 |
| `implementation_status` | 아래 구현 상태 사용 |

### 2.2 설계 상태

| 상태 | 의미 |
| --- | --- |
| `DESIGN_PENDING` | 1차 설계 범위이며 BFD 분석은 존재하지만 기능 패키지·화면·API 상세설계가 미완료 |
| `DESIGN_IN_PROGRESS` | 기능 상세설계를 작성 중 |
| `READY` | 구현 준비 완료 기준을 충족 |
| `DEFERRED` | 후속 릴리스 또는 후속 설계 범위 |
| `EXCLUDED` | 제품 범위에서 제외 |

`DESIGN_PENDING`, `DESIGN_IN_PROGRESS`, `READY`인 행을 1차 기능 범위로 보고 `DEFERRED`, `EXCLUDED`와 구분한다. 화면·API·권한·업무규칙·테스트와 Manifest가 확정되기 전에는 `READY`로 변경하지 않는다.

### 2.3 구현 상태

| 상태 | 의미 |
| --- | --- |
| `NOT_STARTED` | 구현 미착수 |
| `IN_PROGRESS` | 구현 중 |
| `IMPLEMENTED` | 구현 완료, 검증 전 |
| `VERIFIED` | 정의된 검증과 인수조건 통과 |
| `BLOCKED` | 외부 결정이나 선행 작업으로 구현 중단 |

## 3. 현재 등록 기준

- 최신 제품 백로그와 애플리케이션 아키텍처를 화면목록의 과거 `1차` 표기보다 우선한다.
- 시스템관리의 사용자·역할·메뉴·공통코드·조직, 인증, 고객관리, 영업기회, 수동 사업공고, 최소 프로젝트와 인력관리를 1차 설계 범위로 등록한다.
- 일반사용자를 포함한 사용자별 대시보드는 원천 업무기능 구현 후 지표와 구성을 확정하는 후속 설계 범위로 등록한다.
- 계약관리는 v1.1, 알림은 v1.1, 첨부파일·메모는 v1.2 설계 범위로 등록한다.
- 사업공고 자동수집·검토·재처리, 수주·제안·사업성분석은 후속 상태를 유지한다.
- 최소 프로젝트는 `REQ-PRJ-001`, `PB-005`, `PRJ-001`로 추적한다.
- API·오류·권한 카탈로그는 OpenAPI와 기능 패키지 상세설계가 존재하는 인증 및 사용자관리 1차 단위부터 등록한다.
- 역할·메뉴권한과 다른 기능의 API는 경로·오류·재인증 여부를 상세설계한 뒤 추가하며 예상 경로를 미리 등록하지 않는다.

## 4. 변경 원칙

- 기능 ID나 명칭을 변경할 때는 BFD 원본을 먼저 수정하고 카탈로그를 동기화한다.
- API 카탈로그의 참조 ID와 OpenAPI 확장 속성 일치를 자동 검증한다.
- 하나의 사실을 Markdown과 CSV에 중복 정의하지 않고, 기능 목록과 상태는 `functions.csv`를 원본으로 사용한다.

## 5. API 카탈로그

`apis.csv`는 API의 식별자, 추적관계, 권한, 오류 연결과 설계·구현 상태의 원본이다.

```text
api_id,api_name,domain,method,path,operation_id,function_ids,screen_ids,permission_code,csrf_required_yn,reauthentication_required_yn,request_schema,response_schema,error_codes,feature_package_id,design_status,implementation_status
```

- 여러 기능·화면·오류코드는 세미콜론으로 구분한다.
- `method`, `path`, `operation_id`, 기능·화면·권한과 CSRF 요구 여부는 OpenAPI의 대응 Operation과 일치해야 한다.
- 요청·응답의 필드 구조와 HTTP 응답 계약의 원본은 `../04.openapi/openapi.yaml`이다.
- `NoContent`는 응답 본문이 없는 성공 응답을 나타내는 카탈로그 예약값이다.
- 중요 작업 재인증은 권한 자체의 기본속성과 별도로 API 행의 `reauthentication_required_yn`으로 지정한다.
- API 목록을 기능 패키지 Markdown에 별도 원본으로 다시 관리하지 않는다. 상세문서는 처리 흐름과 예외 설명만 보완한다.

## 6. 오류코드 카탈로그

`error-codes.csv`는 외부 API 오류코드의 의미, HTTP 상태와 사용자 메시지의 원본이다.

```text
error_code,domain,http_status,user_message,exposure_policy,retryable_yn,log_level,description
```

| 통제값 | 의미 |
|---|---|
| `SAFE` | 등록된 메시지를 외부에 표시할 수 있음 |
| `GENERIC_AUTH` | 계정·인증 실패의 내부 원인을 숨긴 공통 메시지만 표시 |
| `GENERIC_INTERNAL` | 내부 예외를 숨긴 일반 장애 메시지만 표시 |

- 내부 접속로그 사유코드와 시스템로그 이벤트코드는 외부 오류코드 카탈로그에 넣지 않는다.
- 하나의 오류코드는 HTTP 상태와 의미를 변경하여 재사용하지 않는다.
- API가 반환하는 모든 외부 오류코드는 해당 API의 `error_codes`에 연결한다.
- OpenAPI Operation은 연결 오류코드의 HTTP 상태 응답을 제공해야 한다.

## 7. 권한 카탈로그

`permissions.csv`는 OpenAPI `x-bms-permission`과 API 카탈로그가 사용하는 권한코드의 원본이다.

```text
permission_code,permission_name,permission_type,session_scope,screen_id,menu_id,data_scope,reauthentication_default_yn,description
```

| `permission_type` | 의미 |
|---|---|
| `PUBLIC` | 인증 세션 없이 호출 가능 |
| `SESSION` | 지정된 종류의 유효한 세션 필요 |
| `MENU` | 일반 세션과 명시적인 역할-메뉴 RBAC 관계 필요 |

- 메뉴 권한은 화면 ID와 초기 데이터의 메뉴 ID를 함께 참조한다.
- `PUBLIC`도 API별 CSRF 요구가 있으면 CSRF 토큰을 검증한다.
- 화면 메뉴 권한은 데이터 범위 권한을 대신하지 않는다.
- 시스템관리자를 역할명으로 우회하지 않고 동일한 권한코드를 사용한다.

## 8. 검증

다음 명령은 CSV 구조·중복·참조, OpenAPI Operation, 기능·화면·기능 패키지·메뉴·오류·권한 연결을 검사한다.

```text
python scripts/validate_api_security_catalog.py
```

기존 기능·데이터·선행데이터 검증과 함께 실행하며 오류가 있으면 설계를 구현 준비 상태로 변경하지 않는다.

## 9. 범위 결정 반영

- 역할·메뉴권한, 메뉴, 공통코드, 조직, 고객담당자, 영업활동과 수동 사업공고의 제품 백로그 ID를 추가했다.
- 영업기회 `SAL-002`와 최소 프로젝트 `PRJ-001`은 1차, 수주 `SAL-005`와 계약 `CON-001`은 후속으로 통일했다.
- 사업공고는 목록·상세조회, 수동 등록·수정 4개 기능만 1차로 두고 자동수집·검토·재처리는 v1.1 후속으로 분리했다.
- `BFD-02-05-01-03 현재 사용자 조회`를 인증 1차 기능으로 추가했다.
- 현재 1차 기능의 요구사항·백로그·화면 참조 미결정 항목은 없다.
