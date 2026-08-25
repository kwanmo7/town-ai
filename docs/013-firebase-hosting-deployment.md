# Firebase Hosting 배포 및 검증

## 목적

Town AI React Frontend를 기존 GCP Project `town-ai`의 Firebase Hosting에 배포하고,
`/api/**` 요청을 Tokyo Region의 Cloud Run Backend로 전달한다.

## 확정 구성

```text
GCP/Firebase Project : town-ai
Cloud Run Service    : town-ai-api
Cloud Run Region     : asia-northeast1
Live URL             : https://town-ai.web.app
Alternative URL      : https://town-ai.firebaseapp.com
```

| 파일 | 역할 |
| --- | --- |
| `frontend/firebase.json` | 정적 파일, Cache Header, Cloud Run·SPA Rewrite |
| `frontend/.firebaserc` | 기본 Firebase Project `town-ai` 연결 |
| `frontend/cloudbuild.preview.yaml` | PR별 7일 Preview Channel 배포 |
| `frontend/cloudbuild.production.yaml` | `main` Live Channel 배포 |

## 배포 전 보안 조건

Firebase Hosting과 Preview Channel URL은 공개된다. Preview도 `/api/**`를 실제 Cloud Run에
전달한다. 현재 Web 관리 API가 인증 없이 Area·Visit·Report 생성·수정·삭제를 허용한다면
주소를 아는 제3자가 데이터를 변경할 수 있다.

다음 조건을 충족하기 전에는 Preview 및 Live Trigger를 활성화하지 않는다.

- [x] Firebase Google 로그인 UI와 ID Token 전달
- [x] Firebase Admin SDK 기반 ID Token 검증
- [x] 허용된 단일 UID만 관리 API 호출 허용
- [x] LINE Webhook Signature, Cloud Tasks OIDC 및 Health Check 예외 경로 분리
- [x] LINE Report 공개 링크에 30일 만료 HMAC-SHA256 서명 적용
- [x] Preview에서 인증·권한·기존 LINE 흐름 회귀 검증
  - [x] 허용 UID 로그인과 Dashboard 접근
  - [x] 다른 Firebase UID의 관리 권한 거부
  - [x] 실제 LINE Webhook → Cloud Tasks → OIDC 처리

## 1. Firebase 활성화

1. Firebase Console에서 기존 Google Cloud Project `town-ai`를 선택해 Firebase를 추가한다.
2. 별도로 사용할 계획이 없다면 Google Analytics는 활성화하지 않는다.
3. `Build > Hosting > Get started`에서 기본 Hosting Site를 생성한다.
4. Repository에는 설정 파일이 이미 있으므로 Console 안내의 `firebase init`으로 덮어쓰지 않는다.
5. 기본 Site ID와 `.firebaserc`의 Project ID가 `town-ai`인지 확인한다.
6. `Security > Authentication`에서 Google Provider를 활성화한다.
7. 최초 Google 로그인 후 Authentication 사용자 목록에서 본인 UID를 확인한다.

## 2. 인증 설정

Cloud Run의 일반 환경변수에 다음 값을 설정한다.

```text
WEB_AUTH_ENABLED=true
FIREBASE_PROJECT_ID=town-ai
FIREBASE_ALLOWED_UID={본인 Firebase UID}
REPORT_LINK_VALIDITY=30d
```

`FIREBASE_ALLOWED_UID`는 Secret Manager가 아닌 일반 환경변수로 관리한다. Cloud Run은
연결된 Runtime Service Account의 ADC로 Firebase Admin SDK를 초기화한다. 별도 Service
Account JSON Key를 생성하지 않는다.

Secret Manager에 32자 이상의 무작위 값으로 `REPORT_LINK_SIGNING_SECRET`을 만들고 Cloud
Run의 같은 이름 환경변수에 Secret Reference로 연결한다. 이 Secret은 LINE Report 공개
링크 서명 전용이며 `LINE_CHANNEL_SECRET`과 공유하지 않는다.

새 Revision 배포 후 기존 LINE 메시지에 들어 있던 서명 없는 `/api/reports/{id}` 링크는
더 이상 열리지 않는다. LINE의 리포트 조회 메뉴에서 같은 기존 Report를 다시 선택하면
AI를 재호출하지 않고 새로운 30일 서명 링크를 받을 수 있다.

Local에서 인증 UI를 생략하려면 `frontend/.env.local`에 다음 값을 사용한다.

```dotenv
VITE_WEB_AUTH_ENABLED=false
```

실제 Firebase Token 검증을 Local에서 수행하려면 Backend도 `WEB_AUTH_ENABLED=true`로
실행해야 한다. Firebase 공식 문서상 일반 `gcloud auth application-default login`의 최종
사용자 Credential은 Firebase Authentication에 바로 사용할 수 없으므로, 실제 통합 검증은
Cloud Run Preview Revision에서 우선 수행한다.

## 3. Cloud Build 권한

Frontend Trigger에서 사용할 Cloud Build Service Account에 다음 역할을 부여한다.

```text
Firebase Hosting Admin : roles/firebasehosting.admin
API Keys Viewer        : roles/serviceusage.apiKeysViewer
```

Firebase CLI 배포에는 두 역할이 모두 필요하다. 기존 Backend Build Service Account를
재사용할 수 있지만 Runtime Service Account와는 분리한다. Cloud Run Rewrite 확인에서
권한 오류가 발생할 때만 Cloud Run Viewer를 추가한다.

## 4. Developer Connect Trigger

### Preview

```text
Name              : town-ai-frontend-preview
Event             : Pull request
Base Branch       : ^main$
Repository        : kwanmo7/town-ai
Build Config      : frontend/cloudbuild.preview.yaml
Service Account   : Cloud Build 배포용 Service Account
```

PR 번호가 12이면 `pr-12` Channel을 생성하고 7일 후 자동 만료한다. Preview URL은 Build
Log에 출력된다. 외부 사용자가 PR Build를 임의로 실행하지 못하도록 Owner·Collaborator만
자동 실행하고 그 외 PR은 승인 후 실행하도록 Trigger의 Comment Control을 설정한다.

### Production

```text
Name              : town-ai-frontend-production
Event             : Push to a branch
Branch            : ^main$
Repository        : kwanmo7/town-ai
Build Config      : frontend/cloudbuild.production.yaml
Service Account   : Cloud Build 배포용 Service Account
```

Production Trigger는 Web 인증이 구현되고 Preview 검증이 끝난 뒤 생성하거나 활성화한다.
`main` Branch Ruleset은 기존 GitHub Actions CI 성공을 Merge 조건으로 유지한다.

## 5. Local 명령

Node.js Application 의존성에는 Firebase CLI를 포함하지 않는다. 명령 실행 시 검증된 CLI
버전을 일회성으로 사용한다.

```powershell
cd frontend
npx --yes firebase-tools@15.26.0 login
npm run hosting:serve
npm run hosting:preview
npm run hosting:deploy
```

- `hosting:serve`: Production Build 후 Hosting 설정을 Local에서 확인
- `hosting:preview`: `manual-preview` Channel에 1일간 배포
- `hosting:deploy`: Live Channel 배포

세 Hosting 명령과 Cloud Build는 `build:firebase`를 사용한다. 이 Build Mode는 추적 가능한
`frontend/.env.firebase`에서 `VITE_WEB_AUTH_ENABLED=true`를 강제하므로 Local
`.env.local` 설정과 관계없이 공개 배포에 로그인 화면이 포함된다.

로컬 배포는 Google 계정 로그인 또는 Application Default Credentials가 필요하다. Secret,
Refresh Token이나 Service Account JSON Key는 Repository에 저장하지 않는다.

## 6. 검증 기준

| 검증 항목 | 기대 결과 |
| --- | --- |
| `/` 직접 접속 | Dashboard 표시 |
| `/areas` 직접 접속·새로고침 | React Router 화면 표시 |
| `/api/areas` | Firebase Hosting을 통해 Cloud Run 응답 반환 |
| 정적 `/assets/**` | `Cache-Control: public,max-age=31536000,immutable` |
| `/index.html` | `Cache-Control: no-cache` |
| Preview Channel | PR별 URL 생성, 7일 만료 |
| Live Channel | `town-ai.web.app`과 `town-ai.firebaseapp.com` 정상 표시 |
| 권한 | 허용 사용자만 관리 API 호출 가능 |
| 미로그인 API | `401 AUTHENTICATION_REQUIRED` |
| 잘못된 Token | `401 INVALID_FIREBASE_TOKEN` |
| 다른 Firebase UID | `403 WEB_ACCESS_DENIED` |
| LINE Webhook | Firebase 인증과 관계없이 기존 Signature 검증 유지 |
| Cloud Tasks | Firebase 인증과 관계없이 기존 OIDC 검증 유지 |
| Web Report 본문·다운로드 | Firebase Bearer Token이 있어야 정상 응답 |
| LINE Report 본문·다운로드 | 유효한 30일 HMAC 서명 URL만 정상 응답 |
| 변조한 LINE Report URL | `401 INVALID_REPORT_LINK` |
| 만료된 LINE Report URL | `410 REPORT_LINK_EXPIRED` |

문제가 발생하면 Firebase Hosting Release History에서 직전 정상 Version으로 Rollback한다.

## 현재 확인 결과

2026-08-26 기준 기존 GCP Project에 Firebase가 활성화됐고 Web App 등록과 Google 로그인
Provider 활성화를 완료했다. Frontend 로그인, Backend ID Token 검증, 단일 UID 제한과
LINE·Cloud Tasks·Health 예외 경로, LINE Report 만료 서명 URL 구현 및 자동 테스트도
완료했다.

배포된 Cloud Run Revision을 외부에서 확인한 결과는 다음과 같다.

- Liveness와 Readiness는 모두 `200 UP`
- Token 없는 관리 API는 `401 AUTHENTICATION_REQUIRED`
- 잘못된 Bearer Token은 `401 INVALID_FIREBASE_TOKEN`
- 인증 없는 Web Report 본문 요청은 `401 AUTHENTICATION_REQUIRED`
- 변조한 LINE Report 서명 URL은 `401 INVALID_REPORT_LINK`

수동 `manual-preview` Channel을 배포했으며 임시 URL의 `/`, `/areas`, `/reports` 직접
접속은 모두 `200`으로 SPA Rewrite가 정상 동작했다. `/api/areas`와 `/api/auth/me`는 Token
없이 `401`을 반환해 Cloud Run Rewrite와 관리 API 인증 경계도 확인했다. 정적 Asset은
`public,max-age=31536000,immutable`, `index.html`은 `no-cache` Header를 반환한다.
Preview는 Firebase Authentication 허용 Domain에 자동 등록됐고 Google 로그인 화면도
표시된다.

Preview 브라우저에서 허용 UID로 로그인해 Dashboard와 관리 API의 정상 접근을 확인했고,
다른 Firebase UID로 로그인하면 `WEB_ACCESS_DENIED` 안내와 함께 관리 화면 진입이 차단되는
것도 확인했다. Live Channel의 `town-ai.web.app`은 아직 `404 Site Not Found`를 반환하므로
Production Hosting 배포는 아직 완료되지 않았다.

Developer Connect의 `main` Push를 감지하는 `town-ai-web-production` Cloud Build Trigger를
`asia-northeast1`에 생성했다. Trigger는 `frontend/cloudbuild.production.yaml`과 전용
`town-ai-firebase-deployer` Service Account를 사용한다. 실제 Live Channel 최초 배포와
Production 화면 검증은 다음 PR을 `main`에 Merge한 뒤 수행한다.

Cloud Run에 `LINE_EVENT_DISPATCHER=cloud-tasks`와 OIDC 설정을 적용한 뒤 내부 Endpoint의
인증 없는 요청은 `401 INVALID_LINE_TASK_AUTHORIZATION`으로 차단되는 것을 확인했다.
구조가 정상인 잘못된 JWT도 `401`이지만 JWT 형식 자체가 깨진 문자열은 Google 인증
Library의 파싱 예외가 변환되지 않아 `500`을 반환하는 문제를 확인했다. 해당 예외를 내부
OIDC 검증 실패로 변환하는 회귀 테스트와 수정은 완료했으며, Backend 재배포 후 `401` 응답과
실제 LINE 메시지의 Cloud Tasks·OIDC 처리를 다시 검증한다.

실제 LINE 회귀 검증 중 Cloud Tasks API가 비활성화돼 있고 `asia-northeast1`의 `line-events`
Queue가 생성되지 않아 Webhook이 `503`을 반환하는 운영 설정 누락을 확인했다. 2026-08-26에
Cloud Tasks API를 활성화하고 Queue를 생성했으며, Cloud Run Runtime Service Account에
`roles/cloudtasks.enqueuer`를 추가했다. Queue는 초당·동시 실행 각각 1개, 최대 5회·1시간
이내 재시도로 구성했다. 이후 실제 LINE 이벤트에서 Webhook `200`, OIDC 내부 Task Endpoint
`204`와 잔여 Task 0건을 확인해 비동기 처리 경로가 정상 복구된 것을 검증했다.

## 참고 문서

- [Firebase Hosting 시작](https://firebase.google.com/docs/hosting/quickstart)
- [Preview Channel과 Live 배포](https://firebase.google.com/docs/hosting/test-preview-deploy)
- [Firebase CLI 인증](https://firebase.google.com/docs/cli)
- [Cloud Build Trigger](https://docs.cloud.google.com/build/docs/automating-builds/create-manage-triggers)
- [Firebase Hosting IAM](https://firebase.google.com/docs/projects/iam/roles-predefined-product)
- [Google 로그인](https://firebase.google.com/docs/auth/web/google-signin)
- [Firebase ID Token 검증](https://firebase.google.com/docs/auth/admin/verify-id-tokens)
- [Firebase Admin SDK 설정](https://firebase.google.com/docs/admin/setup)
