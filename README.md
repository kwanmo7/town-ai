# Town AI

[한국어](README.md) | [English](README.en.md) | [日本語](README.ja.md)

[![CI](https://github.com/kwanmo7/town-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/kwanmo7/town-ai/actions/workflows/ci.yml)

Town AI는 직접 방문한 지역의 경험을 기록하고, 누적된 점수와 메모를 바탕으로 지역을 비교하는 개인용 의사결정 지원 시스템입니다. LINE에서는 자연어로 빠르게 기록하고 리포트를 조회할 수 있으며, Web에서는 지역·방문 기록·통계·AI 리포트를 한곳에서 관리할 수 있습니다.

V1 구현과 Production 전환을 완료했으며, 현재 데이터 저장소는 Firestore Standard, 리포트 본문 저장소는 Google Cloud Storage입니다.

## 주요 기능

- 자연어와 선택 점수를 조합한 방문 기록 초안 생성 및 확인 후 저장
- Area 등록·조회·수정·Soft Delete
- Visit 등록·조회·수정·Hard Delete
- 전체 및 Area별 통계, 평가 항목별 Top 5 조회
- AREA·COMPARE·SUMMARY·ALL 유형의 AI 리포트 생성
- LINE 조회의 동일 데이터 기반 기존 리포트 재사용, Markdown 조회·다운로드·삭제
- LINE Flex Message 기반 등록·수정·리포트 조회 흐름
- Firebase Google 로그인과 단일 허용 UID 기반 Web 접근 제어
- LINE Webhook 서명, Cloud Tasks OIDC, 만료 서명 리포트 링크 적용
- GitHub Actions CI와 Cloud Build 기반 Backend·Frontend 자동 배포

## 시스템 구성

```text
Web Browser
  └─ Firebase Hosting
       ├─ React SPA
       └─ /api/** rewrite ─────────────┐
                                      ▼
LINE ── Webhook ── Cloud Tasks ── Cloud Run Backend
                                      ├─ Firestore: Area, Visit, Report metadata, LINE state
                                      ├─ Cloud Storage: Markdown report content
                                      └─ OpenAI API: visit parsing and report generation
```

Web 관리 API는 Firebase ID Token을 검증하며, LINE 비동기 작업은 전용 Service Account의 OIDC Token을 검증합니다. LINE에서 전달하는 리포트 링크는 제한된 기간 동안만 유효한 HMAC 서명을 사용합니다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Gradle |
| Frontend | React 19, TypeScript 5, Vite 6 |
| Data | Firestore Standard, Google Cloud Storage |
| AI | OpenAI Responses API |
| LINE | LINE Messaging API, Flex Message, Rich Menu |
| Auth | Firebase Authentication, Google Sign-In |
| Runtime | Cloud Run, Cloud Tasks, Firebase Hosting |
| CI/CD | GitHub Actions, Cloud Build, Developer Connect |

정확한 라이브러리 버전은 [Backend Build](backend/app/build.gradle)와 [Frontend Package](frontend/package.json)를 기준으로 합니다.

## 로컬 실행

필수 도구는 Java 25, Node.js 24.19.x와 PowerShell입니다. Production GCP 작업에는 Google Cloud CLI가 추가로 필요합니다. OpenAI·LINE 기능을 시험하려면 별도 환경변수 또는 Secret이 필요하지만, 기본 CRUD와 화면 확인은 Firestore Emulator로 실행할 수 있습니다.

### 1. Firestore Emulator와 Backend

```powershell
# 터미널 1
./backend/scripts/local-firestore-start.ps1

# 터미널 2
./backend/scripts/local-backend-firestore.ps1
```

테스트 데이터를 넣으려면 다음 스크립트를 별도 터미널에서 실행합니다.

```powershell
./backend/scripts/local-seed.ps1
```

### 2. Frontend

```powershell
cd frontend
npm install
npm run dev
```

Vite 개발 서버는 `/api` 요청을 로컬 Backend로 프록시합니다. 상세 설정과 인증 우회 조건은 [Backend 실행 안내](backend/README.md)와 [로컬 Firestore 검증 문서](docs/007-local-firestore-emulator.md)를 참고하세요.

## 검증

```powershell
# Backend
cd backend
./gradlew test javadoc

# Frontend
cd ../frontend
npm run lint
npm test
npm run build
npm run test:e2e
```

실제 OpenAI 호출을 사용하는 Prompt 평가는 기본 테스트에서 제외되며 별도 명령과 비용이 필요합니다. 운영 정리 스크립트는 기본적으로 Dry Run이며, 사용 방법은 [Backend README](backend/README.md)에 정리되어 있습니다.

## 저장소 구조

| 경로 | 역할 |
|---|---|
| `backend/` | Spring Boot API, Firestore Repository, LINE·AI·Report 처리, 운영 스크립트 |
| `frontend/` | React Web 관리 화면, Firebase 로그인, Unit·E2E 테스트 |
| `docs/` | 요구사항·아키텍처·데이터·API·배포·운영 설계서와 V1 이력 |
| `linebotdesign/` | LINE Flex Message·Rich Menu 기준 JSON과 이미지 |
| `docker/` | Docker 관련 확장을 위한 예약 디렉터리. 현재 실행 이미지는 `backend/Dockerfile` 사용 |

패키지별 역할과 문서 분류는 [프로젝트 구조](docs/000-project-structure.md)를 참고하세요.

## 문서

- [프로젝트 구조](docs/000-project-structure.md)
- [요구사항](docs/001-requirements.md)
- [아키텍처](docs/002-system-architecture.md)
- [Firestore 데이터 모델](docs/003-firestore-data-model.md)
- [API 설계](docs/004-api-design.md)
- [Prompt 설계](docs/005-ai-prompt-design.md)
- [배포 및 운영](docs/006-deployment-operations.md)
- [Local Firestore Emulator](docs/007-local-firestore-emulator.md)
- [AI Report Prompt 품질 평가](docs/008-ai-report-prompt-quality-evaluation.md)
- [LINE Bot UX 설계](docs/011-line-bot-ux-design.md)
- [Frontend 설계](docs/012-frontend-design.md)
- [Firebase Hosting·인증 설계](docs/013-firebase-hosting-web-auth.md)
- [Firestore 전환 설계](docs/014-firestore-migration-design.md)
- [Production 전환 검증](docs/015-firestore-production-cutover-validation.md)
- [V1 완료 내역과 V2 후보](docs/999-v1-completion-v2-backlog.md)

- [Legacy Local MySQL API 검증](docs/legacy/009-legacy-local-mysql-api-validation.md)과
  [Legacy Cloud SQL Production 검증](docs/legacy/010-legacy-cloud-sql-production-validation.md)은
  MySQL·Cloud SQL을 사용하던 시점의 기록입니다. 현재 운영 구성은
`docs/003-firestore-data-model.md`, `docs/006-deployment-operations.md`,
`docs/013-firebase-hosting-web-auth.md`를 기준으로 합니다.

## Production과 보안

Production Web은 [https://town-ai.web.app](https://town-ai.web.app)에서 제공되지만 개인용 시스템이므로 허용된 Google 계정만 관리 기능에 접근할 수 있습니다. Secret 값, Firebase 허용 UID, LINE Token과 운영 데이터는 저장소에 커밋하지 않습니다.

## 상태

V1의 Backend, Web, LINE, Firestore 전환과 Production 검증은 완료되었습니다. 이후 개선 후보와 의사결정 기록은 [V1 완료 기록 및 V2 백로그](docs/999-v1-completion-v2-backlog.md)에서 관리합니다.
