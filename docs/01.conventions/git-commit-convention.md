# Git Commit Convention

## 1. 목적

본 문서는 프로젝트의 Git Commit Message 작성 규칙을 정의한다.

일관된 Commit Message는 다음과 같은 효과를 제공한다.

* 변경 이력 추적성 향상
* 코드 리뷰 효율 향상
* 릴리즈 노트 자동 생성 지원
* AI 코딩 도구(Codex, Claude Code, Cursor 등)의 변경 이력 이해도 향상
* 프로젝트 품질 관리 표준화

---

# 2. 기본 형식

```
<type>(<scope>): <summary>
```

예시

```
feat(customer): add customer registration API
fix(contract): resolve contract save error
docs(architecture): add package structure guide
```

---

# 3. Type 정의

| Type     | 설명              | 사용 예                  |
| -------- | --------------- | --------------------- |
| feat     | 새로운 기능 추가       | 고객관리 CRUD 추가          |
| fix      | 버그 수정           | 로그인 오류 수정             |
| refactor | 기능 변경 없는 구조 개선  | Service 분리            |
| docs     | 문서 변경           | README 수정             |
| style    | 코드 스타일 변경       | Formatting, Import 정리 |
| test     | 테스트 코드          | JUnit 추가              |
| perf     | 성능 개선           | SQL 최적화               |
| build    | 빌드 설정 변경        | Maven 수정              |
| ci       | CI/CD 변경        | GitHub Actions        |
| chore    | 환경설정, 프로젝트 유지보수 | Docker, Package 구조    |

---

# 4. Scope 작성 규칙

Scope는 변경 대상 업무 영역 또는 모듈명을 사용한다.

예시

```
customer
contact
opportunity
proposal
contract
project
billing
dashboard
common
config
architecture
build
```

기능분해도 ID(BMS100 등)는 Scope로 사용하지 않는다.

좋은 예

```
feat(customer)
```

권장하지 않는 예

```
feat(bms100)
```

---

# 5. Summary 작성 규칙

Summary는 현재형 동사로 시작하며 50자 이내를 권장한다.

좋은 예

```
add customer search API
create contract entity
remove unused configuration
update Docker compose file
```

권장하지 않는 예

```
customer 수정
버그 수정
최종
작업
```

---

# 6. Commit Message 예시

## 기능 추가

```
feat(customer): add customer registration API
```

```
feat(project): implement project dashboard
```

---

## 버그 수정

```
fix(customer): resolve duplicate customer validation
```

---

## 리팩토링

```
refactor(contract): separate service and repository layer
```

---

## 문서

```
docs(architecture): add backend package structure
```

---

## 환경설정

```
chore(init): initialize project structure
```

```
chore(build): configure Docker environment
```

---

# 7. AI 코딩 도구 사용 시 권장사항

AI가 Commit Message를 생성하는 경우 다음 사항을 준수한다.

* Type은 변경 목적에 맞게 선택한다.
* Scope는 업무 도메인명을 사용한다.
* Summary는 변경 내용을 명확히 표현한다.
* "수정", "최종", "작업"과 같은 모호한 표현은 사용하지 않는다.

---

# 8. 권장 예시

```
feat(customer): add customer registration API

feat(contract): implement contract search

fix(project): resolve project status update issue

refactor(common): extract response utility

docs(architecture): define backend package structure

chore(init): initialize standard project structure
```

---

# 9. 금지 예시

```
수정

버그

최종

최종2

fix

commit

작업

aaa
```

---

# 10. 프로젝트 원칙

* Commit 하나는 하나의 목적만 가진다.
* Feature와 Refactor를 하나의 Commit에 혼합하지 않는다.
* 의미 없는 Commit Message는 작성하지 않는다.
* Commit History만 보더라도 프로젝트 변경 이력을 이해할 수 있어야 한다.
* AI와 사람이 모두 이해하기 쉬운 Commit Message를 작성한다.
