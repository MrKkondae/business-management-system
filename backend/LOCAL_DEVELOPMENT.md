# 백엔드 로컬 개발 환경

## 1. 계정과 데이터베이스 구성

개발자는 각자 분리된 PostgreSQL 데이터베이스와 애플리케이션 접속 계정을 사용한다.
Flyway는 개인 계정이 아니라 공통 마이그레이션 계정으로 실행한다.

| 구분 | 예시 | 용도 |
| --- | --- | --- |
| 개발자 DB | `bms_dev_staff` | 개발자별 스키마와 데이터 격리 |
| 개발자 계정 | `bms_dev_staff` | 애플리케이션의 조회·등록·수정·삭제 |
| 마이그레이션 계정 | `bms_migrator` | Flyway DDL 실행과 DB 객체 소유 |

PostgreSQL 역할은 서버 전체에서 생성되지만 DB 접속, 스키마와 객체 권한은 데이터베이스별로
설정해야 한다. 따라서 개발자 DB를 추가할 때마다 아래 관리자 작업을 수행한다.

## 2. DB 관리자 작업

다음 예시는 개발자 DB와 계정이 모두 `bms_dev_staff`인 경우다. 실제 할당 명칭으로
바꾸어 PostgreSQL 관리자 계정으로 실행한다.

### 2.1 빈 개발DB 권한 설정

먼저 어느 DB에서든 개발DB 소유자와 접속 권한을 설정한다.

```sql
ALTER DATABASE bms_dev_staff OWNER TO bms_migrator;
GRANT CONNECT ON DATABASE bms_dev_staff TO bms_migrator;
GRANT CONNECT ON DATABASE bms_dev_staff TO bms_dev_staff;
```

그다음 반드시 `bms_dev_staff` DB에 다시 접속한 상태에서 실행한다. DBeaver 등 GUI를
사용하면 해당 DB 연결을 새로 열고, `psql`을 사용하면 `\connect bms_dev_staff`로 전환한다.

```sql
ALTER SCHEMA public OWNER TO bms_migrator;

GRANT USAGE, CREATE
ON SCHEMA public
TO bms_migrator;

GRANT USAGE
ON SCHEMA public
TO bms_dev_staff;

GRANT SELECT, INSERT, UPDATE, DELETE
ON ALL TABLES IN SCHEMA public
TO bms_dev_staff;

ALTER DEFAULT PRIVILEGES
FOR ROLE bms_migrator
IN SCHEMA public
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES
TO bms_dev_staff;
```

빈 DB에서는 `ON ALL TABLES`의 대상이 아직 없다. `ALTER DEFAULT PRIVILEGES`가 이후
`bms_migrator`가 생성하는 테이블에 개발자 DML 권한을 자동으로 부여한다.

### 2.2 기존 객체를 개인 계정으로 만든 경우

이미 개발자 계정으로 Flyway를 실행했다면 기존 테이블과 `flyway_schema_history`의
소유권도 이관해야 한다. 해당 개발DB에 접속한 상태에서 관리자가 실행한다.

```sql
ALTER DATABASE bms_dev_staff OWNER TO bms_migrator;
REASSIGN OWNED BY bms_dev_staff TO bms_migrator;
```

소유권 이관 후 개발자 계정의 애플리케이션 권한을 다시 부여하고, 2.1의
`ALTER DEFAULT PRIVILEGES`도 적용한다.

```sql
GRANT CONNECT ON DATABASE bms_dev_staff TO bms_dev_staff;
GRANT USAGE ON SCHEMA public TO bms_dev_staff;
GRANT SELECT, INSERT, UPDATE, DELETE
ON ALL TABLES IN SCHEMA public
TO bms_dev_staff;
```

## 3. 개발자 접속 설정

백엔드는 개발자별 DB 접속 설정을 다음 외부 파일에서 읽는다.

```text
${user.home}/.bms/application-local.properties
```

Windows에서 제공된 예제 파일을 복사한다.

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.bms"
Copy-Item ".\config\application-local.properties.example" "$env:USERPROFILE\.bms\application-local.properties"
notepad "$env:USERPROFILE\.bms\application-local.properties"
```

개발자에게 할당된 데이터베이스 이름, 사용자명, 비밀번호와 `bms_migrator` 정보를
입력한다. 애플리케이션과 Flyway URL은 반드시 같은 개발DB를 가리켜야 한다.

```properties
spring.datasource.url=jdbc:postgresql://localhost:15432/bms_dev_staff
spring.datasource.username=bms_dev_staff
spring.datasource.password=DEVELOPER_PASSWORD

spring.flyway.url=jdbc:postgresql://localhost:15432/bms_dev_staff
spring.flyway.user=bms_migrator
spring.flyway.password=MIGRATOR_PASSWORD
```

DB URL에는 DB 노트북의 네트워크 주소가 아니라 SSH 터널의 로컬 주소를 사용한다.

## 4. SSH 터널

별도의 PowerShell 창에서 SSH 터널을 실행한다.

```powershell
ssh -N -L 15432:127.0.0.1:5432 bmsadmin@DB_LAPTOP_IP
```

비밀번호 입력 후 아무 메시지 없이 창이 계속 실행되는 것이 정상이다. 이 창을 닫으면
DB 연결도 종료된다. 다른 창에서 포트 상태를 확인할 수 있다.

```powershell
Test-NetConnection localhost -Port 15432
```

## 5. 최초 실행과 자동 마이그레이션

그다음 백엔드를 평소처럼 실행한다.

```powershell
.\mvnw.cmd spring-boot:run
```

애플리케이션 시작 시 다음 순서로 처리된다.

1. `bms_migrator`로 Flyway 이력과 체크섬을 검증한다.
2. 빈 DB이면 테이블, FK, 인덱스와 필수 초기 역할을 생성한다.
3. 적용된 버전을 `flyway_schema_history`에 기록한다.
4. 개발자 계정으로 애플리케이션 DataSource를 연결한다.
5. Hibernate `ddl-auto=validate`로 JPA Entity와 실제 스키마를 검증한다.

이미 적용된 마이그레이션은 다시 실행하지 않는다. 새로운 버전 파일만 순서대로
적용한다. `spring.datasource.url`과 `spring.flyway.url`이 서로 다른 DB를 가리키면
Flyway 적용 대상과 JPA 검증 대상이 달라지므로 애플리케이션 시작이 실패할 수 있다.

## 6. 적용 결과 확인

개발DB에 접속해 Flyway 적용 결과를 확인한다.

```sql
SELECT installed_rank,
       version,
       description,
       installed_by,
       installed_on,
       success
FROM flyway_schema_history
ORDER BY installed_rank;
```

현재 초기 스키마 기준으로 성공 마이그레이션 4건과 업무 테이블 47개가 생성된다.

```sql
SELECT COUNT(*) AS business_table_count
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name LIKE 'tb_%';
```

## 7. 비밀정보 관리

`application-local.properties`는 Git에서 제외된다. 실제 DB 접속정보를
`application.properties` 또는 예제 파일에 입력해서는 안 된다.
