# ADR-004 MyBatis 기반 데이터 접근 표준

## 상태

승인

## 일자

2026-07-24

## 배경

현재 백엔드는 Spring Data JPA 의존성과 JPA 엔터티를 포함하지만 JPA Repository를 사용하지 않고, 실제 업무 데이터 접근은 `JdbcTemplate`으로 수행한다. 인증 영역은 애플리케이션 인터페이스와 JDBC 구현체가 분리되어 있으나 일부 부트스트랩·공통·인력관리 애플리케이션 서비스는 SQL을 직접 실행한다.

이 혼합 구조에서는 기능마다 데이터 접근 방식과 패키지 경계가 달라지고, 서비스가 업무 흐름과 SQL·결과 매핑을 함께 책임한다. 향후 조회 조건과 조인이 증가하면 서비스 가독성, SQL 검토 가능성, 테스트 일관성과 데이터 접근 코드 재사용성이 더 낮아질 수 있다.

BMS는 PostgreSQL의 명시적인 SQL, 잠금과 갱신 건수 검증이 중요한 내부 업무시스템이다. 따라서 SQL 제어권을 유지하면서 애플리케이션 계층에서 데이터 접근 기술을 분리할 표준이 필요하다.

## 결정

### 기술 기준

- 백엔드 데이터 접근 표준은 Spring Boot, PostgreSQL, MyBatis, HikariCP와 Flyway 조합으로 한다.
- Spring Boot 4 계열과 Java 17에 호환되는 `mybatis-spring-boot-starter` 4 계열을 사용한다.
- PostgreSQL은 업무 데이터와 스키마의 단일 원본으로 유지한다.
- HikariCP는 Spring Boot가 관리하는 단일 `DataSource` 커넥션 풀로 사용한다. 애플리케이션 코드가 JDBC `Connection`을 직접 생성·종료하거나 별도 풀을 만들지 않는다.
- 스키마 생성과 변경은 Flyway로만 수행한다. MyBatis Mapper가 스키마를 생성하거나 변경하지 않는다.
- 신규 기능에는 JPA Repository와 JPA 엔터티를 추가하지 않는다. 기존 JPA 의존성과 엔터티는 MyBatis 전환 및 PostgreSQL 스키마 검증 완료 후 제거했다.
- 기존 `JdbcTemplate` 구현은 단계적 전환 기간에만 허용한다. `api`, `application`, `domain` 패키지에는 신규 `JdbcTemplate`, `SqlSession` 또는 MyBatis Mapper 의존성을 추가하지 않는다.
- 이 결정은 ADR-001의 `JPA ddl-auto=validate` 기준 중 데이터 접근 기술 부분을 대체한다. 스키마 검증은 Flyway 전체 적용 및 PostgreSQL 통합 테스트가 담당한다.

### 계층과 의존 방향

데이터 접근 의존 방향은 다음과 같이 고정한다.

```text
API
  → Application Service
    → 출력 Port/Repository/Query 인터페이스
      ← MyBatis Adapter
        → MyBatis Mapper
          → Mapper XML
            → PostgreSQL
```

- API 계층은 자신의 업무영역 애플리케이션 서비스만 호출한다.
- 애플리케이션 서비스는 유스케이스 흐름과 트랜잭션 경계를 담당하고 SQL이나 결과 매핑을 포함하지 않는다.
- Aggregate 저장·조회처럼 도메인 의미를 갖는 저장소 계약은 `domain.repository`에 둔다.
- 특정 유스케이스를 위한 조회, 명령 저장, 외부 기술 연계 계약은 `application.port.out`에 둔다.
- 다른 업무영역이 사용하는 공개 서비스와 조회 계약은 해당 업무영역의 `application` 경계에 둔다.
- MyBatis Adapter는 저장소 또는 출력 Port를 구현하며 영속 모델과 애플리케이션·도메인 모델 사이를 변환한다.
- MyBatis Mapper는 `infrastructure.persistence.mybatis.mapper` 내부 구현으로 한정한다. Controller와 애플리케이션 서비스가 Mapper를 직접 주입받지 않는다.
- 인터페이스와 Mapper가 완전히 같은 단순 위임만 반복하는 경우에도 Mapper를 애플리케이션에 노출하지 않는다. Adapter가 별도 변환·오류 처리 없이 위임만 하더라도 기술 교체 경계 역할을 유지한다.
- 범용 `BaseDao`, `BaseMapper`, 모든 테이블을 다루는 공통 DAO는 만들지 않는다. 업무 의미가 드러나는 `Repository`, `Store`, `Query` 또는 `Reader` 이름을 사용한다.

### 패키지와 SQL 파일 배치

신규 데이터 접근 코드는 다음 구조를 기본으로 한다.

```text
com.bms.backend.{business-area}
├── api
├── application
│   └── port
│       └── out
├── domain
│   └── repository
└── infrastructure
    └── persistence
        └── mybatis
            ├── adapter
            ├── mapper
            └── model

backend/src/main/resources/mybatis/mapper/{business-area}
└── {Feature}Mapper.xml
```

- Java Mapper 인터페이스와 Mapper XML namespace는 일치시킨다.
- Mapper XML은 소유 업무영역별 디렉터리에 둔다. 다른 업무영역의 Mapper XML이나 내부 Mapper를 직접 참조하지 않는다.
- `model`에는 MyBatis 입출력에만 필요한 영속 레코드와 파라미터 객체를 둔다. API 요청·응답 DTO를 Mapper 파라미터나 결과 타입으로 사용하지 않는다.
- 기존 코드의 패키지는 일괄 이동하지 않고 해당 기능을 MyBatis로 전환할 때 새 기준에 맞춘다.

### SQL 작성 기준

- 운영 SQL은 Mapper XML에 작성한다. SQL 어노테이션은 테스트 보조 코드 외에는 사용하지 않는다.
- 파라미터는 `#{}` 바인딩을 사용한다. `${}` 문자열 치환은 SQL 식별자를 사전 허용목록에서 선택해야 하는 예외를 별도 검토한 경우가 아니면 금지한다.
- `SELECT *`를 사용하지 않고 필요한 컬럼을 명시한다.
- 복합 조회와 이름이 다른 컬럼은 명시적인 `resultMap`을 사용한다. 단순 조회의 자동 매핑은 팀 설정으로 일관되게 적용한다.
- 공통 `<sql>` 조각은 같은 Mapper 또는 같은 업무영역 안의 반복 컬럼·조건에만 제한적으로 사용한다. 업무영역 전체가 결합되는 전역 SQL 조각은 만들지 않는다.
- 목록 조회는 명시적인 정렬 순서와 안정적인 동률 정렬 기준을 포함한다.
- 동적 조건은 `<if>`, `<choose>`, `<where>`, `<foreach>`를 사용하고 Java에서 SQL 문자열을 조합하지 않는다.
- PostgreSQL 전용 구문, `FOR UPDATE`, 낙관적 조건과 논리삭제 조건은 XML에 명시하여 동시성 의도를 보존한다.
- 단건 등록·수정·삭제는 기대 갱신 건수를 Adapter 또는 애플리케이션 규칙에서 검증한다.
- `Y/N` 플래그, 업무 enum, 시간 타입처럼 반복되는 변환은 검증된 TypeHandler 또는 명시적 매핑으로 통일한다.
- SQL 재사용보다 업무 의미가 있는 저장소·조회 메서드 재사용을 우선한다.

### 트랜잭션과 커넥션 풀

- 트랜잭션은 애플리케이션 서비스의 공개 유스케이스 메서드에 설정한다.
- 조회 전용 유스케이스는 `@Transactional(readOnly = true)`를 기본으로 한다.
- Mapper에는 트랜잭션 어노테이션을 선언하지 않는다. Adapter도 독립 트랜잭션이 반드시 필요한 별도 설계가 아니라면 상위 트랜잭션에 참여한다.
- 여러 저장소 변경이 하나의 업무 결과를 구성하면 하나의 애플리케이션 트랜잭션으로 묶는다.
- 단일 백엔드 인스턴스의 초기 기준은 최대 10개, 최소 유휴 2개로 한다. 연결 대기 5초, 검증 2초, 유휴 10분, 최대 수명 30분을 기준으로 하며 모든 값은 `BMS_DB_*` 환경변수로 재정의한다.
- 운영 반영 전 `애플리케이션 인스턴스 수 × maximum-pool-size`에 Flyway, 관리자와 모니터링 연결 여유분을 더한 값이 PostgreSQL의 일반 사용자 연결 한도를 넘지 않는지 확인한다.
- 풀 크기와 타임아웃은 부하 시험 결과 및 DB·프록시의 연결 종료 정책을 기준으로 조정한다. 다중 인스턴스로 전환할 때는 인스턴스마다 같은 최대값을 그대로 적용하지 않고 전체 연결 예산을 다시 계산한다.
- 커넥션 누수 탐지는 기본적으로 비활성화한다. 장애 진단 시에만 임계값을 임시 적용하고 정상화 후 해제한다.

### 테스트 기준

- 애플리케이션 서비스 단위 테스트는 출력 Port나 Repository를 대역으로 교체하여 업무 흐름과 트랜잭션 결과를 검증한다.
- Mapper와 Adapter 테스트는 Flyway가 적용된 실제 PostgreSQL 호환 환경에서 실행한다.
- PostgreSQL 전용 SQL, 잠금, 제약조건, TypeHandler와 갱신 건수는 PostgreSQL 통합 테스트로 검증한다.
- H2 성공만으로 Mapper 검증을 완료한 것으로 보지 않는다.
- SQL 또는 Mapper 변경 시 해당 Mapper 테스트와 영향을 받는 애플리케이션·API 테스트를 함께 실행한다.

### 전환 원칙

- 전환은 기능 단위로 수행하며 JDBC와 MyBatis의 일시적 공존을 허용한다.
- 서비스가 SQL을 직접 실행하는 코드부터 출력 Port와 MyBatis Adapter로 분리한다.
- 이미 애플리케이션 인터페이스가 있는 인증 영역은 계약을 유지하고 JDBC 구현체를 MyBatis 구현체로 교체한다.
- 한 기능의 전환이 완료되면 같은 기능의 JDBC 구현과 중복 SQL을 제거한다. 동일 기능에 JDBC와 MyBatis 구현을 장기간 병행하지 않는다.
- 2026-07-24 PostgreSQL Mapper 통합 테스트 확보 후 JPA 의존성, JPA 엔터티와 `spring.jpa.*` 설정 제거를 완료했다.

## 영향

- 서비스 코드에서 SQL과 JDBC 결과 매핑이 제거되어 유스케이스 흐름이 명확해진다.
- SQL은 업무영역별 Mapper XML에서 검색·검토하고 PostgreSQL 특화 기능을 명시적으로 사용할 수 있다.
- MyBatis Mapper를 직접 호출하는 것보다 Adapter 코드가 늘지만 애플리케이션이 데이터 접근 기술과 분리된다.
- XML namespace, 파라미터와 결과 매핑 오류는 컴파일 시점에 모두 발견되지 않으므로 실제 PostgreSQL 기반 Mapper 테스트가 필수 품질 게이트가 된다.
- JPA의 시작 시점 스키마 검증이 제거되므로 Flyway 전체 적용 테스트와 데이터 카탈로그·물리 스키마 검증을 유지해야 한다.
- 데이터 접근 방식의 일시적 공존 기간에는 같은 기능을 두 기술로 중복 구현하지 않도록 전환 범위를 명확히 관리해야 한다.

## 검토한 대안

### JdbcTemplate 유지

SQL 제어는 가능하지만 반복 매핑과 SQL 배치 규칙이 약하고 현재 서비스 직접 의존 문제를 그대로 둘 가능성이 있어 표준안으로 선택하지 않았다.

### Spring Data JPA로 통일

단순 CRUD에는 유리하지만 현재 구현과 운영 쿼리가 SQL 중심이고 PostgreSQL 잠금·복합 조회를 명시적으로 다뤄야 하므로 표준안으로 선택하지 않았다.

### JPA와 MyBatis 병행을 영구 표준으로 채택

기능별 선택 유연성은 있으나 매핑 모델, 트랜잭션과 테스트 기준이 이중화된다. 현재 규모에서는 복잡성이 더 크므로 전환 기간 외의 영구 병행은 채택하지 않았다.

## 재검토 조건

다음 중 하나가 발생하면 데이터 접근 표준을 별도 ADR로 재검토한다.

- 대규모 분석 쿼리에 타입 안전 SQL 생성 도구가 필요한 경우
- Aggregate 변경 추적과 객체 그래프 영속화가 SQL 제어보다 중요해진 경우
- 읽기·쓰기 저장소를 분리하거나 비관계형 저장소를 도입하는 경우
- 다중 DataSource 또는 읽기 전용 Replica 라우팅이 필요한 경우
