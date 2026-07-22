# Flyway 데이터베이스 마이그레이션

PostgreSQL 스키마와 필수 기준 데이터 변경을 Flyway 버전 마이그레이션으로 관리한다.

## 파일명

```text
VyyyyMMddHHmm__lower_snake_case_description.sql
```

예시:

```text
V202607221000__create_system_common_tables.sql
```

## 작성 원칙

- 물리 모델 CSV를 먼저 변경하고 대응하는 마이그레이션과 JPA 매핑을 함께 변경한다.
- PostgreSQL 객체명은 큰따옴표 없는 소문자로 작성한다.
- 공유 환경에 적용된 버전 파일은 수정, 삭제, 이름 변경하거나 버전을 재사용하지 않는다.
- 테이블 생성, FK 추가, 인덱스 생성과 필수 기준 데이터 등록은 목적별 파일로 분리한다.
- 운영 사용자, 비밀번호, 개인정보와 환경별 비밀값은 마이그레이션에 포함하지 않는다.
- 잘못된 변경은 기존 파일 수정이 아니라 더 높은 버전의 마이그레이션으로 보정한다.

## 초기 스키마 구성

- `V202607221000__create_initial_tables.sql`: 컬럼, PK, DB CHECK와 설명
- `V202607221010__add_initial_foreign_keys.sql`: `DATABASE/Y` FK
- `V202607221020__create_initial_indexes.sql`: 일반·고유·부분 인덱스
- `V202607221030__seed_initial_roles.sql`: 승인된 초기 역할

앞의 세 DDL 파일은 `scripts/generate_initial_ddl.py`가 물리 모델 CSV에서 생성한다.
공유 환경 적용 전 최신 여부는 다음 명령으로 확인한다.

```powershell
python scripts/generate_initial_ddl.py --check
```

공유 환경에 적용된 마이그레이션은 생성기로 다시 덮어쓰지 않는다. 물리 모델이 변경되면
더 높은 버전의 새 마이그레이션을 작성한다.
