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

이 프로젝트는 사용자 전역 Maven 설정과 분리된 `.mvn/bms-settings.xml`을 사용한다.
따라서 다른 프로젝트를 위해 `${user.home}/.m2/settings.xml`에 로컬 Nexus가 설정되어 있어도
BMS 의존성과 플러그인은 Maven Central에서 조회한다. Maven 명령은 `backend` 디렉터리에서 실행한다.

그다음 백엔드를 평소처럼 실행한다.

```powershell
.\mvnw.cmd spring-boot:run
```

애플리케이션 시작 시 다음 순서로 처리된다.

1. `bms_migrator`로 Flyway 이력과 체크섬을 검증한다.
2. 빈 DB이면 테이블, FK, 인덱스와 필수 초기 역할을 생성한다.
3. 적용된 버전을 `flyway_schema_history`에 기록한다.
4. 개발자 계정으로 애플리케이션 DataSource를 연결한다.
5. MyBatis `SqlSessionFactory`를 구성하고 `classpath*:mybatis/mapper/**/*.xml`의 Mapper XML을 읽는다.

이미 적용된 마이그레이션은 다시 실행하지 않는다. 새로운 버전 파일만 순서대로
적용한다. `spring.datasource.url`과 `spring.flyway.url`이 서로 다른 DB를 가리키면
애플리케이션과 마이그레이션 대상이 달라지므로 동일한 데이터베이스를 가리키도록 설정한다.
스키마 호환성은 빈 PostgreSQL에 Flyway 전체 마이그레이션을 적용하는 Testcontainers
통합 테스트와 데이터 카탈로그 검증으로 확인한다.

### 5.1 MyBatis Mapper 검증

일반 테스트에서는 H2를 사용해 Mapper XML 로딩, 파라미터 바인딩과 Adapter 흐름을
빠르게 검증한다. PostgreSQL 전용 구문, 타입, 잠금과 제약조건의 최종 검증은
Testcontainers PostgreSQL로 수행한다.

Docker Desktop 또는 호환 컨테이너 런타임을 실행한 뒤 아래 테스트를 실행한다. 테스트는
PostgreSQL 16 컨테이너를 생성하고 Flyway 전체 마이그레이션을 적용한 뒤 트랜잭션을
롤백한다.

```powershell
.\mvnw.cmd -Dtest=BootstrapPersistencePostgresIntegrationTests test
.\mvnw.cmd -Dtest=AuthenticationMapperPostgresIntegrationTests test
```

컨테이너 런타임을 사용할 수 없는 로컬 환경에서는 PostgreSQL 테스트가 제외되지만,
CI 품질 게이트에서는 Docker를 제공하여 반드시 실행한다. H2 테스트 성공만으로
PostgreSQL Mapper 검증을 대체하지 않는다.

## 6. 최초 시스템관리자 부트스트랩

빈 DB의 마이그레이션과 인증 선행 데이터 반영이 끝난 뒤 한 번만 실행한다.
부트스트랩 모드는 일반 HTTP 포트를 열지 않으며, 조직·직원·사용자·역할 관계와
감사로그를 하나의 트랜잭션으로 생성한다.

임시 비밀번호를 포함한 입력값은 명령행 인자로 전달하지 않고 현재 PowerShell
프로세스의 환경변수로만 설정한다.

```powershell
$env:BMS_BOOTSTRAP_ADMIN_ENABLED = "true"
$env:BMS_BOOTSTRAP_ORGANIZATION_NAME = "회사명"
$env:BMS_BOOTSTRAP_EMPLOYEE_NUMBER = "EMP-0001"
$env:BMS_BOOTSTRAP_ADMIN_NAME = "관리자"
$env:BMS_BOOTSTRAP_LOGIN_ID = "admin"
$env:BMS_BOOTSTRAP_TEMPORARY_PASSWORD = "<배포 비밀 저장소에서 주입>"
$env:BMS_BOOTSTRAP_EMAIL = "admin@example.com" # 선택
$env:BMS_BOOTSTRAP_MOBILE = "010-0000-0000"    # 선택

.\mvnw.cmd spring-boot:run
```

부트스트랩 실행부터 최초 로그인까지의 정확한 순서는 다음과 같다.

1. 백엔드 디렉터리의 PowerShell에서 위 환경변수를 설정한다.
2. `.\mvnw.cmd spring-boot:run`을 실행한다.
3. `Initial system administrator bootstrap completed successfully` 로그를 확인한다.
   부트스트랩 모드는 관리자 생성 후 애플리케이션 컨텍스트를 닫기 때문에
   `BUILD SUCCESS`와 함께 프로세스가 종료되는 것이 정상이다. 이 실행에서는 HTTP
   서버가 계속 실행되지 않는다.
4. 아래 명령으로 같은 PowerShell 세션의 `BMS_BOOTSTRAP_*` 환경변수를 제거한다.
   이때 삭제하는 것은 PowerShell 환경변수이며, DB에 생성된 조직·직원·관리자 계정과
   역할 관계를 삭제해서는 안 된다.
5. `.\mvnw.cmd spring-boot:run`을 다시 실행한다. 이번에는 부트스트랩이 비활성화된
   일반 웹 애플리케이션으로 기동되며 프로세스가 계속 실행되어야 한다.
6. 프런트엔드 서버를 별도로 기동하고 `http://localhost:3000/login`에 접속한 뒤,
   `BMS_BOOTSTRAP_LOGIN_ID`로 지정했던 로그인ID와 초기 임시 비밀번호로 로그인한다.
7. 제한 세션으로 이동한 최초 등록 화면에서 필수 정보 등록과 초기 비밀번호 변경을
   완료한다.

임시 비밀번호는 12~64자이고 영문 대문자·소문자·숫자·특수문자 중 3종 이상을
포함해야 하며 로그인ID나 관리자 성명을 포함할 수 없다. 성공하면 생성된 사용자ID와
로그인ID만 기록하며 임시 비밀번호는 출력하지 않는다. 사용자 테이블에 한 건이라도
있거나 시스템관리자 역할·메뉴권한·필수 코드가 준비되지 않았으면 전체 작업을
롤백하고 오류코드와 함께 종료한다.

실행 후에는 같은 PowerShell 세션에 비밀값이 남지 않도록 변수를 제거한다.

```powershell
Remove-Item Env:BMS_BOOTSTRAP_ADMIN_ENABLED
Remove-Item Env:BMS_BOOTSTRAP_ORGANIZATION_NAME
Remove-Item Env:BMS_BOOTSTRAP_EMPLOYEE_NUMBER
Remove-Item Env:BMS_BOOTSTRAP_ADMIN_NAME
Remove-Item Env:BMS_BOOTSTRAP_LOGIN_ID
Remove-Item Env:BMS_BOOTSTRAP_TEMPORARY_PASSWORD
Remove-Item Env:BMS_BOOTSTRAP_EMAIL -ErrorAction SilentlyContinue
Remove-Item Env:BMS_BOOTSTRAP_MOBILE -ErrorAction SilentlyContinue
```

부트스트랩 계정은 `PWD_INIT_REQ_YN='Y'`, `SEC_VER=1`, 임시 비밀번호 만료
24시간으로 생성된다. 로그인 구현 후 최초 접속에서는 제한 세션만 발급하고
`/account/initial-registration`에서 등록을 완료해야 한다.

## 7. 적용 결과 확인

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

현재 스키마 기준으로 성공 마이그레이션 6건과 업무 테이블 47개가 생성된다.

```sql
SELECT COUNT(*) AS business_table_count
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name LIKE 'tb_%';
```

## 8. 비밀정보 관리

`application-local.properties`는 Git에서 제외된다. 실제 DB 접속정보를
`application.properties` 또는 예제 파일에 입력해서는 안 된다.
