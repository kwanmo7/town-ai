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

- Web 관리 API 요청의 사용자 인증
- 허용된 단일 사용자만 관리 API를 호출하도록 권한 검증
- LINE Webhook Signature, Cloud Tasks OIDC 및 Health Check 예외 경로 분리
- LINE Report 공개 링크의 인증 또는 만료 URL 정책 확정

## 1. Firebase 활성화

1. Firebase Console에서 기존 Google Cloud Project `town-ai`를 선택해 Firebase를 추가한다.
2. 별도로 사용할 계획이 없다면 Google Analytics는 활성화하지 않는다.
3. `Build > Hosting > Get started`에서 기본 Hosting Site를 생성한다.
4. Repository에는 설정 파일이 이미 있으므로 Console 안내의 `firebase init`으로 덮어쓰지 않는다.
5. 기본 Site ID와 `.firebaserc`의 Project ID가 `town-ai`인지 확인한다.

## 2. Cloud Build 권한

Frontend Trigger에서 사용할 Cloud Build Service Account에 다음 역할을 부여한다.

```text
Firebase Hosting Admin : roles/firebasehosting.admin
API Keys Viewer        : roles/serviceusage.apiKeysViewer
```

Firebase CLI 배포에는 두 역할이 모두 필요하다. 기존 Backend Build Service Account를
재사용할 수 있지만 Runtime Service Account와는 분리한다. Cloud Run Rewrite 확인에서
권한 오류가 발생할 때만 Cloud Run Viewer를 추가한다.

## 3. Developer Connect Trigger

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

## 4. Local 명령

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

로컬 배포는 Google 계정 로그인 또는 Application Default Credentials가 필요하다. Secret,
Refresh Token이나 Service Account JSON Key는 Repository에 저장하지 않는다.

## 5. 검증 기준

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

문제가 발생하면 Firebase Hosting Release History에서 직전 정상 Version으로 Rollback한다.

## 현재 확인 결과

2026-08-12 기준 Local 환경에는 Firebase CLI 로그인과 Google Cloud ADC가 없으며,
`town-ai.web.app`과 `town-ai.firebaseapp.com`은 모두 `404`를 반환한다. 설정 파일과 Cloud
Build 배포 정의는 준비됐지만 Firebase 활성화, 사용자 인증 및 실제 Preview·Live 배포는
아직 완료되지 않았다.

## 참고 문서

- [Firebase Hosting 시작](https://firebase.google.com/docs/hosting/quickstart)
- [Preview Channel과 Live 배포](https://firebase.google.com/docs/hosting/test-preview-deploy)
- [Firebase CLI 인증](https://firebase.google.com/docs/cli)
- [Cloud Build Trigger](https://docs.cloud.google.com/build/docs/automating-builds/create-manage-triggers)
- [Firebase Hosting IAM](https://firebase.google.com/docs/projects/iam/roles-predefined-product)
