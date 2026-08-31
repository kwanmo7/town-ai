# Town AI

[한국어](README.md) | [English](README.en.md) | [日本語](README.ja.md)

[![CI](https://github.com/kwanmo7/town-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/kwanmo7/town-ai/actions/workflows/ci.yml)

Town AI is a personal decision-support system for recording neighborhood visits and comparing areas from first-hand scores and notes. LINE provides a conversational flow for quick input and report access, while the Web application manages areas, visits, statistics, and AI reports.

V1 and its production cutover are complete. Firestore Standard is the system of record, and generated Markdown reports are stored in Google Cloud Storage.

## Features

- Create a visit draft from natural language and optional score selections, then review it before saving
- Create, view, update, and soft-delete areas
- Create, view, update, and hard-delete visits
- View overall and per-area statistics and Top 5 rankings by score category
- Generate AREA, COMPARE, SUMMARY, and ALL AI reports
- Reuse unchanged reports in the LINE lookup flow; view, download, filter, and delete Markdown reports
- Register visits and retrieve reports through LINE Flex Messages and a Rich Menu
- Protect the Web application with Firebase Google Sign-In and a single allowed UID
- Verify LINE webhook signatures, Cloud Tasks OIDC tokens, and expiring signed report links
- Run CI with GitHub Actions and deploy the Backend and Frontend through Cloud Build

## Architecture

```text
Browser ── Firebase Hosting ── /api rewrite ──┐
                                             ▼
LINE ── Webhook ── Cloud Tasks ──────── Cloud Run Backend
                                             ├─ Firestore metadata and state
                                             ├─ Cloud Storage report content
                                             └─ OpenAI Responses API
```

The Web management APIs require a verified Firebase ID token. LINE background tasks require an OIDC token from the dedicated service account, and public LINE report URLs carry an expiring HMAC signature.

## Technology

| Layer | Stack |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Gradle |
| Frontend | React 19, TypeScript 5, Vite 6 |
| Data | Firestore Standard, Google Cloud Storage |
| AI | OpenAI Responses API |
| Messaging | LINE Messaging API, Flex Message, Rich Menu |
| Authentication | Firebase Authentication, Google Sign-In |
| Runtime | Cloud Run, Cloud Tasks, Firebase Hosting |
| CI/CD | GitHub Actions, Cloud Build, Developer Connect |

See [backend/app/build.gradle](backend/app/build.gradle) and [frontend/package.json](frontend/package.json) for exact dependency versions.

## Local development

Java 25, Node.js 24.19.x, and PowerShell are required. Google Cloud CLI is additionally required for production GCP operations. The core CRUD flows can run against the Firestore Emulator without production credentials.

```powershell
# Terminal 1: Firestore Emulator
./backend/scripts/local-firestore-start.ps1

# Terminal 2: Backend
./backend/scripts/local-backend-firestore.ps1

# Optional local data
./backend/scripts/local-seed.ps1

# Terminal 3: Frontend
cd frontend
npm install
npm run dev
```

The Vite development server proxies `/api` to the local Backend. See the [Backend guide](backend/README.md) and [local Firestore guide](docs/007-local-firestore-emulator.md) for details.

## Verification

```powershell
cd backend
./gradlew test javadoc

cd ../frontend
npm run lint
npm test
npm run build
npm run test:e2e
```

Live OpenAI prompt evaluations are separate from the default test suite and incur API usage. Production cleanup tools use Dry Run by default.

## Repository and documentation

- `backend/`: Spring Boot APIs, Firestore persistence, LINE/AI/report processing, and operations scripts
- `frontend/`: React administration UI, Firebase authentication, unit tests, and Playwright E2E tests
- `docs/`: requirements, architecture, API, deployment, migration, and verification documents
- `linebotdesign/`: source-of-truth LINE Flex Message and Rich Menu assets

Start with the [project structure](docs/000-project-structure.md), [requirements](docs/001-requirements.md), [architecture](docs/002-system-architecture.md), [API design](docs/004-api-design.md), and [deployment guide](docs/006-deployment-operations.md). The [local MySQL](docs/legacy/009-legacy-local-mysql-api-validation.md) and [Cloud SQL production](docs/legacy/010-legacy-cloud-sql-production-validation.md) validation documents are retained under `docs/legacy/` as historical records.

## Production and status

The production Web application is hosted at [https://town-ai.web.app](https://town-ai.web.app), but administration access is restricted to the owner's Google account. Secrets, allowed UIDs, LINE credentials, and production data must never be committed.

V1 is complete. Future candidates and decision records are tracked in [docs/999-v1-completion-v2-backlog.md](docs/999-v1-completion-v2-backlog.md).
