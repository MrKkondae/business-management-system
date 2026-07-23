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
| `api_ids` | API 카탈로그 작성 전에는 빈 값. 여러 값은 세미콜론으로 구분 |
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

## 4. 변경 원칙

- 기능 ID나 명칭을 변경할 때는 BFD 원본을 먼저 수정하고 카탈로그를 동기화한다.
- API·화면 카탈로그가 생성되면 참조 ID의 존재 여부를 자동 검증한다.
- 하나의 사실을 Markdown과 CSV에 중복 정의하지 않고, 기능 목록과 상태는 `functions.csv`를 원본으로 사용한다.

## 5. 범위 결정 반영

- 역할·메뉴권한, 메뉴, 공통코드, 조직, 고객담당자, 영업활동과 수동 사업공고의 제품 백로그 ID를 추가했다.
- 영업기회 `SAL-002`와 최소 프로젝트 `PRJ-001`은 1차, 수주 `SAL-005`와 계약 `CON-001`은 후속으로 통일했다.
- 사업공고는 목록·상세조회, 수동 등록·수정 4개 기능만 1차로 두고 자동수집·검토·재처리는 v1.1 후속으로 분리했다.
- `BFD-02-05-01-03 현재 사용자 조회`를 인증 1차 기능으로 추가했다.
- 현재 1차 기능의 요구사항·백로그·화면 참조 미결정 항목은 없다.
