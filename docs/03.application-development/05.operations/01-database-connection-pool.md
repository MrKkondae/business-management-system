# 데이터베이스 커넥션 풀 운영 기준

## 1. 적용 범위

BMS 백엔드는 Spring Boot가 관리하는 단일 HikariCP `DataSource`를 사용한다. 이 문서는
단일 백엔드 인스턴스로 시작하는 v1.0 운영 기준과 환경변수 조정 절차를 정의한다.

## 2. 초기 기준값

| 환경변수 | 기본값 | 의미 |
| --- | ---: | --- |
| `BMS_DB_POOL_MAX_SIZE` | `10` | 한 인스턴스가 동시에 보유할 수 있는 최대 DB 연결 수 |
| `BMS_DB_POOL_MIN_IDLE` | `2` | 평상시 유지할 최소 유휴 연결 수 |
| `BMS_DB_CONNECTION_TIMEOUT_MS` | `5000` | 풀에서 연결을 얻기까지 기다리는 최대 시간 |
| `BMS_DB_VALIDATION_TIMEOUT_MS` | `2000` | 연결 유효성 검사 제한시간 |
| `BMS_DB_IDLE_TIMEOUT_MS` | `600000` | 최소 유휴 수를 초과한 연결의 유휴 정리시간 |
| `BMS_DB_MAX_LIFETIME_MS` | `1800000` | 풀 연결의 최대 수명 |
| `BMS_DB_KEEPALIVE_TIME_MS` | `0` | keepalive 비활성화 |
| `BMS_DB_LEAK_DETECTION_THRESHOLD_MS` | `0` | 누수 탐지 비활성화 |

기본값은 약 50명 이내의 사용자가 이용하는 단일 백엔드 인스턴스의 시작점이다.
성능 보증값이 아니며 운영 부하 시험과 관측 결과로 조정한다.

## 3. PostgreSQL 연결 예산

운영 반영 전에 PostgreSQL에서 다음 값을 확인한다.

```sql
SHOW max_connections;
SHOW superuser_reserved_connections;

SELECT application_name, usename, state, COUNT(*) AS connection_count
FROM pg_stat_activity
GROUP BY application_name, usename, state
ORDER BY connection_count DESC;
```

연결 예산은 다음 조건을 만족해야 한다.

```text
(백엔드 인스턴스 수 × BMS_DB_POOL_MAX_SIZE)
+ Flyway 연결
+ 관리자·모니터링·백업 연결 여유분
<= max_connections - superuser_reserved_connections
```

Flyway가 별도 계정을 사용하므로 배포 시점의 마이그레이션 연결도 예산에 포함한다.
백엔드를 증설할 때는 전체 연결 수가 인스턴스 수에 비례해 증가하므로 풀 최대값을 다시
계산하지 않고 인스턴스만 늘려서는 안 된다.

## 4. 타임아웃 조정 원칙

- 연결 대기시간이 길어지는 상황을 요청 스레드 적체로 숨기지 않도록
  `BMS_DB_CONNECTION_TIMEOUT_MS`는 초기 5초를 유지한다.
- 검증 제한시간은 연결 대기시간보다 작게 유지한다.
- 최대 수명은 DB, 방화벽 또는 프록시가 연결을 강제로 종료하는 시간보다 짧게 설정한다.
- keepalive는 네트워크 장비의 유휴 연결 종료가 확인된 경우에만 활성화하고 최대 수명보다
  짧게 설정한다.
- 누수 탐지는 진단 비용과 장기 트랜잭션 오탐이 있으므로 상시 활성화하지 않는다. 진단할
  때만 정상 트랜잭션 시간보다 충분히 큰 임계값을 임시 적용한다.

## 5. 운영 환경변수 예시

```text
BMS_DB_POOL_MAX_SIZE=10
BMS_DB_POOL_MIN_IDLE=2
BMS_DB_CONNECTION_TIMEOUT_MS=5000
BMS_DB_VALIDATION_TIMEOUT_MS=2000
BMS_DB_IDLE_TIMEOUT_MS=600000
BMS_DB_MAX_LIFETIME_MS=1800000
BMS_DB_KEEPALIVE_TIME_MS=0
BMS_DB_LEAK_DETECTION_THRESHOLD_MS=0
```

비밀값은 아니지만 배포 환경별 값이므로 운영 배포 설정에서 관리한다. 값을 변경할 때는
변경 전후의 활성·유휴·대기 연결 수, 연결 획득 시간, 요청 지연시간과 DB CPU를 함께
비교하고 변경 사유와 결과를 배포 기록에 남긴다.

## 6. 검증 절차

1. 위 SQL로 PostgreSQL 연결 한도와 현재 사용량을 확인한다.
2. 연결 예산식을 적용해 인스턴스별 최대 풀 크기를 결정한다.
3. 예상 동시 사용량으로 부하 시험을 실행한다.
4. 풀 대기와 연결 획득 타임아웃, DB CPU 및 느린 SQL을 확인한다.
5. 풀 부족보다 느린 SQL이나 긴 트랜잭션이 원인이면 풀을 늘리기 전에 해당 원인을
   해결한다.
6. 환경변수 변경 후 애플리케이션을 재시작하고 동일 조건으로 다시 측정한다.
