# Local Database와 Flyway 사용 방법

## 목적

Local MySQL에서 Backend의 DB 연결, JPA Entity 및 Migration을 검증하는 방법을 정리한다.

V1의 Local Database 구성은 다음과 같다.

```text
MySQL       : 8.4 LTS
Host        : localhost
Port        : 3306
Database    : town_ai
Charset     : utf8mb4
Collation   : utf8mb4_unicode_ci
Migration   : Flyway
```

Local은 개발 및 구동 확인 목적이므로 초기에는 `root` 계정을 사용할 수 있다.
Production Cloud SQL에서는 Local 계정을 재사용하지 않고 별도 DB 계정과 Secret을 구성한다.

## Flyway의 역할

Flyway는 Database 구조 변경 SQL을 Version 순서대로 한 번씩 실행하고 그 결과를
`flyway_schema_history` 테이블에 기록한다.

```text
Spring Boot 시작
→ Flyway가 flyway_schema_history 확인
→ 아직 성공 이력이 없는 Migration 실행
→ Hibernate가 Entity와 실제 Table 구조 검증
→ Backend 시작
```

현재 Migration은 다음 파일들이다.

```text
backend/app/src/main/resources/db/migration/V1__initialize_schema.sql
backend/app/src/main/resources/db/migration/V2__make_line_draft_warnings_required.sql
backend/app/src/main/resources/db/migration/V3__support_line_follow_event.sql
backend/app/src/main/resources/db/migration/V4__add_line_report_idempotency_key.sql
backend/app/src/main/resources/db/migration/V5__support_area_registration_from_line_draft.sql
backend/app/src/main/resources/db/migration/V6__support_line_visit_draft_revision.sql
backend/app/src/main/resources/db/migration/V7__claim_line_visit_draft_revision.sql
backend/app/src/main/resources/db/migration/V8__add_report_source_fingerprint.sql
```

V1은 초기 스키마를 생성하고, V2는 기존 Draft의 `warnings`가 `NULL`이면 빈 JSON
배열로 정규화한 뒤 해당 Column을 `NOT NULL`로 변경한다.
V3는 LINE `FOLLOW` Event 제약을 추가하고, V4는 LINE Report 재처리용
`source_webhook_event_id` UNIQUE Key를 추가한다. V5는 LINE에서 첫 Visit 확인 시
신규 Area를 함께 등록할 수 있도록 Draft에 위치 Snapshot과 등록 필요 여부를 추가한다.
V6는 LINE Draft 부분 수정을 위한 `AWAITING_REVISION`, `SUPERSEDED` 상태와 최신
수정 대기 Draft 조회 Index를 추가한다.
V7은 수정 Text Message가 원본 Draft를 AI 호출 전에 점유하는
`REVISION_PROCESSING` 상태와 `revision_webhook_event_id`를 추가하고, 재시도에서도
수정 의도를 유지하도록 Event에 `revision_source_draft_id`를 보존한다.
V8은 변경되지 않은 Report를 재사용할 수 있도록 Prompt 입력 기반
`source_fingerprint`와 조회 Index를 추가한다.

## Local 화면 확인용 Seed 데이터

Frontend의 Dashboard, Area·Visit 목록과 Statistics 표시를 확인할 때는 Backend를
실행한 뒤 Local 전용 Seed 스크립트를 사용한다.

```powershell
cd C:\Users\kwanm\git\town-ai\backend
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\local-seed.ps1
```

기본 대상은 `http://localhost:8080`이다. 다른 Local Port를 사용한다면 다음처럼
Loopback URL을 전달한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\local-seed.ps1 `
  -BaseUrl http://localhost:18080
```

스크립트는 API를 통해 Area 3개와 Visit 5개를 생성한다. 기존 Area는
`prefecture`, `city`, `name`으로 찾고, 같은 Area·방문일·점수의 Visit은 건너뛴다.
따라서 반복 실행해도 같은 Fixture가 중복 생성되지 않는다. Production 오실행을
막기 위해 `localhost`, `127.0.0.1` 같은 Loopback 주소 이외에는 실행을 거부한다.
명령의 `ExecutionPolicy Bypass`는 해당 PowerShell Process에만 적용하며 시스템의
영구 실행 정책은 변경하지 않는다.

### Soft Delete한 Seed Area 복구

Area 관리 화면에서 세 Seed Area를 삭제하면 Area Row와 기존 Visit Row는 DB에
보존되지만 일반 Area·Visit 목록과 통계에서는 제외된다. V1에는 사용자용 Area 복구
API가 없으므로 Local Fixture에 한해서 다음 스크립트로 세 Area의 `deleted_at`만
`NULL`로 되돌린다.

```powershell
cd C:\Users\kwanm\git\town-ai\backend
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\local-restore-seed.ps1
```

스크립트는 Local MySQL 접속만 허용하고 `mysql.exe`가 비밀번호를 직접 묻는다.
비밀번호는 스크립트, Git 또는 Process 명령 인자에 저장되지 않는다. MySQL을 다른
경로에 설치했다면 `-MySqlPath`로 실제 Client 경로를 전달할 수 있다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\local-restore-seed.ps1 `
  -MySqlPath "D:\MySQL\bin\mysql.exe"
```

복구 후 `local-seed.ps1`을 다시 실행하면 기존 Visit은 중복 생성하지 않고 현재
Fixture 상태를 확인한다. 이 스크립트는 정확히 일치하는 세 Local Seed Area만
복구하며 Production Database에는 사용하지 않는다.

Report는 OpenAI API 비용이 발생하므로 Seed에 포함하지 않는다. Report 화면은
필요할 때 Local API 또는 이후 구현할 Frontend 생성 화면에서 별도로 생성해 확인한다.

최초 실행 후에는 다음과 같은 이력이 저장된다.

```text
version     : 1
description : initialize schema
script      : V1__initialize_schema.sql
success     : 1
```

애플리케이션을 다시 실행해도 V1은 다시 실행되지 않는다. Flyway는 파일 Version과
Checksum을 이력과 비교하고 새 Migration만 실행한다.

## 이번 초기 적용 방법

기존 `town_ai`는 SQL을 직접 실행해 만든 스키마였고 모든 업무 테이블이 0건이었다.
또한 `flyway_schema_history`가 없었으므로 다음 순서로 Flyway 기준 스키마로 전환했다.

```text
1. 여섯 업무 테이블의 실제 행 수가 모두 0인지 확인
2. V1__initialize_schema.sql 생성
3. 기존 town_ai Database 삭제
4. utf8mb4 / utf8mb4_unicode_ci로 town_ai Database 재생성
5. Spring Boot 시작
6. Flyway가 V1 실행
7. Hibernate ddl-auto=validate 통과 확인
8. Health와 목록 API 확인
```

데이터가 존재하는 Database라면 이 방식으로 초기화하면 안 된다. 데이터를 보존해야 하는
기존 스키마는 백업과 현재 구조 비교 후 Flyway Baseline 적용 여부를 별도로 결정해야 한다.

## Local Database 최초 생성

MySQL Client를 실행한다.

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" `
    --host=127.0.0.1 `
    --port=3306 `
    --user=root `
    --password
```

`--password` 뒤에 실제 비밀번호를 명령문으로 작성하지 않고 Prompt에서 입력한다.

Database가 없다면 다음 SQL로 빈 Database만 생성한다. 업무 Table은 직접 생성하지 않고
Backend를 시작해 Flyway가 생성하게 한다.

```sql
CREATE DATABASE `town_ai`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

## Backend 실행

현재 Local 검증에서는 별도 `town_ai` DB 사용자를 만들지 않고 `root`를 사용한다.
비밀번호는 Git에 저장하지 않고 현재 Terminal Process의 환경변수로만 전달한다.

```powershell
cd C:\Users\kwanm\git\town-ai\backend

$env:DB_HOST = "localhost"
$env:DB_PORT = "3306"
$env:DB_NAME = "town_ai"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "<로컬 MySQL 비밀번호>"

.\gradlew.bat :app:bootRun
```

실행을 종료한 뒤 현재 Terminal에서 민감한 환경변수를 제거할 수 있다.

```powershell
Remove-Item Env:DB_PASSWORD
```

IDE에서 실행할 때도 동일한 값을 Run Configuration 환경변수로 전달한다.
실제 비밀번호를 `application.yml`이나 Git 추적 파일에 작성하지 않는다.

## 적용 결과 확인

Flyway 이력:

```sql
SELECT
    installed_rank,
    version,
    description,
    script,
    success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Table 목록:

```sql
SHOW TABLES;
```

정상 V1 결과에는 다음 일곱 Table이 존재한다.

```text
area
visit
report
report_area
line_webhook_event
line_visit_draft
flyway_schema_history
```

Backend Health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/actuator/health/liveness
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
```

세 요청 모두 `UP`이면 Backend Process와 DB 연결 준비 상태가 정상이다.

## 다음 스키마 변경 방법

V1이 실행된 이후 Column이나 Table을 변경할 때는 `V1__initialize_schema.sql`을 수정하지 않는다.
다음 Version 파일을 추가한다.

예를 들어 Report에 새 Column을 추가한다면:

```text
backend/app/src/main/resources/db/migration/V2__add_report_status.sql
```

```sql
ALTER TABLE `report`
    ADD COLUMN `status` VARCHAR(20) NOT NULL;
```

권장 작업 순서:

```text
1. ERD와 변경 목적 검토
2. 새로운 V{번호}__{설명}.sql 작성
3. Entity와 Repository 수정
4. Local Database 백업
5. Backend 시작으로 Migration 실행
6. flyway_schema_history 성공 이력 확인
7. JPA 검증, Test 및 API 동작 확인
8. 기준 ERD SQL과 관련 설계 문서 갱신
```

Migration 규칙:

- 이미 실행된 Migration 파일의 내용과 Version을 변경하지 않는다.
- 기존 Version 번호를 재사용하지 않는다.
- 여러 변경 파일의 Version 순서를 바꾸지 않는다.
- 운영 데이터가 있는 Table의 삭제나 Type 변경은 백업 및 복구 방법을 먼저 정한다.
- Migration 실패 시 원인을 해결하기 전에 `repair`를 임의로 실행하지 않는다.
- Local과 Production은 동일한 Migration 파일을 사용한다.

## Database 초기화가 필요한 경우

다음 명령은 `town_ai`의 모든 데이터를 삭제한다. Local 데이터가 필요 없고 백업이 완료된
경우에만 사용한다.

```sql
DROP DATABASE `town_ai`;

CREATE DATABASE `town_ai`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

빈 Database를 만든 다음 Backend를 시작하면 Flyway가 V1부터 순서대로 다시 실행한다.

Production Cloud SQL에서는 이 초기화 방법을 사용하지 않는다.

## 현재 Local 검증 결과

2026-07-24 기준 결과:

```text
MySQL Server                    : 8.4.10
Flyway V1                       : 성공
Hibernate ddl-auto=validate     : 성공
Spring Boot 기동                : 성공
GET /actuator/health            : UP
GET /actuator/health/liveness   : UP
GET /actuator/health/readiness  : UP
GET /api/areas                  : 200 OK, 빈 목록
GET /api/reports                : 200 OK, 빈 목록
```

## 참고 사항

현재 PC에는 `DEBUG` 환경변수가 존재해 Spring Boot 실행 시 상세 Debug 로그가 출력될 수 있다.
일반 로그로 확인하려면 Backend를 실행한 Terminal에서만 다음과 같이 제거할 수 있다.

```powershell
Remove-Item Env:DEBUG
```

다른 개발 도구가 해당 환경변수를 사용하는지 먼저 확인하고 시스템 환경변수 자체를 임의로
삭제하지 않는다.
