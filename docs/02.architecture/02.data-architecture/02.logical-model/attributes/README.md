# 엔터티 속성

업무영역별 속성 CSV를 관리한다. 속성 하나를 한 행으로 관리하며 각 CSV가 해당 업무영역 엔터티 속성의 원본이다.

내부 엔터티 속성 CSV는 다음 형식을 사용한다.

```text
entity_name,attribute_sequence,attribute_name,pk_yn,fk_target,required_yn,unique_yn,code_group_or_domain,derived_yn,personal_info_yn,default_value
```

- FK 대상은 `엔터티명.속성명`으로 기록한다.
- `required_yn=N`은 선택 입력 또는 NULL 허용 후보를 의미한다.
- `unique_yn=Y`는 단일 속성 유일성을 의미하며 복합키의 유일성은 PK 및 관계·제약조건 정의에서 관리한다.
- 코드그룹이 확정되지 않은 코드 속성은 우선 `코드그룹:속성명`으로 기록하고 공통코드 설계에서 실제 그룹을 확정한다.
- `도메인:미정`은 표준용어 또는 도메인 추가 검토가 필요한 속성이다.
- 파생속성과 개인정보 여부는 물리설계 및 보안설계 단계에서 최종 확정한다.

연계기관의 영문·국문 필드명을 그대로 보존하는 외부 연계 수신 엔터티는 `interface-attribute-{business-area}.csv`에서 별도 관리한다. 이 파일은 BMS 내부 속성의 표준용어 적용 여부와 외부 필드의 표준화 예외 여부를 함께 관리한다.
