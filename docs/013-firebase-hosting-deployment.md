# Firebase Hosting 및 Web 인증 설계

## 목차

1. 목적
2. 확정 구성
3. 배포 구조
4. Hosting Routing
5. 인증과 접근 제어
6. Build 및 배포
7. Cache 정책
8. 장애와 Rollback
9. 보안 원칙
10. 관련 파일
11. 참고 문서

## 1. 목적

Town AI React Frontend를 Firebase Hosting에 배포하고 `/api/**` 요청을 Tokyo Region의
Cloud Run Backend로 전달한다. Web 관리 화면은 공개 URL을 사용하지만 Firebase
Authentication과 Backend 권한 검증을 통해 허용된 한 명만 데이터에 접근하도록 한다.

이 문서는 Hosting, 인증, 자동 배포와 운영 경계를 정의한다. 실제 구현·배포 진행 상태와
검증 결과는 `999-TODO.md`에서 관리한다.

## 2. 확정 구성

| 항목 | 값 |
| --- | --- |
| GCP/Firebase Project | `town-ai` |
| Firebase Web App | `town-ai-web` |
| Hosting Live URL | `https://town-ai.web.app` |
| Hosting 대체 URL | `https://town-ai.firebaseapp.com` |
| Backend Service | `town-ai-api` |
| Backend Region | `asia-northeast1` |
| 로그인 제공업체 | Google |
| Web 접근 범위 | `FIREBASE_ALLOWED_UID`로 지정한 단일 사용자 |
| Production 배포 | `main` Push를 감지하는 Cloud Build Trigger |
| Preview 배포 | 필요할 때만 수동 Preview Channel 사용 |

Firebase App 설정값의 `apiKey`, `authDomain`, `projectId` 등은 Browser에서 Firebase
Project를 식별하기 위한 공개 설정이다. 관리 권한을 부여하는 비밀 값으로 취급하지 않으며,
실제 권한 경계는 Firebase ID Token과 Backend의 UID 검증이다.

## 3. 배포 구조

```text
GitHub main
  → Developer Connect
    → Cloud Build
      → React Production Build
      → Firebase Hosting Live Channel

Browser
  → Firebase Hosting
    ├─ /assets/**       → 정적 Asset
    ├─ /api/**          → Cloud Run town-ai-api
    └─ 그 외 경로       → /index.html
```

Frontend Build와 Hosting 배포는 전용 Service Account
`town-ai-firebase-deployer@town-ai.iam.gserviceaccount.com`을 사용한다. Backend Build
계정과 Cloud Run Runtime 계정은 이 계정과 분리한다.

개인용 V1에서는 PR마다 실제 Production Backend와 연결되는 Preview 환경을 자동 생성하지
않는다. `cloudbuild.preview.yaml`은 수동 통합 검증이 필요할 때만 사용한다.

## 4. Hosting Routing

### 4.1 Cloud Run Rewrite

`/api/**`는 Firebase Hosting의 Cloud Run Rewrite를 통해 `asia-northeast1`의
`town-ai-api`로 전달한다. Browser는 Hosting과 동일한 Origin으로 API를 호출하므로 별도
Production CORS Origin을 추가하지 않는다.

### 4.2 SPA Fallback

정적 파일과 `/api/**`에 해당하지 않는 경로는 `/index.html`로 Rewrite한다. 따라서
`/areas`, `/visits`, `/statistics`, `/reports`에 직접 접속하거나 새로고침해도 React
Router가 화면을 복원한다.

Rewrite 순서는 구체적인 API 규칙을 SPA Fallback보다 먼저 둔다. 순서가 바뀌면 API 응답
대신 `index.html`이 반환될 수 있다.

## 5. 인증과 접근 제어

### 5.1 Web 로그인 흐름

```text
Google 로그인
  → Firebase Authentication ID Token 발급
  → Frontend가 Authorization: Bearer {token} 전송
  → Backend가 Firebase Admin SDK로 서명·만료·Project 검증
  → Token UID와 FIREBASE_ALLOWED_UID 비교
  → 허용된 사용자만 관리 API 처리
```

Frontend는 Token 갱신을 Firebase SDK에 맡기며, API 요청 시점의 유효한 Token을 사용한다.
로그아웃하거나 인증 상태가 사라지면 관리 화면으로 진입시키지 않고 API 요청도 보내지 않는다.

Cloud Run은 Runtime Service Account의 Application Default Credentials로 Firebase Admin
SDK를 초기화한다. Service Account JSON Key는 생성하거나 Repository에 저장하지 않는다.

### 5.2 Endpoint별 인증 경계

| Endpoint | 인증 방식 |
| --- | --- |
| Web 관리 API | Firebase ID Token + 허용 UID |
| Web Report 본문·다운로드 | Firebase ID Token + 허용 UID |
| LINE Webhook | LINE HMAC-SHA256 Signature |
| Cloud Tasks 내부 Endpoint | Google OIDC Token |
| LINE Report 본문·다운로드 | 만료 시간이 포함된 HMAC-SHA256 서명 URL |
| Liveness·Readiness | 인증 없음 |

Firebase 인증 Filter가 LINE, Cloud Tasks와 Health Check 경로를 가로막지 않도록 각 인증
경계를 명시적으로 분리한다.

LINE Report 링크는 `REPORT_LINK_SIGNING_SECRET`으로 서명하고 기본 30일 동안만 유효하게
한다. 이 Secret은 `LINE_CHANNEL_SECRET`과 공유하지 않으며 Secret Manager Reference로
주입한다. Web Report와 LINE Report는 같은 본문을 사용하더라도 접근 정책을 공유하지 않는다.

### 5.3 Backend 환경변수

```text
WEB_AUTH_ENABLED=true
FIREBASE_PROJECT_ID=town-ai
FIREBASE_ALLOWED_UID={허용할 Firebase UID}
REPORT_LINK_VALIDITY=30d
```

`REPORT_LINK_SIGNING_SECRET`은 일반 문자열 환경변수가 아닌 Secret Manager Reference로
연결한다. `FIREBASE_ALLOWED_UID`는 식별자이므로 일반 환경변수로 관리한다.

Local UI 개발에서만 인증을 생략할 때는 `VITE_WEB_AUTH_ENABLED=false`를 사용한다. 공개
Build는 추적 가능한 `.env.firebase`를 통해 인증을 항상 활성화한다.

## 6. Build 및 배포

### 6.1 Production Build

Cloud Build는 `frontend/cloudbuild.production.yaml`을 사용해 의존성을 고정 설치하고,
검증된 Node.js 환경에서 `build:firebase`를 실행한 뒤 Firebase Hosting Live Channel에
배포한다. Local `.env.local`은 Production Build 입력으로 사용하지 않는다.

```text
Trigger Name    : town-ai-web-production
Event           : Push to a branch
Branch          : ^main$
Repository      : kwanmo7/town-ai
Build Config    : frontend/cloudbuild.production.yaml
Service Account : town-ai-firebase-deployer
```

GitHub Actions는 Lint, Unit Test, Build와 E2E를 검증한다. Cloud Build는 `main`에 반영된
검증 완료 Commit을 실제 Hosting에 배포한다. 두 Pipeline의 역할을 중복시키지 않는다.

### 6.2 수동 Preview

Preview Channel은 Production Backend Rewrite, 인증과 Cache Header를 실제 Hosting
환경에서 검증해야 할 때만 수동으로 생성한다. 개인용 V1에서는 PR Trigger를 만들지 않는다.
Preview URL도 공개 URL이므로 Production과 같은 Firebase 인증 정책을 사용한다.

### 6.3 Local 명령

```powershell
cd frontend
npm run hosting:serve
npm run hosting:preview
npm run hosting:deploy
```

- `hosting:serve`: Production Build와 Hosting 설정의 Local 확인
- `hosting:preview`: 제한된 기간의 수동 Preview Channel 배포
- `hosting:deploy`: Live Channel 수동 배포

Firebase CLI Credential, Refresh Token과 Service Account Key는 Repository에 저장하지 않는다.

## 7. Cache 정책

| 대상 | Cache-Control |
| --- | --- |
| Hash가 포함된 `/assets/**` | `public,max-age=31536000,immutable` |
| `/index.html` | `no-cache` |
| API 응답 | Backend가 결정 |

Hash Asset은 장기 Cache하고 진입 문서는 매번 재검증한다. 이 조합으로 새 배포 직후에도
최신 Asset 경로가 포함된 `index.html`을 받으면서 변경되지 않은 Asset은 재사용한다.

## 8. 장애와 Rollback

Hosting 배포에 문제가 생기면 Firebase Hosting Release History에서 직전 정상 Version으로
Rollback한다. Backend와 Frontend가 함께 변경된 경우 API 호환성을 먼저 확인하고 필요하면
Cloud Run Traffic도 이전 Revision으로 되돌린다.

배포 후 최소 확인 범위는 다음과 같다.

- 루트 및 React Router 직접 경로
- `/api/**` Cloud Run Rewrite
- 허용 UID 로그인과 다른 UID 거부
- 새로고침·Token 갱신·로그아웃
- Report 보기·다운로드의 Web/LINE 인증 분리
- Desktop과 Mobile 주요 화면
- 정적 Asset과 `index.html` Cache Header

## 9. 보안 원칙

- Hosting URL이 공개된다는 사실을 전제로 Backend에서 모든 관리 권한을 검증한다.
- Firebase Web 설정값과 Secret을 구분한다.
- Firebase Admin 또는 Service Account JSON Key를 Frontend에 포함하지 않는다.
- Build Service Account와 Runtime Service Account를 분리하고 최소 역할만 부여한다.
- LINE Webhook, Cloud Tasks, Web 사용자와 공개 Report 링크의 인증 방식을 혼합하지 않는다.
- Source Map과 Build 산출물에 비공개 환경변수가 포함되지 않는지 배포 전에 확인한다.

## 10. 관련 파일

| 파일 | 역할 |
| --- | --- |
| `frontend/firebase.json` | 정적 파일, Cache Header, Cloud Run·SPA Rewrite |
| `frontend/.firebaserc` | 기본 Firebase Project 연결 |
| `frontend/.env.firebase` | 공개 Firebase Build 설정과 인증 활성화 |
| `frontend/cloudbuild.production.yaml` | `main` Live Channel 배포 |
| `frontend/cloudbuild.preview.yaml` | 수동 Preview Channel 배포 |
| `.github/workflows/ci.yml` | Frontend 자동 검증 |

## 11. 참고 문서

- [Firebase Hosting 시작](https://firebase.google.com/docs/hosting/quickstart)
- [Preview Channel과 Live 배포](https://firebase.google.com/docs/hosting/test-preview-deploy)
- [Cloud Build Trigger](https://docs.cloud.google.com/build/docs/automating-builds/create-manage-triggers)
- [Firebase Hosting IAM](https://firebase.google.com/docs/projects/iam/roles-predefined-product)
- [Google 로그인](https://firebase.google.com/docs/auth/web/google-signin)
- [Firebase ID Token 검증](https://firebase.google.com/docs/auth/admin/verify-id-tokens)
- [Firebase Admin SDK 설정](https://firebase.google.com/docs/admin/setup)
