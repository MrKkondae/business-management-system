# 백엔드 로컬 개발 환경

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

개발자에게 할당된 데이터베이스 이름, 사용자명, 비밀번호를 입력한다. DB URL에는
DB 노트북의 네트워크 주소가 아니라 SSH 터널의 로컬 주소를 사용한다.

별도의 PowerShell 창에서 SSH 터널을 실행한다.

```powershell
ssh -N -L 15432:127.0.0.1:5432 bmsadmin@DB_LAPTOP_IP
```

그다음 백엔드를 평소처럼 실행한다.

```powershell
.\mvnw.cmd spring-boot:run
```

`application-local.properties`는 Git에서 제외된다. 실제 DB 접속정보를
`application.properties` 또는 예제 파일에 입력해서는 안 된다.
