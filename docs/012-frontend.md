# Frontend 설계 및 구현

## 1. 목적

Town AI Frontend는 Area, Visit, Statistics와 AI Report를 PC와 모바일 브라우저에서
관리하는 개인용 React Client다. LINE Bot이 빠른 현장 입력과 Report 수신을 담당하고,
Frontend는 누적 데이터를 자세히 조회·수정하는 관리 화면을 담당한다.

## 2. 기술 구성

| 구분 | 기술 |
| --- | --- |
| UI | React 19, TypeScript |
| Build | Vite 6 |
| Routing | React Router 7 |
| Markdown | react-markdown + remark-gfm |
| Test | Vitest, Testing Library, Playwright, axe-core |
| Static Hosting | Firebase Hosting |
| Backend | Spring Boot Cloud Run |

개발 PC의 Node.js 20.14에서 실행할 수 있도록 Vite 6 계열을 사용한다. 의존성은
`package-lock.json`으로 고정하고 CI는 `npm ci`를 사용한다.

## 3. 연결 구조

```text
Local Browser
→ Vite Development Server /api
→ Vite Proxy
→ http://localhost:8080

Production Browser
→ Firebase Hosting /api
→ Hosting Cloud Run Rewrite
→ asia-northeast1 / town-ai-api
```

Frontend의 API 호출은 환경과 관계없이 상대 경로 `/api`를 사용한다. Production에서
Firebase Hosting Rewrite를 사용하므로 Browser에 Cloud Run 주소를 하드 코딩하지
않고 별도의 CORS 허용 범위도 만들지 않는다.

Local Proxy 대상은 기본적으로 `http://localhost:8080`이며 `.env.local`의
`VITE_API_PROXY_TARGET`으로 변경할 수 있다. `.env.local`은 Git에 포함하지 않는다.

## 4. 화면 구조

| Route | 화면 | 현재 범위 |
| --- | --- | --- |
| `/` | Dashboard | 전체 건수, 평균 점수, 최근 Visit·Report |
| `/areas` | Area 관리 | 활성 Area 조회·등록·전체 수정·Soft Delete |
| `/visits` | Visit 관리 | 자연어 Draft 등록·전체 수정·Hard Delete |
| `/statistics` | Statistics | 전체 평균·항목별 Top 5·Area별 평균 |
| `/reports` | Report 목록·생성 | 유형·대상 선택, 동기 생성, 유형·생성 시각·Model 조회 |
| `/reports/{id}` | Report 상세 | Markdown 본문 보기·다운로드 |

Desktop은 고정 Sidebar, Mobile은 하단 Navigation을 사용한다. 모든 조회 화면은
Loading, Error, Empty 상태를 구분하고 API 오류의 사용자용 `message`를 표시한다.

## 5. 디렉터리 원칙

```text
src/
├── app/          Router와 Application 진입점
├── components/   공통 UI와 Layout
├── features/     Area, Visit, Report 등 기능별 화면
├── hooks/        공통 React Hook
├── lib/          API Client와 표시 형식
├── styles/       전역 Design Token과 반응형 Style
├── test/         공통 Test 설정
└── types/        Backend API Type
```

API 응답 Type은 Backend DTO와 같은 필드명을 사용한다. 화면 표시용 한글 이름과 날짜
변환은 `lib`에서 관리하며, API Client는 HTTP 오류를 `ApiError`로 정규화한다.

## 6. 구현 순서

1. 조회 중심 공통 Layout과 Backend 연결
2. Area CRUD Form 완료 후 Visit CRUD Form
3. 자연어 Visit Draft 입력과 확인
4. Report 생성·필터·삭제
5. Statistics 상세와 Area별 통계
6. Firebase Preview·Production 배포

등록·수정 Form은 Backend의 PUT 전체 교체 정책과 필드별 Validation을 그대로 따른다.
Report 생성은 V1의 동기 API 특성상 중복 제출 방지, 긴 Loading 상태와 오류 복구를
명확하게 제공해야 한다.

Area Form은 등록과 수정에 같은 입력 Component를 사용한다. 필수값과 길이를
Client에서 먼저 검사하고 Backend의 공통 `errors` 응답도 입력 필드 아래에 표시한다.
삭제 전에는 기존 Visit이 유지되는 Soft Delete 정책을 확인 Dialog로 안내한다.
보존된 Visit은 삭제된 Area를 복구하거나 별도의 이력 관리 기능을 구현하기 전까지
Dashboard 통계와 일반 Visit 목록에서 모두 제외한다.

Visit 등록은 LINE과 동일하게 자연어를 AI Draft로 변환한 뒤 사용자가 확인해야만
저장한다. Web에서는 다섯 점수를 Select Box로 직접 선택하고 Backend의
`selectedScores` 입력으로 전달한다. 선택 점수는 AI 출력보다 우선하며, 자연어에서는
주로 Area·방문일·memo를 추출한다. 확인 화면에서 기존 Area 연결 또는 신규 Area의
위치, 방문일, 점수와 memo를 수정할 수 있다. 신규 Area이면 Area 저장 후 반환된 ID로
Visit을 저장하며, Area 저장 뒤 Visit만 실패한 경우 재시도에서 Area를 중복 생성하지
않는다. 기존 Visit 수정은 상세 API에서 현재 메모까지 불러온 후 활성 Area, 방문일,
다섯 점수와 메모를 PUT 전체 교체 방식으로 저장한다. 삭제 전에는 Hard Delete와 기존
Report 비변경 정책을 확인 Dialog로 안내하고, 완료 후 현재 Visit 목록을 다시 조회한다.

Report 생성 Dialog는 최신 Area와 Visit을 별도로 조회하고 방문 기록이 있는 활성
Area만 `AREA`와 `COMPARE` 대상으로 선택할 수 있게 한다. `AREA`는 1개,
`COMPARE`는 선택 순서를 보존한 2~5개 Area ID를 전송한다. `SUMMARY`와 `ALL`은
Backend 계약에 따라 `areaIds` 필드 자체를 생략한다. 활성 Visit이 0건이면 모든 생성
유형을 차단하고 방문 기록 등록을 안내한다.

생성 중에는 Dialog 닫기와 중복 제출을 막고 유형별 진행 문구를 표시한다. 완료 후에는
생성된 Report 상세 Route로 바로 이동한다. REST 생성 API는 사용자의 명시적인 새 생성
요청이므로 기존 Report를 재사용하지 않으며, 화면에도 호출마다 새 Report 저장 및 API
비용 발생 가능성을 안내한다. 기존 Report 재사용은 LINE 조회 흐름에만 적용한다.

Report 상세 화면은 GFM 확장을 적용해 Backend가 생성한 점수 표를 HTML Table로
렌더링한다. 열이 많은 비교 표는 모바일 화면을 넘치거나 본문처럼 무너지지 않도록 표
영역만 가로 스크롤한다.

Report 목록은 전체와 `AREA`, `COMPARE`, `SUMMARY`, `ALL` 유형 필터를 제공하고,
화면 진입 시 조회한 전체 목록을 Frontend에서 즉시 필터링한다. 개인용 V1에서 예상하는
수십 건 규모에는 별도의 페이지네이션이나 필터별 반복 요청을 사용하지 않는다. Backend의
`reportType` Query Parameter는 API 계약으로 유지한다. 각 Report 카드는 삭제 확인
Dialog를 제공한다. 삭제가 확정되면 Storage의 Markdown과 DB 메타데이터를 삭제하는
Backend API를 호출하고 전체 목록을 다시 조회한다. 삭제 실패 시 Dialog를 유지해 오류를
확인하고 다시 시도할 수 있게 한다.

Statistics 화면은 전체 Visit 평균과 Area별 평균 Top 5를 다섯 평가 항목별로 표시한다.
Top 5의 Area 또는 Select Box에서 활성 Area를 선택하면
`GET /api/areas/{areaId}/statistics`를 호출해 방문 횟수와 누적 평균을 표시한다. 빠르게
대상을 바꾸더라도 마지막으로 선택한 Area 응답만 화면에 반영한다.

## 7. 검증 명령

```bash
npm run lint
npm run test
npm run build
npm run test:e2e:install
npm run test:e2e
```

GitHub Actions의 기존 Required Check 안에서 Backend 검증 후 Frontend 의존성 설치,
Lint, Unit Test, Production Build와 Chromium E2E를 실행한다. CI에서는 Playwright가
필요한 Chromium과 Linux 시스템 의존성을 함께 설치한다.

E2E는 실제 Backend나 Production 데이터를 변경하지 않도록 Browser Route에서 API 응답을
격리한다. Desktop Chrome과 Pixel 7 환경에서 Dashboard, Area, Visit, Statistics, Report
목록과 상세 화면을 열어 다음 항목을 확인한다.

- WCAG 2.0·2.1 A/AA 규칙 중 `serious`, `critical` 접근성 위반 없음
- 문서 전체의 의도하지 않은 가로 넘침 없음
- Desktop Sidebar와 Mobile Bottom Navigation 전환
- Visit 전체 수정과 Hard Delete
- Report 즉시 유형 필터와 삭제
- Top 5에서 Area별 누적 통계 조회

실패한 실행의 Screenshot과 첫 재시도 Trace는 `test-results/`, HTML Report는
`playwright-report/`에 생성하며 두 경로는 Git에서 제외한다.

## 8. Firebase Hosting 배포

Vite가 생성하는 `dist/`를 Firebase Hosting에 배포한다. `/assets/**`에는 1년 Immutable
Cache를 적용하고 `index.html`은 항상 새 배포를 확인하도록 Cache하지 않는다. `/api/**`는
`asia-northeast1`의 Cloud Run `town-ai-api`로 Rewrite하고, 그 외 경로는 React Router를
위해 `index.html`로 Rewrite한다.

```bash
npm run hosting:serve
npm run hosting:preview
npm run hosting:deploy
```

배포 CLI는 Frontend Package 의존성과 분리하고 Script에서 검증한 Firebase CLI 버전을
고정해 실행한다. 자동 배포는 Developer Connect의 Pull Request·main Push Trigger가 각각
`cloudbuild.preview.yaml`, `cloudbuild.production.yaml`을 사용한다. 실제 활성화와 IAM,
검증 순서는 `013-firebase-hosting-deployment.md`를 따른다.

Hosting URL과 Preview URL은 공개되고 `/api`가 실제 운영 Backend로 연결된다. 현재처럼
Web 관리 API에 사용자 인증이 없으면 제3자가 데이터를 변경할 수 있으므로, 인증과 사용자
제한을 적용하기 전에는 Preview와 Live 배포를 실행하지 않는다.
