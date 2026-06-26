# Backend Package Structure

## 기본 원칙

패키지는 기능분해도 ID가 아닌 업무 도메인명을 기준으로 구성한다.

AI 코딩 도구와 개발자가 패키지명만 보고 역할을 이해할 수 있도록 한다.

## 구조
```text
com.bms.backend
├── common
├── config
├── customer
├── contact
├── opportunity
├── proposal
├── contract
├── project
├── billing
└── dashboard
```

## 기능 ID 매핑
```text

| 기능 ID  | 업무 영역   | Package     |
| ------ | ------- | ----------- |
| BMS100 | 고객관리    | customer    |
| BMS200 | 담당자관리   | contact     |
| BMS300 | 영업기회관리  | opportunity |
| BMS400 | 제안/견적관리 | proposal    |
| BMS500 | 계약관리    | contract    |
| BMS600 | 프로젝트관리  | project     |
| BMS700 | 청구/매출관리 | billing     |
| BMS800 | 대시보드    | dashboard   |
```