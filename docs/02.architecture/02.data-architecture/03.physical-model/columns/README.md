# 물리 컬럼

업무영역별 물리 컬럼 CSV를 관리한다.

- 시스템관리 원본: `column-system.csv`
- 공통 원본: `column-common.csv`
- 고객관리 원본: `column-customer.csv`
- 영업관리 원본: `column-sales.csv`
- 인력관리 원본: `column-employee.csv`

물리 타입은 논리 도메인과 DBMS 타입 매핑에서 파생하고 예외만 `data_type_override`에 기록한다.
