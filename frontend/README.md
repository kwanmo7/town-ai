# Town AI Frontend

Town AI의 지역, 방문 기록, 통계와 AI 리포트를 관리하는 React Client다.

## 실행 환경

- Node.js 20 이상
- npm 10 이상
- Local Backend: `http://localhost:8080`

## Local 실행

```bash
npm install
npm run dev
```

Vite 개발 서버의 `/api` 요청은 기본적으로 `http://localhost:8080`으로 전달된다.
다른 Backend를 사용할 때는 `.env.local`에 다음 값을 설정한다.

```dotenv
VITE_API_PROXY_TARGET=http://localhost:8080
```

방문 기록은 `/visits`에서 자연어 설명과 다섯 점수를 입력하고, AI 초안의 지역·날짜·
메모를 확인한 뒤 저장한다. 점수는 Select Box의 사용자 선택값을 그대로 사용한다.
신규 지역 후보이면 확인 화면에서 위치를 수정한 뒤 Area와 Visit을 순서대로 등록한다.
기존 방문 기록은 상세 값을 불러와 지역·날짜·점수·메모를 전체 수정할 수 있으며,
삭제 확인 후 Hard Delete할 수 있다.

`/statistics`에서는 전체 방문 평균, 다섯 항목별 Area Top 5와 선택한 Area의 누적
방문 횟수·평균을 확인한다.

AI 리포트는 `/reports`의 새 리포트 Dialog에서 생성한다. `AREA`는 한 지역,
`COMPARE`는 방문 기록이 있는 2~5개 지역을 선택하며, `SUMMARY`와 `ALL`은 전체
활성 방문 기록을 사용한다. 생성 요청은 동기로 처리되고 완료되면 Markdown 상세
화면으로 이동한다. 보관함에서는 전체 또는 Report 유형별로 목록을 조회할 수 있으며,
삭제 확인 후 Storage의 Markdown과 Report 메타데이터를 함께 삭제한다.

## 검증

```bash
npm run lint
npm run test
npm run build
npm run test:e2e:install
npm run test:e2e
```

`test:e2e:install`은 최초 한 번 또는 Playwright 버전이 바뀐 뒤 Chromium을 설치할 때
사용한다. E2E는 Backend를 직접 호출하지 않고 고정된 API 응답을 사용해 Desktop Chrome과
Pixel 7 화면에서 주요 사용자 흐름, 가로 넘침과 심각한 WCAG 접근성 위반을 검사한다.
실패 시 `playwright-report/`에서 HTML 결과를 확인할 수 있다.

## Production 배포

Firebase Hosting은 `/api/**` 요청을 `asia-northeast1`의 Cloud Run
`town-ai-api` 서비스로 전달한다. 정적 파일 배포 전에 기존 Town AI GCP Project에
Firebase를 활성화하고 `.firebaserc`의 Project ID를 확인한다.

```bash
npm run hosting:serve
npm run hosting:preview
npm run hosting:deploy
```

Firebase Hosting과 Preview URL은 공개되며 `/api`는 실제 Production Backend로 연결된다.
따라서 Web 관리 API 인증과 단일 사용자 권한 제한을 적용하기 전에는 Preview 및 Live
배포를 실행하지 않는다. 활성화, IAM과 Developer Connect Trigger 설정은
`../docs/013-firebase-hosting-deployment.md`를 따른다.
