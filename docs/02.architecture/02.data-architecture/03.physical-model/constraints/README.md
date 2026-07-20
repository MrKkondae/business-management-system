# 물리 제약조건과 참조

업무영역별 PK, UK, FK, CK와 애플리케이션 참조 메타데이터 CSV를 관리한다. 시스템관리 원본은 `constraint-system.csv`이다.

DB FK를 생성하지 않는 참조도 `enforcement_type=APPLICATION`, `create_yn=N`으로 기록하여 관계와 무결성 점검 대상을 보존한다.

DB FK 생성 대상과 `RESTRICT` 적용 기준은 `../../00.governance/03.physical-model-common-policy.md`를 따른다.
