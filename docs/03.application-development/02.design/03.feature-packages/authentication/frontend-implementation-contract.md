# 인증 프런트엔드 구현 계약

## 1. 문서 상태와 적용 범위

| 항목 | 내용 |
| --- | --- |
| 문서 상태 | `READY` |
| 적용 기능 | 로그인, 세션 복원, 최초 등록 화면 연결, 로그아웃 |
| 관련 화면 | `COM-001`, `SYS-009` |
| 프런트엔드 | Next.js 16 App Router, React 19, TypeScript, Tailwind CSS 4 |
| 제외 범위 | 대시보드, 알림, 파일, 메모, 비밀번호 찾기, MFA, 사용자관리 본 화면 |

본 문서는 두 기능 패키지에 걸친 인증 진입 흐름과 구현 파일 경계의 원본이다. 화면별 필드와 상호작용은 각 화면설계서를 따른다.

## 2. 라우팅과 화면 전이

| URL | 역할 | 접근 처리 |
| --- | --- | --- |
| `/` | 인증 진입점 | `GET /auth/me` 결과에 따라 로그인, 최초 등록 또는 공통 애플리케이션 기본 화면으로 이동 |
| `/login` | 로그인 | 미인증은 유지, 일반 세션은 `/`, 제한 세션은 `/account/initial-registration`으로 이동 |
| `/account/initial-registration` | 최초 등록 | 제한 세션만 유지, 미인증은 `/login`, 일반 세션은 `/`로 이동 |

일반 로그인 후 서버가 반환한 메뉴 중 프런트엔드에 구현된 URL을 `sortOrder`, `menuId` 순서로 선택한다. 구현된 업무 메뉴가 아직 없으면 `/`의 공통 애플리케이션 기본 화면에 사용자 정보와 “현재 사용할 수 있는 메뉴가 없습니다” 안내를 표시한다. 이 화면은 대시보드가 아니며 집계·알림·업무 데이터를 조회하지 않는다.

브라우저의 인증 상태는 URL이나 로컬 저장소 값으로 판정하지 않는다. 새로고침과 직접 URL 접근 시 반드시 `GET /auth/me` 결과로 판정한다.

## 3. 백엔드 연결

### 3.1 동일 출처 프록시

브라우저는 백엔드 주소를 직접 호출하지 않고 `/backend-api`를 사용한다. `next.config.ts`의 rewrite가 서버 경로를 숨긴 채 백엔드로 전달한다.

```text
/backend-api/:path*
→ ${BMS_BACKEND_ORIGIN}/api/v1/:path*
```

개발 기본값은 `BMS_BACKEND_ORIGIN=http://localhost:8080`이다. 환경변수는 서버 전용이며 `NEXT_PUBLIC_` 접두사를 사용하지 않는다. 운영에서는 동일 출처 리버스 프록시 주소를 배포 설정으로 주입한다.

### 3.2 쿠키와 CSRF

- 인증정보는 백엔드가 발급하는 HttpOnly `BMSSESSION` 쿠키만 사용한다.
- 비밀번호, 세션ID, CSRF 토큰을 URL, `localStorage`, `sessionStorage`, 콘솔 및 분석 이벤트에 저장하지 않는다.
- 브라우저 요청은 `credentials: "same-origin"`을 명시한다.
- 상태 변경 요청 전에 `GET /auth/csrf`로 받은 `headerName`과 `token`을 모듈 메모리에 보관한다.
- 로그인, 최초 등록 완료, 로그아웃처럼 세션ID가 교체되거나 무효화된 뒤에는 기존 CSRF 토큰을 즉시 폐기한다.
- `AUTH_CSRF_INVALID`이면 새 토큰을 조회해 멱등성이 보장되는 단일 요청만 한 번 재시도한다. 로그인과 최초 등록은 중복 제출 잠금 상태에서만 재시도한다.

## 4. API 클라이언트 계약

`shared/api`는 HTTP 세부사항을 화면에서 분리한다.

```ts
type ApiProblem = {
  code: string;
  message: string;
  traceId: string;
  fieldErrors: Array<{
    field: string;
    reason: "REQUIRED" | "SIZE" | "FORMAT" | "INVALID";
    message: string;
  }>;
};
```

공통 클라이언트는 다음을 보장한다.

- 성공 응답의 JSON과 `204 No Content`를 구분한다.
- `application/problem+json`을 `ApiProblem`으로 변환한다.
- 알 수 없는 본문이나 네트워크 오류를 안전한 클라이언트 오류로 변환한다.
- `401`에서는 사용자·CSRF 메모리를 제거하고 `/login`으로 이동할 수 있는 오류를 반환한다.
- `429`의 `Retry-After`를 초 단위로 파싱하며 없거나 잘못된 값이면 60초를 사용한다.
- 응답 본문, 요청 헤더와 비밀번호를 콘솔에 출력하지 않는다.

## 5. 상태 소유권

외부 전역 상태 라이브러리는 추가하지 않는다.

| 상태 | 소유 위치 | 보존 |
| --- | --- | --- |
| 로그인 폼 값·오류·제출 상태 | `LoginForm` | 화면 생명주기 |
| 최초 등록 폼 값·오류·제출 상태 | `InitialRegistrationForm` | 화면 생명주기 |
| 현재 사용자·역할·메뉴 | `AuthProvider` | 메모리, 새로고침 시 `/auth/me` 재조회 |
| CSRF 토큰 | `csrf-token-store.ts` 모듈 | 메모리, 세션 경계마다 폐기 |
| 속도 제한 종료시각 | 해당 폼 | 메모리, 화면 이탈 시 폐기 |

비밀번호는 폼 컴포넌트 밖으로 전달하지 않으며 요청 완료 또는 실패 시 필요한 시점에 즉시 비운다.

## 6. Server/Client Component 경계

- `app/**/page.tsx`와 레이아웃은 기본적으로 Server Component로 유지한다.
- 브라우저 쿠키 기반 API 호출, 폼 상태, 라우터 이동, 활동 감지는 명시적인 Client Component에서 수행한다.
- Client Component 경계는 `AuthProvider`, `AuthGate`, `LoginForm`, `InitialRegistrationForm`, `SessionActivityTracker`로 제한한다.
- 서버 렌더링 단계에서 백엔드 세션을 별도로 복제하거나 인증 결과를 캐시하지 않는다.
- 인증 응답은 Next.js 데이터 캐시에 저장하지 않으며 클라이언트 요청은 `cache: "no-store"`를 사용한다.

## 7. 구현 파일 구조

구현 완료 시 다음 구조를 기준으로 한다.

```text
frontend/
├── app/
│   ├── (auth)/
│   │   ├── layout.tsx
│   │   ├── login/page.tsx
│   │   └── account/initial-registration/page.tsx
│   ├── layout.tsx
│   ├── page.tsx
│   └── globals.css
├── features/
│   ├── auth/
│   │   ├── AuthGate.tsx
│   │   ├── AuthProvider.tsx
│   │   ├── LoginForm.tsx
│   │   └── auth-routing.ts
│   ├── account/initial-registration/
│   │   ├── InitialRegistrationForm.tsx
│   │   └── password-policy.ts
│   └── session/
│       └── SessionActivityTracker.tsx
├── shared/
│   ├── api/
│   │   ├── api-client.ts
│   │   ├── auth-api.ts
│   │   ├── csrf-token-store.ts
│   │   └── users-api.ts
│   ├── types/
│   │   ├── api-problem.ts
│   │   └── auth.ts
│   └── ui/
│       ├── FieldError.tsx
│       └── FormAlert.tsx
├── next.config.ts
└── package.json
```

작은 표시 컴포넌트는 사용처에 유지하고, 두 화면 이상에서 실제로 재사용할 때만 `shared/ui`로 이동한다. 배럴 `index.ts`는 만들지 않으며 화면에서 백엔드 API를 직접 `fetch`하지 않는다.

## 8. 구현 변경 경계

### 허용

- 위 파일 구조의 생성·수정
- `package.json`의 검증 스크립트와 프런트엔드 테스트 도구 추가
- 로그인 화면에 필요한 전역 색상·타이포그래피 토큰
- `next.config.ts`의 `/backend-api` rewrite

### 금지

- 백엔드 인증 계약 및 DB 마이그레이션 변경
- 대시보드·알림·파일·메모 화면 또는 API 구현
- JWT 또는 브라우저 저장소 기반 인증 추가
- 전체 API를 브라우저에 직접 노출하기 위한 CORS 완화
- 디자인 시스템 도입을 명분으로 한 대규모 UI 라이브러리 추가

## 9. 활동 및 세션 만료 처리

- 키보드, 포인터, 스크롤과 화면 이동을 실제 사용자 활동으로 본다.
- 이벤트마다 API를 호출하지 않고 마지막 성공 갱신 후 일정 간격으로 묶어 `POST /auth/activity`를 호출한다.
- 자동조회, CSRF 조회와 렌더링은 사용자 활동으로 취급하지 않는다.
- `idleTimeoutSeconds`와 `absoluteSessionExpiresAt`을 화면 타이머의 보조값으로 사용하되 최종 유효성은 서버 응답으로 판정한다.
- `401`이면 인증 메모리를 제거하고 로그인 화면으로 이동하며 “세션이 만료되었습니다”를 한 번 표시한다.

## 10. 구현 검증 기준

### 10.1 정적 검증

구현 과정에서 `package.json`에 다음 스크립트를 마련한다.

```json
{
  "scripts": {
    "lint": "eslint",
    "typecheck": "tsc --noEmit",
    "test": "vitest run",
    "build": "next build"
  }
}
```

필수 통과 명령은 다음과 같다.

```text
cd frontend
npm run lint
npm run typecheck
npm run test
npm run build
```

### 10.2 구조 검증

- `page.tsx`에는 화면 조합과 메타데이터만 두고 요청·검증 로직을 두지 않는다.
- 모든 브라우저 API 경로는 `/backend-api`로 시작한다.
- `fetch(` 호출은 `frontend/shared/api/**`에서만 허용한다.
- `localStorage`, `sessionStorage`, `document.cookie`를 인증 코드에서 사용하지 않는다.
- 비밀번호 필드 타입은 공통 타입과 전역 상태에 포함하지 않는다.
- `(auth)` 라우트에 업무 메뉴나 대시보드 컴포넌트를 포함하지 않는다.

구현 후 다음 검색 결과가 기준을 위반하지 않아야 한다.

```text
rg -n "fetch\\(" frontend --glob "!frontend/shared/api/**"
rg -n "localStorage|sessionStorage|document\\.cookie" frontend
rg -n "localhost:8080|/api/v1" frontend/app frontend/features frontend/shared
rg -n "password" frontend --glob "*.ts" --glob "*.tsx"
```

마지막 검색은 비밀번호가 폼과 요청 DTO 경계 밖으로 전달되거나 로그·스토리지에 저장되지 않는지 사람이 검토한다.

### 10.3 단위·컴포넌트 테스트

- 로그인 필수값, 중복 제출, 인증 실패 후 비밀번호 제거
- `Retry-After` 카운트다운과 만료 후 재제출
- `/auth/me` 결과별 세 경로 분기
- 필드 오류와 폼 오류 연결 및 첫 오류 포커스
- 최초 등록 비밀번호 정책과 확인값 불일치
- CSRF 토큰 폐기와 한 번 재조회
- 제한 세션에서 일반 화면 접근 차단

### 10.4 실제 연동 검증

1. 프런트엔드 `3000`, 백엔드 `8080`, PostgreSQL SSH 터널을 실행한다.
2. `/login` 진입 시 CSRF 준비와 익명 세션 쿠키 발급을 확인한다.
3. 잘못된 로그인과 `429 Retry-After` 화면 상태를 확인한다.
4. 초기 관리자 임시 비밀번호로 로그인해 `/account/initial-registration` 이동을 확인한다.
5. 정책 오류, 확인값 불일치와 선택 개인정보 형식 오류를 확인한다.
6. 최초 등록 성공 후 세션ID 교체, `/` 이동과 일반 세션 응답을 확인한다.
7. 새 비밀번호로 재로그인하고 로그아웃 후 이전 세션 재사용이 실패하는지 확인한다.

## 11. 완료 조건

- `COM-001`, `SYS-009` 화면 인수조건을 모두 충족한다.
- 문서·카탈로그·OpenAPI 검증이 모두 통과한다.
- 프런트엔드 lint, typecheck, test와 production build가 모두 통과한다.
- 실제 개발 DB에서 임시 비밀번호 로그인부터 최초 등록, 로그아웃, 새 비밀번호 재로그인까지 성공한다.
- 브라우저 저장소, 콘솔, URL 및 오류 화면에 비밀번호·세션ID·CSRF 토큰이 노출되지 않는다.
