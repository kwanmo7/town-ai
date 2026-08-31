# 프로젝트 구조

이 문서는 V1 완료 시점의 저장소 구조와 각 디렉터리의 책임을 설명한다. 파일 목록을
단순히 나열하는 대신, 기능을 수정할 때 어느 계층과 문서를 함께 확인해야 하는지에
초점을 둔다. 실제 의존성 버전은 `backend/app/build.gradle`과
`frontend/package.json`, Runtime 설정은 `backend/app/src/main/resources/application.yml`을
최종 기준으로 삼는다.

## 1. 최상위 구조

```text
town-ai/
├── .github/workflows/ci.yml       # Backend·Frontend·E2E CI
├── backend/                       # Spring Boot API와 운영 Script
│   ├── app/                       # Gradle Application Module
│   ├── scripts/                   # Local 실행·Seed·Production 운영 Script
│   ├── Dockerfile                 # Cloud Run용 Backend Image
│   └── README.md                  # Backend 실행·운영 안내
├── frontend/                      # React Web Application
│   ├── src/                       # 화면, 공통 Component, API·Firebase Client
│   ├── e2e/                       # Playwright Desktop·Mobile E2E
│   ├── firebase.json              # Hosting Rewrite·Cache 정책
│   ├── cloudbuild.production.yaml # main Push Hosting 배포
│   └── cloudbuild.preview.yaml    # 필요할 때 수동 Preview 배포
├── docs/                          # 설계·운영·전환·검증 문서
├── linebotdesign/                 # LINE Message와 Rich Menu 기준 Asset
├── docker/                        # 향후 공통 Docker 자산을 위한 예약 경로
├── README.md                      # GitHub 기본 노출 한국어 안내
├── README.en.md                   # English guide
└── README.ja.md                   # 日本語 guide
```

`build/`, `.gradle/`, `node_modules/`, `dist/`, Playwright 결과와 Local Emulator
데이터는 생성물이며 Git 관리 대상이 아니다. Secret, Firebase 허용 UID, LINE Token과
Production Data도 저장소에 커밋하지 않는다.

## 2. Backend

### 2.1 Module과 공통 설정

```text
backend/
├── app/
│   ├── build.gradle
│   └── src/
│       ├── main/java/com/townai/
│       ├── main/resources/
│       └── test/java/com/townai/
├── scripts/
├── Dockerfile
├── settings.gradle
├── gradlew / gradlew.bat
└── README.md
```

- `backend/app`만 실행 가능한 Spring Boot Module이다.
- Root Gradle Project는 Wrapper, Module 구성과 Java Toolchain Resolver를 관리한다.
- `application.yml`은 기본값과 환경변수 Mapping을 정의하며 Secret 값 자체를 갖지 않는다.
- Production은 `backend/Dockerfile`로 Image를 만들고 Cloud Run에서 실행한다.
- Backend CD는 저장소 파일이 아닌 Developer Connect Cloud Build Trigger의
  인라인 구성을 사용한다.
- `docker/` 최상위 디렉터리는 현재 Runtime Image의 Source가 아니다.

### 2.2 Java Package 책임

| Package | 책임 |
|---|---|
| `area` | Area CRUD, Soft Delete, Firestore Area Repository |
| `visit` | Visit CRUD, AI Draft 요청·검증, Firestore Visit Repository |
| `statistics` | 전체·Area별 평균과 평가 항목별 Top 5 집계 |
| `report` | Report 데이터 조립, AI 생성, 재사용 판단, Markdown 검증·저장·서명 링크 |
| `line` | Webhook 수신, Cloud Tasks Dispatch, Draft·수정·저장, Flex Message와 Report 조회 |
| `auth` | Firebase Admin 초기화, ID Token 검증, 단일 허용 UID 접근 제어 |
| `persistence.firestore` | Firestore Client 설정, Collection 이름, ID Counter와 Transaction 공통 기능 |
| `common.error` | API 오류 코드, 예외, 공통 응답 처리 |
| `common.openai` | OpenAI Responses API Client와 공통 설정 |
| `common.config` | Clock 등 Application 공통 Bean |

기능 Package는 가능한 한 다음 방향으로 의존한다.

```text
Controller
  → Service Interface / Service Implementation
    → Repository Interface
      → Firestore Repository Implementation
```

외부 시스템 연결은 `OpenAi*Client`, `LineMessagingApiClient`, `GcsReportStorage`,
`CloudTasksLineEventDispatcher`처럼 Adapter 역할이 드러나는 이름으로 분리한다. Controller나
Message Factory에서 Firestore SDK를 직접 호출하지 않는다.

### 2.3 주요 Backend 흐름

#### Web 관리 요청

```text
FirebaseAuthenticationFilter
  → Firebase ID Token·허용 UID 검증
  → Area / Visit / Statistics / Report Controller
  → Service
  → Firestore 또는 GCS
```

`/actuator/health/liveness`, `/actuator/health/readiness`, LINE Webhook, Cloud Tasks 내부
Endpoint와 서명된 Public Report Endpoint는 각각 별도의 보안 정책을 적용한다.

#### LINE 요청

```text
LineWebhookController
  → Signature 검증·지원 Event 선별·lineWebhookEvents 저장
  → Local 또는 Cloud Tasks Dispatcher
  → LineEventTaskController
  → OIDC 검증·Event Claim
  → Draft / Save / Report Service
  → LINE Push Message
```

Webhook 응답에서 OpenAI나 Report 생성을 기다리지 않는다. Firestore에 Event를 먼저
기록한 뒤 비동기 Task가 처리하며, 동일 Event ID의 중복 처리를 방지한다.

#### Report 생성

```text
ReportService
  → ReportDataAssembler
  → LINE 요청이면 ReportReuseService
  → ReportContentGenerator / OpenAI
  → Markdown Validator
  → GCS ReportStorage
  → Firestore Report Metadata
```

AREA·COMPARE·SUMMARY·ALL마다 입력 데이터 Fingerprint를 계산한다. LINE 조회는 원본
데이터가 변하지 않았다면 기존 Report를 재사용한다. Web의 명시적 생성 요청은 매번 새
Report를 만들며, Metadata 삭제 시 연결된 GCS 객체도 함께 정리한다.

### 2.4 Resource와 Prompt

```text
backend/app/src/main/resources/
├── application.yml
└── prompts/
    ├── visit-parser/v1/
    ├── area/v1/
    ├── compare/v1/
    ├── summary/v1/
    └── all/v1/
```

Prompt Version은 Application Version과 독립적이다. V1의 호환 가능한 버그 수정은
각 `v1` 경로 안에서 관리하며, 입력·출력 계약이 바뀌는 경우에만 새 Prompt Version을
추가한다. Structured Output을 사용하는 유형은 `output-schema.json`도 함께 관리한다.

### 2.5 Script

| Script | 역할 |
|---|---|
| `local-firestore-start.ps1` | Firestore Emulator Container 시작 |
| `local-backend-firestore.ps1` | Local Firestore 설정으로 Backend 실행 |
| `local-seed.ps1` | Local Area·Visit·Report Seed 생성 |
| `local-restore-seed.ps1` | Local Seed 상태 복원 |
| `production-restore-gcs-report-data.ps1` | GCS Markdown을 바탕으로 Area·Visit 기본 데이터 복원 |
| `production-restore-gcs-report-metadata.ps1` | 기존 GCS Report Metadata 복원 |
| `production-cleanup-orphan-reports.ps1` | Firestore가 참조하지 않는 GCS Report 후보 점검·삭제 |
| `test-report-orphan-cleanup.ps1` | Orphan Cleanup Script의 경계 조건 검증 |

Production Script는 명시된 Project·Database·Bucket을 다시 확인하고, 삭제 작업은 기본
Dry Run과 추가 확인 절차를 유지한다.

## 3. Frontend

```text
frontend/
├── src/
│   ├── app/             # Router와 최상위 Application
│   ├── components/
│   │   ├── common/      # Async 상태, Header, Score 표시
│   │   └── layout/      # Desktop Sidebar·Mobile Navigation
│   ├── features/
│   │   ├── auth/
│   │   ├── dashboard/
│   │   ├── areas/
│   │   ├── visits/
│   │   ├── statistics/
│   │   └── reports/
│   ├── hooks/           # 공통 비동기 상태 Hook
│   ├── lib/             # API Client, Firebase, Format 함수
│   ├── styles/          # 전역 Design Token과 반응형 Style
│   ├── test/            # Vitest 공통 Setup
│   └── types/           # Backend API Type
├── e2e/                 # Playwright Flow·접근성·반응형 검사
├── firebase.json
├── vite.config.ts
├── playwright.config.ts
├── package.json
└── README.md
```

`features`는 화면 단위의 Component와 Dialog를 소유하고, HTTP와 인증 세부 구현은
`lib/api.ts`, `lib/firebase.ts`, `features/auth`에 모은다. Backend DTO가 변경되면
`types/api.ts`, API Client, 해당 Feature Test와 E2E Mock을 함께 갱신한다.

현재 Route는 다음과 같다.

| Route | 화면 |
|---|---|
| `/` | Dashboard |
| `/areas` | Area 관리 |
| `/visits` | Visit 관리 |
| `/statistics` | 전체·Area별 통계 |
| `/reports` | Report 목록·생성 |
| `/reports/:reportId` | Markdown Report 상세 |

Firebase Hosting은 SPA Fallback을 적용하고 `/api/**`를 Cloud Run으로 Rewrite한다.
Production 배포는 `main` Push용 Cloud Build Trigger가 담당한다. Preview 설정 파일은
필요한 경우 수동 검증에만 사용하며 PR마다 자동 Preview를 만들지 않는다.

## 4. 문서 구조와 기준

| 문서 | 상태 | 역할 |
|---|---|---|
| `000-project-structure.md` | 현행 | 저장소·Package·실행 흐름 안내 |
| `001-requirements.md` | 현행 | V1 범위와 완료 조건 |
| `002-system-architecture.md` | 현행 | System 구성과 주요 처리 흐름 |
| `003-firestore-data-model.md` | 현행 | Firestore Collection·Document 모델 |
| `004-api-design.md` | 현행 | Web·LINE·Internal·Public API 계약 |
| `005-ai-prompt-design.md` | 현행 | Visit Parser와 Report Prompt 계약 |
| `006-deployment-operations.md` | 현행 | GCP·Firebase 배포와 운영 설정 |
| `007-local-firestore-emulator.md` | 현행 | Emulator 기반 Local 검증 |
| `008-ai-report-prompt-quality-evaluation.md` | 현행 | 실제 AI 품질 평가 방법과 결과 |
| `legacy/009-legacy-local-mysql-api-validation.md` | Legacy | Firestore 이전 Local MySQL 검증 기록 |
| `legacy/010-legacy-cloud-sql-production-validation.md` | Legacy | Firestore 이전 Cloud SQL 검증 기록 |
| `011-line-bot-ux-design.md` | 현행 | LINE UX·Message·Rich Menu 설계 |
| `012-frontend-design.md` | 현행 | Web 정보 구조·Component·상태 설계 |
| `013-firebase-hosting-web-auth.md` | 현행 | Firebase 인증·Hosting 보안 설계 |
| `014-firestore-migration-design.md` | 전환 기록 | Cloud SQL에서 Firestore로 전환한 설계와 절차 |
| `015-firestore-production-cutover-validation.md` | 검증 기록 | Production 전환과 회귀 검증 결과 |
| `999-v1-completion-v2-backlog.md` | 현행 | V1 완료 내역, 결정 기록, V2 후보 |
| `architecture/` | 보조 자료 | 현행 구성을 보조하는 Draw.io 원본과 PNG |
| `legacy/` | Archive | Firestore 이전 MySQL·Cloud SQL 검증 기록 |

Legacy 문서는 현재 실행 방법으로 사용하지 않는다. 현재 데이터 구조는
`003-firestore-data-model.md`, 운영 구성은 `006-deployment-operations.md`와
`013-firebase-hosting-web-auth.md`, 실제 코드와 환경변수 Mapping은 `application.yml`을
우선한다. `014`와 `015`는 전환 과정과 당시 검증 결과를 보존하는 기록이다.
과거 MySQL SQL·PNG·XLSX 산출물은 현재 Tree에서 제거했으며 필요한 경우 Git 이력에서
확인한다.

## 5. 기능 변경 시 함께 수정할 위치

| 변경 | Code | 함께 확인할 문서·Test |
|---|---|---|
| Area·Visit Field 또는 규칙 | Backend Entity·DTO·Repository, Frontend Type·Feature | `003-firestore-data-model.md`, `004-api-design.md`, Unit·E2E |
| API Endpoint·응답 | Controller·DTO, Frontend API Client | `004-api-design.md`, Frontend Mock·E2E |
| Prompt 입력·출력 | Prompt Resource, AI Client·Validator | `005-ai-prompt-design.md`, `008-ai-report-prompt-quality-evaluation.md`, Prompt Eval |
| Report 경로·보관 정책 | Report Storage·Persistence·Cleanup Script | `003-firestore-data-model.md`, `004-api-design.md`, `006-deployment-operations.md`, Script Test |
| LINE 동작·버튼 | LINE Service·Message Factory, `linebotdesign` JSON | `002-system-architecture.md`, `004-api-design.md`, `011-line-bot-ux-design.md` |
| 인증·권한 | Backend Auth, Frontend Auth, Firebase 설정 | `006-deployment-operations.md`, `012-frontend-design.md`, `013-firebase-hosting-web-auth.md` |
| 배포 Runtime·환경변수 | Cloud Build, Dockerfile, `application.yml` | `006-deployment-operations.md`, `013-firebase-hosting-web-auth.md`, `015-firestore-production-cutover-validation.md` |

문서는 구현 전 설계와 구현 후 검증 기록을 함께 담되, 완료된 전환 계획과 현재 운영
기준을 구분한다. 현재 동작과 문서가 다르면 먼저 Code·배포 설정을 확인하고 해당 문서의
상태와 기준일을 함께 갱신한다.
