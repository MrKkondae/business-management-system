# 데이터 모델 다이어그램

전체 및 업무영역별 데이터 모델 다이어그램 소스를 관리한다. 가능한 경우 논리·물리 모델의 구조화된 원본에서 재생성 가능한 형식을 사용한다.

## 물리 ERD

- [BMS 전체 ERD](physical/00.overall-erd.md): 물리화된 전체 업무영역 관계와 미물리화 논리 확장 경계
- [시스템 관리 상세 ERD](physical/01.system-management-erd.md): 계정·권한, 조직·코드·설정, 외부연계·로그 및 공지사항
- [공통 업무영역 상세 ERD](physical/02.common-management-erd.md): 알림, 업무대상, 첨부파일, 메모 및 업무영역 간 적용 관계
- [고객관리 상세 ERD](physical/03.customer-management-erd.md): 고객사, 고객담당자, 영업활동 및 후속 업무 연계
- [영업관리 상세 ERD](physical/04.sales-management-erd.md): 사업공고 수집·검토, 영업기회, 사업성분석, 제안 및 수주
- [프로젝트관리 상세 ERD](physical/06.project-management-erd.md): 인력투입용 최소 프로젝트 기준정보, 최초·현재 종료일 및 상태
- [인력관리 상세 ERD](physical/07.resource-management-erd.md): 인력 하위유형, 이력·역량·단가, 프로젝트 투입 및 월별실적

물리 ERD는 데이터 카탈로그 CSV에서 생성하는 파생 산출물이다. 생성 파일을 직접 수정하지 않고 대응 생성 스크립트 또는 원본 CSV를 수정한다.

## 업무영역별 ERD 생성기

`scripts/generate_erd.py`는 선택한 업무영역의 데이터 카탈로그 CSV를 읽어 Mermaid 기반의 상세 물리 ERD Markdown을 생성한다. DB 스키마나 원본 CSV를 변경하거나 DDL을 생성하지 않으며, 실행할 때 해당 ERD Markdown을 최신 카탈로그 내용으로 덮어쓴다.

### 입력

- `table-{area}.csv`: 엔터티와 물리 테이블 매핑
- `column-{area}.csv`: 컬럼, 논리 도메인, NULL 허용 여부, 기본값 및 PK 정보
- `constraint-{area}.csv`: PK, FK, CHECK 제약조건과 참조 집행 방식
- `index-{area}.csv`: 일반·고유·부분 인덱스
- [`db-type-mapping.csv`](../01.standard/db-type-mapping.csv): 논리 도메인의 PostgreSQL 타입 매핑

### 처리 내용

1. UTF-8 BOM 유무와 관계없이 데이터 카탈로그 CSV를 읽는다.
2. 논리 도메인을 PostgreSQL 데이터 타입으로 변환한다.
3. PK·FK, NULL 허용 여부 및 기본값을 ERD 속성에 표시한다.
4. FK 컬럼과 고유 인덱스를 기준으로 관계 카디널리티를 계산한다.
5. 데이터베이스 제약으로 집행하는 `DB FK`와 애플리케이션에서 집행하는 `APP REF`를 구분한다.
6. 업무영역 설정에 따라 전체 관계 개요와 읽기 쉬운 크기의 상세 ERD를 생성한다.
7. 업무기능 추적성, 관계 구현 명세, CHECK 제약조건 및 인덱스를 함께 문서화한다.

### 출력

- [`physical/00.overall-erd.md`](physical/00.overall-erd.md): 전체 통합 물리 ERD 및 논리 확장 경계
- [`physical/01.system-management-erd.md`](physical/01.system-management-erd.md): 시스템 관리 상세 물리 ERD
- [`physical/02.common-management-erd.md`](physical/02.common-management-erd.md): 공통 업무영역 상세 물리 ERD
- [`physical/03.customer-management-erd.md`](physical/03.customer-management-erd.md): 고객관리 상세 물리 ERD
- [`physical/04.sales-management-erd.md`](physical/04.sales-management-erd.md): 영업관리 상세 물리 ERD
- [`physical/06.project-management-erd.md`](physical/06.project-management-erd.md): 프로젝트관리 상세 물리 ERD
- [`physical/07.resource-management-erd.md`](physical/07.resource-management-erd.md): 인력관리 상세 물리 ERD

현재 생성기는 `system`, `common`, `customer`, `sales`, `project`, `employee` 영역을 지원한다. 다른 업무영역을 추가할 때는 해당 영역의 카탈로그 파일과 다이어그램 구성을 `AREA_CONFIGS`에 명시적으로 확장한다. `scripts/generate_system_erd.py`는 기존 실행 명령 호환을 위한 시스템 관리 전용 래퍼이다.

### 실행 및 검증

저장소 루트에서 다음 명령을 실행한다.

```powershell
# 한 영역 생성
python scripts/generate_erd.py --area common

# 지원하는 모든 영역 생성
python scripts/generate_erd.py --all

# 전체 통합 ERD만 생성
python scripts/generate_erd.py --overview

# 공통 영역 카탈로그 검증
python scripts/validate_data_catalog.py --review-area common --report tmp/data-catalog-validation-common.csv
```
