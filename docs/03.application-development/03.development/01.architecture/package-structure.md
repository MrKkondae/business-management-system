# 백엔드 패키지 구조

## 1. 목적

본 문서는 BMS 백엔드의 패키지 명칭과 책임을 정의한다. 모듈 명칭과 경계의 원본은 [애플리케이션 아키텍처](../../../02.architecture/01.application-architecture/application-architecture.md)이며, 이 문서는 구현 시 적용할 패키지 구조를 구체화한다.

## 2. 기본 원칙

- 최상위 업무 패키지는 기능분해도 ID나 개별 화면·기능명이 아닌 업무영역을 기준으로 구성한다.
- 하나의 업무영역에 속하는 세부 기능은 별도 최상위 패키지로 분리하지 않는다.
- 기술 공통과 업무 공통을 각각 `global`, `common`으로 구분한다.
- 업무 데이터는 소유 업무영역 패키지만 변경한다.
- 다른 업무영역은 상대 영역의 공개 애플리케이션 서비스 또는 조회 인터페이스를 사용한다.
- v1.0 제외 업무영역의 패키지는 기능 설계와 구현을 시작할 때 추가한다.

## 3. 최상위 패키지 구조

```text
com.bms.backend
├── global
│   ├── config
│   ├── error
│   ├── security
│   ├── web
│   └── persistence
├── system
├── common
├── customer
├── sales
├── project
├── employee
└── external
    └── g2b
```

## 4. 모듈 명칭과 책임

| 모듈 | 관련 기능 | 책임 |
| --- | --- | --- |
| `global` | 기술 공통 | 프레임워크 설정, 공통 오류, 보안 기반, 웹 및 영속성 기술 지원 |
| `system` | BFD-01 시스템관리 | 사용자, 역할, 메뉴권한, 조직, 공통코드, 설정, 로그, 공지사항 |
| `common` | BFD-02 공통 | 인증 유스케이스, 대시보드 조합, 업무대상, 알림, 첨부파일, 메모 |
| `customer` | BFD-03 고객관리 | 고객사, 고객담당자, 영업활동 |
| `sales` | BFD-04 영업관리 | 사업공고, 영업기회, 사업성 분석, 제안, 수주 |
| `project` | BFD-06·PB-005 최소 프로젝트 | 프로젝트 기본정보, 기간, 연장, 상태 변경 및 인력투입용 조회계약 |
| `employee` | BFD-07 인력관리 | 직원·외주인력 공통정보, 기술경력, 단가, 인력투입 및 투입률 |
| `external.g2b` | 나라장터 연계 | 나라장터 API 클라이언트, 외부 응답 모델과 내부 모델 변환 |

## 5. 업무 모듈 내부 구조

`system`, `common`, `customer`, `sales`, `project`, `employee`에는 다음 구조를 기본으로 적용한다.

```text
{business-area}
├── api             # REST Controller, 요청·응답 DTO
├── application     # 유스케이스, 트랜잭션 경계, 공개 모듈 인터페이스
├── domain          # 엔터티, 값 객체, 업무 규칙, 저장소 인터페이스
└── infrastructure  # JPA 저장소와 기술 구현체
```

단순 조회에도 형식적인 클래스를 만들 필요는 없지만, API 계층이 JPA 저장소를 직접 호출하거나 영속 엔터티를 응답으로 반환하지 않는다.

## 6. 기존 명칭 통합 기준

| 기존 최상위 패키지 | 통합 대상 | 처리 기준 |
| --- | --- | --- |
| `config` | `global.config` | 기술 설정은 `global` 하위에서 관리 |
| `contact` | `customer` | 고객담당자는 고객관리 모듈이 소유 |
| `opportunity` | `sales` | 영업기회는 영업관리 모듈이 소유 |
| `proposal` | `sales` | 제안은 영업관리 모듈이 소유 |
| `dashboard` | `common` | 공통 대시보드가 업무영역별 요약정보를 조합 |
| `contract` | v1.0 제외 | v1.1 계약관리 설계 착수 시 모듈 경계를 확정하고 추가 |
| `billing` | v1.0 제외 | 매출·수금관리 설계 착수 시 아키텍처에서 모듈 명칭을 확정하고 추가 |

새 코드에서는 기존 기능별 최상위 패키지를 사용하지 않는다. 기존 패키지에 구현 코드가 생긴 경우에는 해당 업무영역 모듈로 이동한 뒤 참조와 테스트를 함께 수정한다.

## 7. 프론트엔드 명칭 대응

백엔드 업무 모듈과 프론트엔드 기능 디렉터리는 가능한 한 같은 업무영역 명칭을 사용한다.

| 백엔드 | 프론트엔드 |
| --- | --- |
| `system` | `features/system` |
| `common` | `features/common` 및 `features/auth` |
| `customer` | `features/customer` |
| `sales` | `features/sales` |
| `project` | `features/project` |
| `employee` | `features/employee` |

프론트엔드의 `app` 디렉터리는 라우팅과 페이지 조합을 담당하며 업무 규칙은 `features/{business-area}`에 둔다.

## 8. 변경 이력

| 버전 | 일자 | 변경 내용 |
| --- | --- | --- |
| 0.2 | 2026-07-21 | 애플리케이션 아키텍처 기준으로 모듈 명칭·책임·내부 계층과 기존 패키지 통합 기준 정리 |
| 0.1 | - | 기능별 초기 패키지 구조 작성 |
