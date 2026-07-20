# 배포 설계

## 목차

- [목적](#목적)
- [환경 구성](#환경-구성)
- [Production 구성](#production-구성)
- [애플리케이션 설정](#애플리케이션-설정)
- [Secret 관리](#secret-관리)
- [LINE 비동기 처리](#line-비동기-처리)
- [Report Storage](#report-storage)
- [Docker](#docker)
- [CI/CD](#cicd)
- [DB Migration](#db-migration)
- [Image Version](#image-version)
- [Health Check](#health-check)
- [Logging 및 Monitoring](#logging-및-monitoring)
- [비용 관리](#비용-관리)
- [Production 배포 직전 확정 사항](#production-배포-직전-확정-사항)
- [참고 문서](#참고-문서)

## 목적

Town-AI V1을 GCP 기반으로 안전하게 배포하고 개인 사용량에서 운영비를 최소화한다.

주요 원칙은 다음과 같다.

- Local과 Production 두 환경만 운영한다.
- Production 배포 전까지 MySQL은 Local에서 사용한다.
- Backend만 Docker Image로 배포한다.
- Frontend는 정적 파일로 빌드해 Firebase Hosting에 배포한다.
- Secret은 Git과 Docker Image에 포함하지 않는다.
- Production 서비스는 가능한 한 같은 GCP Region에 배치한다.
- CI와 CD는 GitHub Actions Workflow 두 개로 관리한다.

## 환경 구성

### Local

```text
React Development Server
        │
        ▼
Local Spring Boot
   ├── Local MySQL 8.4
   ├── LocalReportStorage
   ├── LocalLineEventDispatcher
   └── OpenAI API
```

| 구성 | Local |
|----|----|
| Frontend | React Development Server |
| Backend | 로컬 Spring Boot |
| Database | Docker 또는 로컬 MySQL 8.4 |
| Report Storage | 로컬 파일시스템 |
| Secret | `.env` 또는 IDE 환경변수 |
| GCP | 사용하지 않거나 필요 기능만 개발자 인증으로 접근 |

### Production

```text
사용자
  │
  ├── Firebase Hosting ── React
  │
  └── Cloud Run ───────── Spring Boot Container
         ├── Cloud SQL for MySQL
         ├── Cloud Storage
         ├── Cloud Tasks
         ├── Secret Manager
         └── OpenAI API
```

| 구성 | Production |
|----|----|
| Frontend | Firebase Hosting |
| Backend | Cloud Run |
| Database | Cloud SQL for MySQL 8.4 |
| Report Storage | Cloud Storage |
| Secret | Secret Manager |
| Container Registry | Artifact Registry |
| CI/CD | GitHub Actions |

Cloud SQL은 Backend 구현과 Local 검증이 끝난 후 실제 Production 운영 직전에 MySQL 8.4, Enterprise Edition으로 생성한다. Instance 사양은 비용 계산 후 결정한다.

## Production 구성

### Backend

- Spring Boot를 Docker Image로 빌드한다.
- Image를 Artifact Registry에 Push한다.
- Cloud Run은 Artifact Registry의 Image를 사용한다.
- 개인 사용량을 고려해 Cloud Run 최소 Instance는 `0`으로 설정한다.
- V1의 최대 Instance는 `1`을 기본값으로 사용한다.
- Health Check가 성공한 Revision에만 Production Traffic을 연결한다.

### Frontend

- React는 Docker Image로 만들지 않는다.
- `npm ci`, Test, Production Build 후 Firebase Hosting에 배포한다.
- 별도의 Firebase Project를 만들지 않고 Town-AI Production GCP Project에 Firebase를 활성화한다.
- Backend API URL은 Frontend Build 환경변수로 전달한다.
- Firebase Hosting의 CDN과 HTTPS를 사용한다.

### Region

- Production Region은 `asia-northeast1`(Tokyo)로 통일한다.
- Cloud Run, Cloud SQL, Cloud Storage 및 Artifact Registry는 같은 Region을 사용한다.
- Frontend는 Firebase Hosting의 CDN을 사용한다.
- 주 사용자의 위치와 비용을 함께 고려해 Tokyo를 선택한다.

미국 무료 Storage Region을 사용하면 Storage 비용은 줄일 수 있지만, Backend 또는 DB와 Region이 달라질 경우 네트워크 지연과 데이터 전송 비용 및 운영 복잡도가 발생할 수 있다. Markdown Report의 용량이 작으므로 Storage 무료 혜택만을 위해 Production 구성요소를 여러 Region으로 분리하지 않는다.

## 애플리케이션 설정

V1에서는 환경별 YAML 파일을 여러 개 만들지 않고 `application.yml` 하나를 사용한다.

Runtime 및 Build 기준 Version:

```text
Java        : 25 LTS
Spring Boot : 4.1.x (초기 고정 Version 4.1.0)
Gradle      : 9.6.1
MySQL       : 8.4 LTS
```

- Spring Boot Patch Version은 `4.1.x` 범위에서 Test 후 올린다.
- Local과 Production은 모두 MySQL 8.4를 사용하고 Minor Version은 각 환경의 최신 보안 Patch를 따른다.
- MySQL 8.4의 기본 인증 방식인 `caching_sha2_password`를 사용한다.
- Cloud SQL MySQL 8.4 생성 시 Edition을 생략하면 Enterprise Plus가 선택될 수 있으므로 V1은 비용을 고려해 Enterprise Edition을 명시한다.

```text
application.yml
```

환경별 차이는 환경변수의 기본값으로 처리한다.

```yaml
spring:
  datasource:
    url: "jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:town_ai}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
    username: ${DB_USERNAME:town_ai}
    password: ${DB_PASSWORD}
  jpa:
    properties:
      hibernate.jdbc.time_zone: UTC

town-ai:
  report-storage:
    type: ${REPORT_STORAGE_TYPE:local}
    local-directory: ${REPORT_LOCAL_DIRECTORY:./data/reports}
    bucket-name: ${GCS_BUCKET_NAME:}
  line:
    event-dispatcher: ${LINE_EVENT_DISPATCHER:local}
    cloud-tasks-queue: ${LINE_CLOUD_TASKS_QUEUE:line-events}
    cloud-tasks-target-url: ${LINE_CLOUD_TASKS_TARGET_URL:}
    cloud-tasks-oidc-audience: ${LINE_CLOUD_TASKS_OIDC_AUDIENCE:}
    cloud-tasks-service-account: ${LINE_CLOUD_TASKS_SERVICE_ACCOUNT:}
```

### 일반 설정

다음 값은 Secret이 아니며 `application.yml`의 기본값 또는 실행 환경의 일반 환경변수로 관리한다.

```text
DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
REPORT_STORAGE_TYPE
REPORT_LOCAL_DIRECTORY
GCP_PROJECT_ID
GCP_REGION
GCS_BUCKET_NAME
LINE_EVENT_DISPATCHER
LINE_CLOUD_TASKS_QUEUE
LINE_CLOUD_TASKS_TARGET_URL
LINE_CLOUD_TASKS_OIDC_AUDIENCE
LINE_CLOUD_TASKS_SERVICE_ACCOUNT
```

Production 연결 방식이 확정되면 Cloud SQL 접속 관련 설정을 추가한다.

## Secret 관리

### Local

Local에서는 `.env` 또는 IDE 환경변수를 사용한다.

```text
OPENAI_API_KEY
DB_PASSWORD
LINE_CHANNEL_SECRET
LINE_CHANNEL_ACCESS_TOKEN
LINE_ALLOWED_USER_ID
```

`.env`는 Git에 Commit하지 않는다. 저장소에는 실제 값이 없는 `.env.example`만 둘 수 있다.

### Production

Production에서는 Secret Manager를 사용한다.

```text
OPENAI_API_KEY
DB_PASSWORD
LINE_CHANNEL_SECRET
LINE_CHANNEL_ACCESS_TOKEN
LINE_ALLOWED_USER_ID
```

- Cloud Run에 전용 Service Account를 연결한다.
- Service Account에 필요한 최소 IAM 권한만 부여한다.
- Service Account JSON Key를 Docker Image, GitHub Secret 또는 환경변수로 전달하지 않는다.
- Cloud Run은 연결된 Service Account의 Application Default Credentials로 GCP 서비스에 접근한다.
- 사용하지 않는 과거 Secret Version은 파기해 활성 Version 수가 불필요하게 증가하지 않게 한다.

## LINE 비동기 처리

Production에서는 LINE Webhook 이벤트를 Cloud Tasks의 HTTP Target Task로 처리한다.

```text
LINE Webhook
→ line_webhook_event Commit
→ asia-northeast1의 line-events Queue에 Task 생성
→ Cloud Run 내부 처리 Endpoint 호출
→ Visit Draft 생성 또는 확인·취소 처리
→ LINE Push Message 전송
```

### Cloud Tasks

- Queue 이름은 `line-events`, Region은 `asia-northeast1`을 사용한다.
- 이벤트마다 `webhookEventId` 기반의 결정적 Task 이름을 사용해 중복 Task 생성을 방지한다.
- 전달 방식은 At-least-once로 간주하고 최종 멱등성은 MySQL의 이벤트 PK와 Draft 상태 전환으로 보장한다.
- 처리 시작 시 3분 Lease를 기록하고, Process 종료로 `PROCESSING`에 남은 이벤트는 Lease 만료 후 다음 시도가 다시 점유한다.
- Cloud Tasks는 일시 오류에 지수 Backoff를 적용한다. Queue의 `maxAttempts`는 최초 전달을 포함한 전달 시도 횟수이며 초기값은 `5`로 설정한다.
- 양수인 `maxRetryDuration`을 함께 설정하면 Cloud Tasks는 `maxAttempts`와 `maxRetryDuration` 조건을 모두 충족할 때 재시도를 중단하므로 실제 Endpoint 전달 시도는 5회를 초과할 수 있다.
- 애플리케이션의 `attemptCount`는 Endpoint에 도달해 이벤트를 `PROCESSING`으로 점유한 경우에만 증가하며 Cloud Tasks의 전달 시도 횟수와 별도로 관리한다.
- 재시도 기간은 LINE Push Message Retry Key의 유효 기간을 넘지 않도록 `24시간 미만`으로 제한한다.
- OpenAI 응답 시간을 고려해 Task Dispatch Deadline은 `120초`로 시작하고 실제 측정 후 조정한다.
- Task Payload에는 `webhookEventId`만 포함하고 LINE 메시지 원문이나 Token을 넣지 않는다.
- Cloud Tasks 전용 Service Account가 OIDC Token을 발급해 내부 처리 Endpoint를 호출한다.
- Cloud Run 서비스는 LINE Webhook 수신을 위해 공개되어 있으므로, 내부 Endpoint는 애플리케이션에서 OIDC Token의 서명, Issuer, Audience 및 Service Account를 모두 검증한다.
- `X-CloudTasks-*` Header는 재시도 횟수와 관측 정보로만 사용하고 인증 수단으로 사용하지 않는다.
- 다섯 번째 애플리케이션 처리 시도에서도 실패하면 이벤트를 `FAILED`로 기록하고 `2xx`로 종료한다.
- Cloud Tasks가 Endpoint에 도달하지 못한 채 최종 실패한 Task와 DB에 남은 `RECEIVED` 또는 Lease 만료 `PROCESSING` 이벤트를 Logging 및 운영 점검 대상에 포함한다.
- LINE Push Message는 최초 전송부터 결정적 `X-Line-Retry-Key`를 사용하며, `409 Conflict`는 이전 요청이 수락된 성공 상태로 처리한다.
- Draft 또는 Visit 처리 결과를 DB에 저장하고 LINE Push 요청이 `2xx` 또는 이미 수락된 Request ID가 포함된 `409 Conflict`로 확인된 후에만 이벤트를 `COMPLETED`로 전환한다.

### Local

- `LocalLineEventDispatcher`는 같은 Application Process에서 작업을 실행해 개발 흐름을 확인한다.
- Local Dispatcher는 내구성 Queue가 아니므로 Production에서 사용할 수 없다.
- LINE의 실제 Webhook을 Local에서 시험할 때는 HTTPS Tunnel을 사용할 수 있지만 Tunnel 제품은 Backend 구현 단계에서 선택한다.

### LINE Console

- `Use webhook`과 `Webhook redelivery`를 활성화한다.
- Webhook URL은 `https://{backend-host}/api/line/webhook`을 사용한다.
- 비동기 처리 결과는 Reply Token 대신 Push Message로 전송한다.
- 허용된 개인 계정의 LINE User ID만 `LINE_ALLOWED_USER_ID`에 등록한다.

## Report Storage

```text
ReportStorage
├── LocalReportStorage
└── GcsReportStorage
```

### LocalReportStorage

Local 개발에서 Cloud Storage 없이 Report 생성·조회·다운로드·삭제 흐름을 확인하기 위한 구현이다.

- 단순한 Unit Test 전용 Mock이 아니다.
- 로컬 개발 환경에서 실제 Markdown 파일을 저장하는 Storage Adapter이다.
- 기본 저장 경로 후보는 `./data/reports`이다.
- 자동화 Test에서는 별도의 임시 디렉터리 또는 Fake 구현을 사용할 수 있다.

### GcsReportStorage

Production에서 Google Cloud Storage에 Markdown Report를 저장한다.

- DB에는 Storage 내부 경로만 저장한다.
- Bucket 이름은 전역 고유해야 하므로 `town-ai-reports-{uniqueSuffix}` 형식을 사용하고 실제 이름은 `GCS_BUCKET_NAME`으로 전달한다.
- Bucket의 기본 경로 Prefix는 `reports`를 사용한다.
- 객체 경로의 `v1`은 Prompt Version이 아니라 Report 저장 구조 Version이다.
- Bucket은 Public으로 공개하지 않는다.
- Backend Service Account만 객체를 읽고 쓰고 삭제할 수 있다.
- 사용자 다운로드는 Backend API를 통해 제공한다.

객체 경로 형식:

```text
reports/v1/{reportType-lowercase}/{filename}_{yyyy-MM-dd}_{reportId}.md
```

Report Type별 파일명:

```text
AREA     : reports/v1/area/{areaName}_{yyyy-MM-dd}_{reportId}.md
COMPARE  : reports/v1/compare/{areaName1}-{areaName2}-{...}_{yyyy-MM-dd}_{reportId}.md
SUMMARY  : reports/v1/summary/{yyyy-MM-dd}_{reportId}.md
ALL      : reports/v1/all/{yyyy-MM-dd}_{reportId}.md
```

예:

```text
reports/v1/area/센터미나미_2026-07-20_10.md
reports/v1/compare/센터미나미-타마플라자_2026-07-20_11.md
reports/v1/summary/2026-07-20_12.md
reports/v1/all/2026-07-20_13.md
```

- Report Type은 Directory에서 식별하므로 파일명에 영문 타입을 반복하지 않는다.
- 같은 날짜와 대상을 사용해 다시 생성해도 충돌하지 않도록 `reportId`를 확장자 바로 앞에 둔다.
- Area 이름은 Report 생성 당시 값을 사용한다.
- `/`, `\` 및 제어 문자처럼 객체 경로에 부적합한 문자는 `-`로 치환한다.
- LocalReportStorage도 같은 상대 경로 규칙을 사용한다.

## Docker

### Backend

```text
Gradle Test
→ Spring Boot JAR Build
→ Docker Image Build
→ Artifact Registry Push
→ Cloud Run Deploy
```

Docker Image는 다음 원칙을 사용한다.

- 실행에 필요한 JRE와 JAR만 최종 Image에 포함한다.
- Source, `.env`, Test 결과 및 Gradle Cache를 최종 Image에 포함하지 않는다.
- Container는 환경변수로 설정을 전달받는다.
- 가능하면 Root가 아닌 사용자로 실행한다.
- Health Check Endpoint를 제공한다.

### Frontend

Frontend는 Docker를 사용하지 않는다.

```text
npm ci
→ Test
→ npm run build
→ Firebase Hosting Deploy
```

## CI/CD

```text
.github/workflows/
├── ci.yml
└── deploy.yml
```

### CI

Pull Request와 `main` Branch Push에서 실행한다.

```text
Backend Test
→ Backend Build
→ Prompt JSON Schema 검증
→ Frontend Test
→ Frontend Production Build 검증
```

하나의 단계가 실패하면 CI 전체를 실패 처리한다.

### CD

V1에서는 수동 실행으로 시작한다.

```yaml
on:
  workflow_dispatch:
```

배포 흐름:

```text
CI 검증
→ Backend JAR Build
→ Docker Image Build
→ Artifact Registry Push
→ Cloud Run Deploy
→ Health Check
→ Frontend Production Build
→ Firebase Hosting Deploy
```

- 배포에는 GitHub Actions의 Production Environment를 사용한다.
- GCP 인증에는 장기 Service Account JSON Key보다 Workload Identity Federation 사용을 우선한다.
- 배포 중 실패하면 기존 Cloud Run Revision과 기존 Frontend 배포를 유지한다.
- 운영이 안정화된 이후 `main` Push 자동 배포 전환을 검토한다.

## DB Migration

Flyway를 사용한다.

```text
Cloud Run Instance 시작
→ Flyway가 flyway_schema_history 조회
→ 아직 실행되지 않은 Migration만 실행
→ Spring Boot 시작 완료
```

- 배포마다 Migration 이력을 확인하지만 새로운 Migration 파일이 없으면 DB를 변경하지 않는다.
- V1에서는 애플리케이션 시작 시 Flyway를 실행한다.
- Cloud Run 최대 Instance를 `1`로 제한해 동시 Migration 가능성을 최소화한다.
- Migration 실패 시 애플리케이션 시작과 새 Revision 배포를 실패 처리한다.
- 다중 Instance가 필요해지면 Migration을 별도의 Cloud Run Job 또는 배포 단계로 분리한다.
- 이미 실행된 Migration 파일은 수정하지 않고 새로운 Version의 Migration 파일을 추가한다.

## Image Version

Docker Image Tag 형식:

```text
v{major}.{minor}.{patch}-{yyyyMMdd}-{shortCommitSha}
```

예:

```text
v0.1.0-20260720-a1b2c3d
```

Docker Label:

```text
org.opencontainers.image.version=v0.1.0
org.opencontainers.image.revision={full-commit-sha}
org.opencontainers.image.created={build-time}
```

- Commit SHA를 배포 Image의 최종 식별 기준으로 사용한다.
- 날짜와 애플리케이션 Version은 사람이 식별하기 위한 정보이다.
- Production Image는 최근 5개를 유지한다.
- 오래된 untagged Image는 삭제한다.
- Rollback은 이전 Image가 배포된 Cloud Run Revision으로 Traffic을 되돌리는 방식으로 수행한다.

## Health Check

Spring Boot Actuator의 Liveness와 Readiness Endpoint를 사용한다.

```text
Liveness  : /actuator/health/liveness
Readiness : /actuator/health/readiness
```

### Liveness

- Spring Boot 프로세스가 정상적으로 동작하고 있는지 확인한다.
- Cloud Run Liveness Probe가 사용한다.
- 반복해서 실패하면 Cloud Run이 해당 Container Instance를 재시작한다.
- DB, OpenAI API 및 Cloud Storage처럼 외부 의존 서비스의 상태를 포함하지 않는다.
- 외부 서비스의 일시 장애 때문에 정상 Backend가 반복 재시작되는 것을 방지한다.

### Readiness

- Backend가 요청을 받을 준비가 되었는지 확인한다.
- Spring Boot 시작과 Flyway Migration이 끝나고 DB를 사용할 수 있어야 `UP`을 반환한다.
- OpenAI API와 Cloud Storage 상태는 포함하지 않는다.
- Cloud Run Startup Probe와 배포 후 검증에 사용한다.

Cloud Run Probe 정책:

```text
Startup Probe
  path             : /actuator/health/readiness
  periodSeconds    : 5
  timeoutSeconds   : 2
  failureThreshold : 24

Liveness Probe
  path             : /actuator/health/liveness
  periodSeconds    : 30
  timeoutSeconds   : 2
  failureThreshold : 3
```

Startup Probe는 최대 120초 동안 Backend 시작을 기다린다. V1에서는 Preview 기능인 Cloud Run Readiness Probe를 별도로 사용하지 않고, Startup Probe와 배포 후 Readiness 확인으로 처리한다.

Spring Boot 설정:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState,db
```

- Health Endpoint는 인증 없이 호출할 수 있도록 허용한다.
- `health` 이외의 Actuator Endpoint는 외부에 노출하지 않는다.
- 응답에는 Component 상세 정보, DB 주소 및 오류 내용을 노출하지 않는다.

## Logging 및 Monitoring

V1에서 다음 항목을 기록한다.

- 애플리케이션 시작과 종료
- API `5xx` 오류
- OpenAI API 실패
- Report Storage 실패
- Report 생성 성공·실패 및 소요 시간
- LINE Webhook 수신·Task 전달·Draft 처리 상태와 소요 시간
- Cloud Tasks 재시도 횟수 및 최종 실패
- Flyway Migration 시작·성공·실패
- Health Check 실패

구조화된 로그 예:

```json
{
  "event": "REPORT_GENERATION_FAILED",
  "reportType": "COMPARE",
  "promptVersion": "compare-v1",
  "errorCode": "OPENAI_API_ERROR",
  "durationMs": 12340
}
```

다음 값은 로그에 기록하지 않는다.

```text
API Key
Password
LINE Token
LINE User ID
LINE 메시지 원문 및 Postback Data
Service Account Credential
전체 Prompt 입력과 전체 AI 응답
사용자의 전체 memo
```

## 비용 관리

### 기본 정책

- 초기 월 Budget은 `¥1,000`으로 설정한다.
- 실제 사용액 `50%`, `80%`, `100%`와 예상 월말 사용액 `80%`에서 이메일 알림을 전송한다.
- Budget Alert는 비용 경고이며 자동 결제 차단 장치로 간주하지 않는다.
- V1에서는 Budget 초과 시 결제 계정이나 서비스를 자동으로 중지하지 않는다.
- Cloud SQL 도입 후 정상 예상 월 비용의 120%를 기준으로 Budget을 다시 산정한다.
- Cloud Run 최소 Instance는 `0`, 최대 Instance는 `1`로 설정한다.
- Artifact Registry에는 최근 Production Image 5개만 유지한다.
- 불필요한 Secret Version과 Storage 객체를 정리한다.
- Cloud SQL은 Production 운영 직전에 생성한다.
- Production 생성 전 Pricing Calculator로 Region과 Instance 비용을 확인한다.

### 서비스별 판단

| 서비스 | V1 비용 방향 |
|----|----|
| Firebase Hosting | 개인 사용량에서는 무료 할당량 우선 사용 |
| Cloud Run | Scale to Zero와 최대 Instance 제한 |
| Secret Manager | 무료 Secret Version 및 Access 범위 내 사용 |
| Cloud Storage | Markdown만 저장하므로 소량 비용 허용 |
| Cloud Tasks | LINE 이벤트에만 사용하고 Queue와 재시도 횟수를 제한 |
| Artifact Registry | Image 5개 유지 정책으로 Storage 제한 |
| Cloud SQL | 주요 고정비 후보, 운영 직전 최종 결정 |

Cloud SQL의 공유 Core Instance는 저비용 Test·개발 용도이며 SLA 대상이 아니다. 개인용 V1에서 사용할 수 있는지는 실제 부하와 가격을 확인해 결정하며, GCP가 Production 용도로 권장하는 구성과 개인 프로젝트의 비용 목표가 다를 수 있음을 인지한다.

## Production 배포 직전 확정 사항

- Cloud SQL Instance 사양과 예상 월 비용
  - Backend 구현 완료 후 Production 운영 직전에 Pricing Calculator로 산정
  - Database Version은 MySQL 8.4, Edition은 Enterprise로 고정

## 참고 문서

- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [LINE Messaging API webhook](https://developers.line.biz/en/docs/messaging-api/receiving-messages/)
- [LINE webhook signature verification](https://developers.line.biz/en/docs/messaging-api/verify-webhook-signature/)
- [Cloud Run pricing](https://cloud.google.com/run/pricing)
- [Secret Manager pricing](https://cloud.google.com/secret-manager/pricing)
- [Cloud SQL pricing](https://cloud.google.com/sql/pricing)
- [Cloud SQL free trial instance](https://docs.cloud.google.com/sql/docs/mysql/free-trial-instance)
- [Cloud SQL MySQL versions](https://docs.cloud.google.com/sql/docs/mysql/db-versions)
- [Cloud Tasks HTTP target](https://docs.cloud.google.com/tasks/docs/creating-http-target-tasks)
- [Google Cloud Free Tier](https://docs.cloud.google.com/free/docs/free-cloud-features)
- [Firebase pricing](https://firebase.google.com/pricing)
- [Cloud Billing budgets](https://docs.cloud.google.com/billing/docs/how-to/budgets)
- [Cloud Run health checks](https://docs.cloud.google.com/run/docs/configuring/healthchecks)
