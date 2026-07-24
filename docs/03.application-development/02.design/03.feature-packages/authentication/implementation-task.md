# 로그인·최초 등록 프런트엔드 구현 작업카드

## 1. 목표

개발 DB의 초기 관리자 계정으로 로그인하고, 제한 세션에서 최초 등록을 완료한 뒤 일반 세션으로 승격하여 로그아웃·재로그인할 수 있는 프런트엔드 흐름을 완성한다.

## 2. 참조 설계

- 로그인 화면: `screens/COM-001.md`
- 최초 등록 화면: `../user-management/screens/SYS-009.md`
- 프런트엔드 계약: `frontend-implementation-contract.md`
- 인증 API: `apis/authentication-api.md`
- 최초 등록 API: `../user-management/apis/user-management-api.md`
- OpenAPI: `../../04.openapi/openapi.yaml`
- 인수 시나리오: `tests/acceptance.feature`, `../user-management/tests/acceptance.feature`

## 3. 변경 범위

### 생성·수정

- `frontend/app/**`
- `frontend/features/auth/**`
- `frontend/features/account/initial-registration/**`
- `frontend/features/session/**`
- `frontend/shared/api/**`
- `frontend/shared/types/**`
- `frontend/shared/ui/**`
- `frontend/next.config.ts`
- `frontend/package.json`

### 변경하지 않음

- `backend/**`
- `backend/src/main/resources/db/migration/**`
- 대시보드·알림·파일·메모 관련 경로
- 사용자 목록·등록 화면 `SYS-001`

## 4. 구현 순서

1. `/backend-api` rewrite와 환경변수 기본값 구성
2. 인증 타입, `ApiProblem`, CSRF 저장소와 공통 API 클라이언트 구현
3. `/auth/me` 기반 `AuthProvider`와 라우팅 판정 구현
4. `COM-001` 로그인 화면과 상태·오류 처리 구현
5. `SYS-009` 최초 등록 화면과 비밀번호 정책 구현
6. 세션 활동 갱신, 로그아웃과 공통 애플리케이션 기본 화면 구현
7. 단위·컴포넌트 테스트 작성
8. lint, typecheck, test, build 및 실제 개발 DB 연동 검증

## 5. 완료 조건

- 두 화면설계서의 인수조건을 충족한다.
- `frontend-implementation-contract.md`의 파일 구조와 금지 규칙을 지킨다.
- 모든 자동 검증 명령이 성공한다.
- 초기 관리자 임시 비밀번호 로그인 → 최초 등록 → 로그아웃 → 새 비밀번호 재로그인이 실제 개발 DB에서 성공한다.
- 대시보드·알림·파일·메모 기능을 추가하지 않는다.

## 6. 검증 명령

```text
python scripts/validate_api_security_catalog.py
python scripts/validate_function_catalog.py
python scripts/validate_test_scenarios.py

cd frontend
npm run lint
npm run typecheck
npm run test
npm run build
```

프런트엔드의 `typecheck`, `test` 스크립트와 테스트 의존성은 구현 작업에서 추가한다. 현재 문서 정비 단계에서는 스크립트가 없다는 이유로 설계 검증을 실패시키지 않는다.
