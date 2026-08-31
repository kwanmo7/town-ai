# 배포 및 운영 설계

## 목적

Town AI V1을 GCP에 안전하게 배포하고 개인 사용량에서 고정비와 운영 부담을 줄인다.

- Local과 Production 두 환경만 운영한다.
- Backend는 Cloud Run Container로 배포한다.
- Frontend는 Firebase Hosting에 배포한다.
- Source of Truth는 Serverless Firestore를 사용한다.
- Report Markdown은 GCS에 저장한다.
- Secret은 Git과 Docker Image에 포함하지 않는다.
- CI는 GitHub Actions, CD는 Developer Connect·Cloud Build Trigger로 관리한다.

## 환경 구성

### Local

```text
React Development Server
        │ /api Proxy
        ▼
Local Spring Boot
   ├── Firestore Emulator
   ├── LocalReportStorage
   ├── LocalLineEventDispatcher
   └── OpenAI API
```

| 구성 | Local |
| --- | --- |
| Frontend | Vite Development Server |
| Backend | Local Spring Boot |
| Database | Firestore Emulator |
| Report | Local File System |
| Secret | Process 환경변수 또는 `.env.local` |

실행 방법은 `007-local-firestore-emulator.md`를 따른다.

### Production

```text
사용자
  │
  ├── Firebase Hosting ── React
  │        └── /api/** Rewrite
  │
  └── Cloud Run ───────── Spring Boot
           ├── Cloud Firestore
           ├── Cloud Storage
           ├── Cloud Tasks
           ├── Secret Manager
           ├── Firebase Admin
           ├── LINE Messaging API
           └── OpenAI API
```

| 구성 | Production |
| --- | --- |
| Frontend | Firebase Hosting |
| Backend | Cloud Run `town-ai-api` |
| Database | Firestore Standard, Native mode |
| Report | GCS `gs://town_ai` |
| Async Queue | Cloud Tasks `line-events` |
| Secret | Secret Manager |
| Container | Artifact Registry |
| CI/CD | GitHub Actions + Cloud Build |

## Region

- Cloud Run, Firestore, GCS, Cloud Tasks와 Artifact Registry는
  `asia-northeast1`을 기준으로 한다.
- Firestore `town-ai` Database의 Location은 생성 후 변경할 수 없으므로 생성 화면에서
  Tokyo를 확인한다.
- Firebase Hosting은 Global CDN을 사용한다.

## Runtime Version

```text
Java                          : 25 LTS
Spring Boot                   : 4.1.x
Gradle                        : 9.6.1
Node.js                       : 24 LTS
React                         : 19.x
Firebase CLI                  : 15.26.0
Google Cloud Java Libraries BOM: 26.83.0
```

Patch Version은 자동으로 Production에 반영하지 않고 CI와 수동 회귀 테스트 후 갱신한다.

## 애플리케이션 환경변수

### 일반 환경변수

```text
PORT
USER_TIME_ZONE
FIRESTORE_PROJECT_ID
FIRESTORE_DATABASE_ID
WEB_AUTH_ENABLED
FIREBASE_PROJECT_ID
FIREBASE_ALLOWED_UID
OPENAI_BASE_URL
OPENAI_REPORT_MODEL
OPENAI_CONNECT_TIMEOUT
OPENAI_READ_TIMEOUT
REPORT_STORAGE_TYPE
REPORT_LOCAL_DIRECTORY
REPORT_LINK_VALIDITY
GCP_PROJECT_ID
GCP_REGION
GCS_BUCKET_NAME
LINE_EVENT_DISPATCHER
LINE_MESSAGING_API_BASE_URL
LINE_MESSAGING_API_CONNECT_TIMEOUT
LINE_MESSAGING_API_READ_TIMEOUT
LINE_LOCAL_TASK_TARGET_URL
LINE_CLOUD_TASKS_QUEUE
LINE_CLOUD_TASKS_TARGET_URL
LINE_CLOUD_TASKS_OIDC_AUDIENCE
LINE_CLOUD_TASKS_SERVICE_ACCOUNT
```

Production 권장값:

```text
USER_TIME_ZONE=Asia/Tokyo
FIRESTORE_PROJECT_ID=town-ai
FIRESTORE_DATABASE_ID=town-ai
WEB_AUTH_ENABLED=true
FIREBASE_PROJECT_ID=town-ai
FIREBASE_ALLOWED_UID={허용할 단일 Firebase UID}
OPENAI_REPORT_MODEL=gpt-5.6-luna
REPORT_STORAGE_TYPE=gcs
GCP_PROJECT_ID=town-ai
GCP_REGION=asia-northeast1
GCS_BUCKET_NAME=town_ai
REPORT_LINK_VALIDITY=30d
LINE_EVENT_DISPATCHER=cloud-tasks
LINE_CLOUD_TASKS_QUEUE=line-events
LINE_REPORT_BASE_URL=https://town-ai-api-574086886148.asia-northeast1.run.app
LINE_CLOUD_TASKS_TARGET_URL=https://town-ai-api-574086886148.asia-northeast1.run.app/internal/tasks/line-events
LINE_CLOUD_TASKS_OIDC_AUDIENCE=https://town-ai-api-574086886148.asia-northeast1.run.app
LINE_CLOUD_TASKS_SERVICE_ACCOUNT=town-ai-runtime@town-ai.iam.gserviceaccount.com
```

`FIRESTORE_EMULATOR_HOST`는 Local 전용이며 Production Cloud Run에 설정하지 않는다.

### Secret

```text
OPENAI_API_KEY
LINE_CHANNEL_SECRET
LINE_CHANNEL_ACCESS_TOKEN
LINE_ALLOWED_USER_ID
LINE_REPORT_BASE_URL
REPORT_LINK_SIGNING_SECRET
```

- Production Secret은 Secret Manager Reference로 Cloud Run에 주입한다.
- `REPORT_LINK_SIGNING_SECRET`은 LINE Report URL HMAC 전용 32자 이상 무작위 값이다.
- LINE Channel Secret이나 Firebase API Key와 공유하지 않는다.
- Firebase Web Config는 공개 식별자이며 비공개 Service Account Key가 아니다.
- Service Account JSON Key는 생성하거나 저장하지 않는다.

Firestore 전환 후 `DB_*` 환경변수, `DB_PASSWORD` Secret과 Cloud SQL 연결은 모두
제거했다. 현재 Production에는 위 6개 Runtime Secret만 존재한다.

## Firestore

```text
Project     : town-ai
Database ID : town-ai
Edition     : Standard
Mode        : Native
Location    : asia-northeast1
```

- Browser와 LINE Client는 Firestore에 직접 접근하지 않는다.
- `backend/firestore.rules`는 모든 Client read/write를 거부한다.
- Firestore 접근에는 전용 Cloud Run Runtime Service Account
  `town-ai-runtime@town-ai.iam.gserviceaccount.com`의 `roles/datastore.user`를 사용한다.
- 기본 Compute Service Account의 기존 Project 역할은 전부 제거했다.
- Backend는 ADC로 인증한다.
- 복합 Index가 필요한 Query를 사용하지 않으므로 V1 Index 파일은 비어 있다.
- 자세한 데이터 모델은 `003-firestore-data-model.md`, 전환 원칙은 `014-firestore-migration-design.md`를 따른다.

Rules와 Index 배포:

```powershell
npx.cmd --yes firebase-tools@15.26.0 deploy `
  --only firestore:rules,firestore:indexes `
  --project town-ai `
  --config backend/firebase.json
```

## Firebase 인증

- Frontend는 Google 로그인 후 Firebase ID Token을 모든 관리 API에 전달한다.
- Backend는 Firebase Admin SDK로 Token을 검증한다.
- `FIREBASE_ALLOWED_UID`와 일치하는 단일 사용자만 관리 API를 사용할 수 있다.
- Health, LINE Webhook, Cloud Tasks 내부 Endpoint, 서명된 LINE Report Endpoint는 각각
  별도 인증 정책을 사용한다.
- Hosting 상세 절차는 `013-firebase-hosting-web-auth.md`를 따른다.

## LINE 비동기 처리

### Production

```text
LINE Webhook
→ HMAC-SHA256 Signature 검증
→ Firestore lineWebhookEvents 저장
→ Cloud Tasks enqueue
→ OIDC 내부 Endpoint 호출
→ Event Lease 획득
→ Parser·Report 처리
→ LINE Push
→ COMPLETED 또는 FAILED 전환
```

- 전달은 At-least-once로 간주한다.
- Webhook Event ID를 Firestore 문서 ID와 결정적 Task Name으로 사용한다.
- Cloud Tasks 요청은 전용 Service Account OIDC Token과 정확한 Audience로 검증한다.
- 잘못되거나 형식이 깨진 JWT는 `401`로 처리한다.
- LINE Push는 Event ID와 Message Purpose 기반 결정적 Retry Key를 사용한다.
- 최대 처리 횟수를 넘으면 Event를 `FAILED`로 종료하고 Best-effort 안내를 보낸다.
- 장시간 유휴 후 첫 LINE 요청은 별도 완료 조건으로 두지 않고 실제 운영 중 관찰한다.

### Local

`LINE_EVENT_DISPATCHER=local`과 `LocalLineEventDispatcher`를 사용한다. Cloud Tasks OIDC를
흉내 내지 않고 같은 Backend의 내부 Endpoint를 호출해 업무 흐름을 검증한다.

## Report Storage

### Local

```text
REPORT_STORAGE_TYPE=local
REPORT_LOCAL_DIRECTORY=./data/reports
```

### Production

```text
REPORT_STORAGE_TYPE=gcs
GCS_BUCKET_NAME=town_ai
```

- Bucket은 비공개로 유지한다.
- Runtime Service Account에 필요한 객체 권한만 부여한다.
- Markdown은 `text/markdown; charset=UTF-8`로 저장한다.
- 객체 경로는 `reports/v1/{type}/{filename}_{date}_{reportId}.md` 형식이다.
- Web 조회는 Firebase 인증, LINE 조회는 만료 HMAC URL을 사용한다.
- Firestore Metadata 저장 실패 시 새 GCS 객체를 Best-effort 삭제한다.
- Process가 객체 저장 직후 종료되는 고아 객체는 운영 Script로 Firestore 참조와 비교한다.

### Production 고아 객체 점검

기본 실행은 Dry Run이며 GCS를 변경하지 않는다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\production-cleanup-orphan-reports.ps1
```

실제 삭제는 출력된 대상을 먼저 검토한 뒤 두 Option을 함께 지정한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\production-cleanup-orphan-reports.ps1 `
  -DeleteOrphans `
  -ConfirmProductionCleanup
```

- 허용 범위는 `town-ai/town-ai`와
  `gs://town_ai/reports/v1/{area|compare|summary|all}/*.md`로 고정한다.
- 기본 24시간 이내의 미참조 객체는 생성 중일 수 있으므로 삭제 후보에서 제외한다.
- 삭제 시 Scan한 GCS Generation 일치 조건을 사용해 조회 이후 변경된 객체를 보호한다.
- 이미 없는 객체 삭제는 성공으로 처리하므로 같은 명령을 다시 실행할 수 있다.
- 운영 계정은 Firestore·GCS 조회 권한이 필요하며 삭제 모드에는 GCS 객체 삭제 권한이 필요하다.
- 삭제 후 Dry Run을 다시 실행해 `Eligible orphans: 0`을 확인한다.

## Docker

Backend Image는 Java 25 Multi-stage Build와 Non-root Runtime User를 사용한다.

```powershell
docker build -f backend/Dockerfile -t town-ai-backend:local backend
```

Container는 Cloud Run이 제공하는 `PORT`에서 수신한다. Local Secret, Gradle Cache,
Emulator Data와 Report Data는 Build Context 또는 실행 JAR에 포함하지 않는다.

Frontend는 Container로 배포하지 않고 Firebase Hosting용 정적 Asset으로 빌드한다.

## CI/CD

### GitHub Actions CI

`main` 대상 Pull Request에서 다음을 검증한다.

- Firestore Emulator 기반 Backend Test·Build·Javadoc
- Backend Docker Build
- Frontend ESLint·Unit Test·Production Build
- Chromium Desktop·Mobile E2E와 접근성 검사

Required Check 누락을 막기 위해 경로 필터를 사용하지 않는다.

### Backend CD

Developer Connect Cloud Build Trigger가 `main` Push를 감지해 Backend Image를 Build하고
Cloud Run `town-ai-api`에 배포한다. Build·배포는 전용 계정
`town-ai-backend-deployer@town-ai.iam.gserviceaccount.com`, Runtime은 `town-ai-runtime`을
사용한다. 새 Revision을 Console에서 수동 생성할 때도 Runtime Service Account가 기본
Compute 계정으로 바뀌지 않았는지 확인한다.

### Frontend CD

`town-ai-web-production` Trigger가 `frontend/cloudbuild.production.yaml`을 사용해
`main` Push 시 Firebase Hosting Live Channel을 배포한다. 개인 프로젝트에서는 별도
PR Preview Trigger를 운영하지 않는다.

## Health Check

```text
Liveness  : /actuator/health/liveness
Readiness : /actuator/health/readiness
```

- Health Endpoint는 인증 없이 접근할 수 있다.
- Liveness는 Process 생존 여부, Readiness는 Application 준비 상태를 확인한다.
- Firestore를 Probe마다 읽으면 불필요한 비용과 외부 장애 결합이 생기므로 매 Probe에서
  문서 조회를 수행하지 않는다.
- 실제 Database 연결은 배포 후 인증된 Smoke Test로 확인한다.

## Logging과 Monitoring

비밀값과 개인정보를 기록하지 않는다.

기록 대상:

- 요청 Method·Path·Status·처리 시간
- OpenAI 요청 목적·모델·Prompt Version·Latency
- Report ID·Type·GCS 객체 경로
- LINE Event ID·Event Type·처리 상태·시도 횟수
- Cloud Tasks enqueue·OIDC 검증 실패의 분류된 오류 코드
- Firestore Transaction 충돌·권한·Timeout 오류

기록 금지:

- Authorization Header와 Firebase ID Token
- LINE Channel Secret·Access Token·User ID 원문
- OpenAI API Key
- Report 서명 Secret
- 사용자의 자연어 원문과 메모 전체

## 비용 관리

- Cloud Run 최소 Instance는 `0`, 최대 Instance는 `1`을 기본으로 한다.
- Firestore는 사용량 기반 과금이며 개인 V1의 수십 건 데이터에 적합하다.
- Firebase Hosting과 GCS 사용량을 함께 Monitoring한다.
- 월 Budget은 `¥1,000`, 알림은 실제 사용액 50%·80%·100%와 예상 80%로 유지한다.
- Document Read가 증가하면 Collection 전체 조회를 Query·Index·Pagination으로 바꾼다.
- 현재 Database의 `freeTier=true`를 확인했다. PITR과 예약 Backup은 필수 기능이 아니며
  별도 과금되므로 개인 V1에서는 비활성 상태를 유지한다.
- 대량 수정·삭제나 데이터 이전 전에는 수동 Export 여부를 결정한다. 자동 Backup이 필요할
  정도로 데이터 중요도나 규모가 커지면 주기와 보존 기간을 별도로 정한다.
- GCS 기반 Firestore 복원 검증 후 Legacy Cloud SQL Instance, DB 환경변수·Secret 참조와
  연결 설정을 모두 제거했다.

## Production 배포 체크리스트

- [x] Firestore `town-ai`, Standard, Native, Tokyo 생성 확인
- [x] `town-ai` Database Rules와 Index 배포
- [x] Runtime Service Account에 `roles/datastore.user` 부여
- [x] `FIRESTORE_PROJECT_ID=town-ai`, `FIRESTORE_DATABASE_ID=town-ai` 적용
- [x] Cloud SQL 관련 환경변수·Secret·연결이 새 Revision에서 제거됨
- [x] GCS Report 기반 Area·Visit 복원과 Markdown 9개 불변 검증
- [x] 기존 GCS Report 9개의 Firestore Metadata 복원과 Report Counter 검증
- [x] Backend CI와 Docker Build 통과
- [x] Firestore 기반 Cloud Run Revision 배포와 인증 API Smoke Test
- [x] Web 로그인과 Area·Visit·Statistics·Report 조회 회귀 검증
- [x] LINE Webhook·Cloud Tasks·OIDC·Draft·Report 회귀 검증
- [x] 최소 Instance 0 설정과 새 Revision Cold Start·Health 검증
- [x] Legacy Cloud SQL `town-ai-api` Instance 삭제
- [x] Legacy DB Secret·기본 Compute 계정 권한 정리
- [x] Firestore Report 참조와 GCS 객체 10개의 고아 객체 Dry Run 검증

## 참고 문서

- `003-firestore-data-model.md`
- `007-local-firestore-emulator.md`
- `legacy/010-legacy-cloud-sql-production-validation.md`
- `013-firebase-hosting-web-auth.md`
- `014-firestore-migration-design.md`
- `015-firestore-production-cutover-validation.md`
- <https://firebase.google.com/docs/firestore/pricing>
- <https://cloud.google.com/firestore/docs/security/iam>
- <https://cloud.google.com/run/docs/configuring/services/service-identity>
