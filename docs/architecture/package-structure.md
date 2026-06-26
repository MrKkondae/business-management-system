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