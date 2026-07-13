# 물리 데이터 모델

DBMS에 구현되는 테이블, 컬럼, 제약조건, 인덱스 및 DBMS별 예외를 관리한다. 논리 모델이 확정된 이후 작성한다.

물리 모델의 원본은 다음 업무영역별 CSV이다.

- `tables/table-{business-area}.csv`: 논리 엔터티와 물리 테이블 매핑
- `columns/column-{business-area}.csv`: 표준용어·도메인 기반 물리 컬럼
- `constraints/constraint-{business-area}.csv`: PK, UK, FK, CK와 애플리케이션 참조
- `indexes/index-{business-area}.csv`: 일반·고유·부분 인덱스

CSV 헤더와 작성 규칙은 `../00.governance/02.catalog-schema.md`를 따른다. PostgreSQL 데이터 타입은 논리 도메인과 `../01.standard/db-type-mapping.csv`에서 파생하며 예외만 컬럼 CSV에 기록한다.
