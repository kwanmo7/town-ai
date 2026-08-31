# Firestore 전환 설계

> 완료된 전환 설계: 이 문서는 Cloud SQL에서 Firestore로 이동할 때 적용한 목표 구조와
> 의사결정을 보존한다. 현재 운영 데이터 모델은 `003-firestore-data-model.md`, 실제 전환 결과는
> `015-firestore-production-cutover-validation.md`를 우선한다.

## 목차

1. 목적
2. 전환 배경
3. 설계 결정
4. 목표 구조
5. 데이터 모델과 저장 정책
6. Transaction과 일관성
7. 실행 환경
8. 보안과 권한
9. 데이터 전환 정책
10. 배포와 Rollback
11. 비용과 운영
12. 제약 사항
13. 관련 파일
14. 참고 문서

## 1. 목적

Town AI의 Source of Truth를 Cloud SQL MySQL에서 Cloud Firestore로 전환한다. REST API,
LINE Bot과 Report 기능의 외부 동작은 유지하면서 상시 Instance 비용과 관계형 Database
운영 부담을 제거하는 것이 목적이다.

이 문서는 목표 Architecture, 데이터 모델, 일관성·보안·전환 정책을 정의한다. 구현과
Production 전환의 완료 이력은 `999-v1-completion-v2-backlog.md`에서 관리한다.

## 2. 전환 배경

Town AI는 한 명이 사용하며 V1 데이터가 수십 건 수준이다. 사용하지 않는 시간이 길어도
Cloud SQL Instance는 계속 비용과 관리 대상을 만든다. Firestore는 요청량과 저장량 기반의
Serverless Database이므로 현재 사용 패턴에 더 적합하다.

전환은 Database 제품만 변경한다. 사용자 기능, API Path, Prompt Version, GCS Markdown
Report와 Firebase·LINE 인증 정책은 변경하지 않는다.

## 3. 설계 결정

| 항목 | 결정 |
| --- | --- |
| Product | Cloud Firestore Standard |
| Mode | Native mode |
| Database ID | `town-ai` |
| Location | `asia-northeast1` |
| Source of Truth | Firestore |
| Client 직접 접근 | 금지 |
| Backend 인증 | Cloud Run Runtime Service Account ADC |
| Firestore 최소 IAM | `roles/datastore.user` |
| Delete Protection | 활성화 |
| Local 환경 | Firestore Emulator |
| Report 본문 | 기존과 동일하게 GCS |
| 외부 API ID | `counters` Transaction으로 숫자 ID 유지 |
| Cloud SQL 제거 | Production 복원·배포 검증 후 완료, 실제 V1 적용은 9.3 참조 |

Database Location과 Edition은 생성 단계에서 확정한다. Application은 명명된 Database
`town-ai`를 명시적으로 선택하며 `(default)` Database에 의존하지 않는다.
Production Database는 Delete Protection을 활성화해 Console이나 CLI의 우발적 삭제를
막는다.

## 4. 목표 구조

```text
Controller
  → Service
    → Domain Repository Interface
      → Firestore Repository
        → FirestoreTransactionRunner
          → Google Cloud Firestore SDK
            → town-ai / town-ai Database

ReportStorage Interface
  → GcsReportStorage
    → gs://town_ai/reports/v1/**
```

Service와 Controller는 Google SDK를 직접 사용하지 않는다. Repository Interface를 유지해
업무 규칙과 저장 기술을 분리하며, Firestore 구현체만 SDK와 문서 구조를 안다.

다음 Runtime 의존성은 제거한다.

- Spring Data JPA와 Hibernate ORM
- Flyway Runtime Migration
- MySQL JDBC Driver와 Cloud SQL MySQL Socket Factory
- DB 접속 환경변수와 Local MySQL 초기화 Script

다음 외부 계약은 유지한다.

- REST API Path, Request와 Response 형식
- Area·Visit·Report의 숫자 ID
- Area Soft Delete와 Visit Hard Delete
- Report 재사용 Fingerprint
- GCS Markdown 객체와 경로
- LINE Webhook·Cloud Tasks·OIDC·Push 흐름
- Firebase Web 인증과 단일 UID 제한

## 5. 데이터 모델과 저장 정책

Firestore Collection과 문서 필드의 상세 정의는 `003-firestore-data-model.md`를 기준으로 한다.

| Collection | 역할 | Document ID |
| --- | --- | --- |
| `areas` | 지역과 Soft Delete 상태 | 숫자 Area ID 문자열 |
| `visits` | 방문 점수와 메모 | 숫자 Visit ID 문자열 |
| `reports` | Report Metadata와 대상 Area | 숫자 Report ID 문자열 |
| `lineVisitDrafts` | LINE 확인·수정 중 Draft | 숫자 Draft ID 문자열 |
| `lineWebhookEvents` | LINE Event 멱등 처리 상태 | `webhookEventId` |
| `counters` | Namespace별 마지막 숫자 ID | Namespace 이름 |
| `areaKeys` | Area 복합 중복 방지 | 정규화된 복합 Key Hash |

Firestore는 Schemaless이지만 Application은 같은 Collection에서 필드명과 자료형을
일관되게 유지한다. Repository의 직렬화·역직렬화와 Test가 Application Schema 역할을 한다.

Collection은 별도 DDL로 만들지 않는다. 첫 문서를 저장하면 자동으로 생성되고 모든 문서를
삭제하면 Console에서 사라진다. 따라서 Production의 빈 Database는 정상 상태이며,
Backend의 데이터 이전 또는 첫 등록 요청이 Collection을 생성한다.

### 5.1 참조와 삭제

- Visit은 `areaId`로 Area를 참조한다.
- Area를 Soft Delete해도 기존 Visit 문서는 보존한다.
- 일반 Visit 목록, 통계와 Report에서는 삭제된 Area의 Visit을 제외한다.
- Report의 `targetAreaIds` 배열은 대상 ID와 표시 순서를 함께 보존한다.
- Foreign Key와 Cascade가 없으므로 삭제·조회 규칙은 Service와 Repository가 책임진다.

### 5.2 시간과 ID

- `visitDate`는 시각 없는 `yyyy-MM-dd` 값이다.
- 감사·처리·만료 시각은 UTC Firestore Timestamp로 저장한다.
- API는 UTC ISO 8601로 직렬화하고 Frontend에서 사용자 시간대로 변환한다.
- 기존 API 호환을 위해 숫자 ID를 유지하며 Namespace별 Counter를 Transaction에서 증가시킨다.

## 6. Transaction과 일관성

`FirestoreTransactionRunner`는 같은 Thread의 중첩 Repository 호출이 하나의 Transaction을
공유하도록 한다. Firestore Transaction에서는 필요한 읽기를 첫 쓰기보다 먼저 수행하고,
다건 ID가 필요하면 Counter에서 연속 범위를 한 번에 예약한다.

### 6.1 Area 중복 방지

관계형 UNIQUE Constraint 대신 `(prefecture, city, name)`을 정규화한 `areaKeys` 예약 문서를
사용한다. 신규 Area 저장과 Key 예약은 같은 Transaction에서 처리한다. Soft Delete된 Area도
Key를 유지해 동일 Area의 의도하지 않은 중복 등록을 막는다.

LINE 신규 Area Draft를 확정할 때 동시에 같은 Area가 등록되면 먼저 생성된 활성 Area를
재조회해 Visit에 연결한다.

### 6.2 LINE 멱등성

`webhookEventId`를 Event 문서 ID와 결정적 Cloud Tasks 이름으로 사용한다. Event 상태,
처리 Lease, 재시도 횟수와 Draft 상태 변경을 Transaction으로 보호한다. 외부 LINE Push는
Event ID와 메시지 목적에서 만든 UUIDv5 Retry Key로 중복을 줄인다.

### 6.3 Report와 GCS

Report Metadata는 Firestore, Markdown 본문은 GCS에 저장되므로 두 시스템을 묶는 분산
Transaction은 없다. 임시 Metadata로 ID를 선점한 뒤 GCS 저장과 Metadata 확정을 수행하고,
실패하면 임시 문서와 객체를 Best-effort로 정리한다. Process 종료 시 남을 수 있는 고아
객체는 운영 점검 또는 별도 정리 방식으로 보완한다.

## 7. 실행 환경

### 7.1 Local

```text
FIRESTORE_PROJECT_ID=demo-town-ai
FIRESTORE_DATABASE_ID=town-ai
FIRESTORE_EMULATOR_HOST=127.0.0.1:8081
```

Local은 Firebase Emulator Suite의 명명된 `town-ai` Database를 사용한다. 초기화·Seed
Script가 Area 3개와 Visit 5개를 복원하고 Counter와 `areaKeys`도 함께 구성한다.
Emulator는 Production Credential이나 실제 GCP 데이터를 사용하지 않는다.

### 7.2 Production

```text
FIRESTORE_PROJECT_ID=town-ai
FIRESTORE_DATABASE_ID=town-ai
```

Production에는 `FIRESTORE_EMULATOR_HOST`를 설정하지 않는다. Cloud Run Runtime Service
Account의 ADC로 `town-ai` Project의 명명된 `town-ai` Database에 접근한다.

`FIREBASE_PROJECT_ID`와 Project 값은 같지만, 저장소 선택 의도를 명확히 하기 위해
`FIRESTORE_PROJECT_ID`와 `FIRESTORE_DATABASE_ID`를 별도로 설정한다.

## 8. 보안과 권한

### 8.1 Client 접근

`backend/firestore.rules`는 Browser와 Mobile SDK의 직접 읽기·쓰기를 모두 거부한다.

```text
allow read, write: if false;
```

Server SDK는 Security Rules 대신 IAM을 사용한다. Web 사용자는 Firebase ID Token으로
Backend에 인증하며 Firestore Credential을 받지 않는다.

### 8.2 Runtime IAM

Cloud Run은 전용 계정 `town-ai-runtime@town-ai.iam.gserviceaccount.com`을 사용한다.
필요한 권한은 Resource 범위별로 다음과 같이 제한한다.

| Resource | 역할 | 목적 |
| --- | --- | --- |
| Project `town-ai` | `roles/datastore.user` | Firestore 문서 읽기·쓰기·Transaction |
| Project `town-ai` | `roles/cloudtasks.enqueuer` | LINE Event Task 생성 |
| Project `town-ai` | `roles/logging.logWriter` | Application Log 기록 |
| Bucket `gs://town_ai` | `roles/storage.objectUser` | Report 객체 저장·조회·삭제 |
| 사용 중인 Secret 6개 | `roles/secretmanager.secretAccessor` | Runtime Secret Version 읽기 |
| `town-ai-runtime` Service Account | `roles/iam.serviceAccountUser` | 같은 계정으로 Cloud Tasks OIDC Token 지정 |

새 Revision은 `LINE_CLOUD_TASKS_SERVICE_ACCOUNT`에도 `town-ai-runtime`을 사용한다. Task를
생성하는 호출자에게 OIDC Service Account의 `iam.serviceAccounts.actAs` 권한이 필요하므로
계정 자체에 `roles/iam.serviceAccountUser`를 부여한다.

Firestore 전환 후 사용하지 않는 `DB_PASSWORD` Secret 접근 권한은 새 Runtime 계정에
부여하지 않았으며, Cloud Run 참조 제거 후 Secret 자체도 삭제했다.

Build·배포 계정과 Runtime 계정을 분리하고 Owner, Editor, Firebase Admin 같은 광범위한
Project 역할을 Runtime 계정에 유지하지 않는다. Backend Build·배포는
`town-ai-backend-deployer`, Runtime은 `town-ai-runtime`을 사용하며 Service Account JSON
Key는 사용하지 않는다. 기존 기본 Compute 계정의 Project 역할은 회귀 검증 후 모두
제거했다.

## 9. 데이터 전환 정책

기본 전환에서는 Cloud SQL 원본을 Firestore Production 검증과 Rollback 관찰 기간이
끝날 때까지 보존한다. Dual-write는 구현하지 않으며 Cutover 시점에는 사용자 입력을 잠시
중지한다. 무료 평가 종료로 원본을 조회할 수 없는 실제 V1의 예외 결정은 9.3에 기록한다.

### 9.1 기존 데이터 보존

기존 데이터를 유지할 경우 다음 대상을 Export·정규화해 Firestore에 Import한다.

- 활성·삭제 Area와 모든 Visit
- Report Metadata와 대상 Area 관계
- 필요한 LINE Draft와 미완료 Event
- Namespace별 최대 숫자 ID

Import 후 Counter를 최대 ID로 맞추고 모든 Area의 `areaKeys`를 재구성한다. Collection별
문서 수, 핵심 필드, 통계와 Report 재사용 결과를 기존 Database와 대조한다.

### 9.2 수동 재등록

데이터가 매우 적다면 Area·Visit만 Web에서 다시 등록할 수 있다. 이 경우 기존 Report
Metadata와 LINE 처리 문서는 이전하지 않고 새로 생성한다. GCS의 기존 Markdown 객체는
별도 보존·정리 정책을 적용한다.

어느 방식을 사용하더라도 기존 Cloud SQL 데이터를 먼저 삭제하거나 덮어쓰지 않는다.

### 9.3 Production 적용 결과

Production의 Legacy Cloud SQL은 무료 평가 종료로 `SUSPENDED` 상태였고 보존 Backup이
없었다. 유료 전환 없이 GCS Markdown Report 9개를 기준으로 Area 3개와 Visit 3개를
복원하는 방식을 선택했다. `areaKeys`와 Area·Visit Counter는 복원 데이터에서 재구성했고
LINE 처리 상태는 새로 시작했다.

초기 Cutover에서는 Report Metadata를 제외했지만, GCS 파일명과 본문에서 기존 ID `2~10`,
유형, 생성 시각과 대상 Area를 정확히 확인할 수 있어 후속 복원했다. 생성 당시 모델
`gpt-5.4-mini`, 유형별 V1 Prompt Version과 `sourceFingerprint=null`을 기록하고 Report
Counter를 10으로 맞췄다. 이미 생성된 신규 Report ID 1과 충돌하지 않았으며 GCS 객체는
변경하지 않았다.

Firestore 필드값과 GCS 객체의 generation·Hash·크기·수정 시각을 검증한 후 Legacy Cloud
SQL Instance를 삭제했다. 실제 입력, 변환 규칙과 검증 결과는
`015-firestore-production-cutover-validation.md`에 기록한다.

## 10. 배포와 Rollback

일반적인 전환은 다음 상태 순서를 따른다.

```text
Firestore Database·Rules·Index 준비
  → Runtime IAM 준비
  → 기존 데이터 보존 방식 확정
  → Firestore Backend Revision 배포
  → Web·LINE 전체 회귀 검증
  → scale-to-zero 첫 요청 검증
  → 관찰 기간
  → Cloud SQL 최종 Export와 제거
```

실제 V1은 `SUSPENDED` 평가 인스턴스를 유료 전환하지 않고 GCS 기반 복원 검증 직후
삭제했으므로 이전 SQL Revision으로 Rollback할 수 없다. 이후 배포는
`015-firestore-production-cutover-validation.md`의 운영 전환 경계를 따른다.

새 Revision은 Cloud SQL 연결과 DB Secret 없이 시작할 수 있어야 한다. Traffic 전환 전
Startup, Liveness와 Readiness를 확인하고, 전환 후 Area·Visit·Statistics·Report 4종,
Firebase Web 인증, LINE Draft, Cloud Tasks OIDC와 서명 Report 링크를 검증한다.

Cloud SQL 삭제 후에는 이전 SQL Revision으로 Rollback하지 않는다. 배포 장애는 동일한
Firestore·GCS를 사용하는 직전 정상 Revision으로 Traffic을 되돌리고, 데이터 변경 작업
전에는 필요에 따라 Firestore Export를 수행한다.

## 11. 비용과 운영

Firestore는 저장량과 Document read/write/delete, Index 저장량 및 네트워크 사용량에 따라
과금된다. 개인용 V1의 수십 건 데이터에서는 작은 Collection을 읽어 Backend에서 필터·집계하는
현재 구조를 유지한다.

다음 조건이 생기면 Query, 복합 Index와 Pagination을 재설계한다.

- Collection당 수백 건 이상으로 증가
- 통계 조회 빈도 또는 Document Read 급증
- 무료 사용량이나 예산 알림에 근접
- Cloud Run 응답 시간 증가

운영 중에는 Permission 오류, Transaction 충돌, 처리 중 상태로 남은 LINE Event, Report
Metadata와 GCS 객체 불일치, Document Read 추이를 확인한다.

PITR과 예약 Backup은 별도 과금 기능이며 V1 운영의 필수 조건이 아니다. 현재는 Delete
Protection을 활성화하고 PITR·예약 Backup은 사용하지 않는다. 대량 수정·삭제나 재이전 전에
수동 Export를 검토하며, 데이터 중요도나 규모가 커질 때 예약 Backup을 다시 결정한다.

## 12. 제약 사항

- Firestore에는 Foreign Key와 Cascade가 없으므로 Backend가 참조 무결성을 책임진다.
- Schemaless 저장소이므로 문서 필드와 자료형의 호환성을 Repository Test로 보호한다.
- Area 복합 UNIQUE는 `areaKeys` 예약 문서에 의존한다.
- GCS와 Firestore를 묶는 완전한 분산 Transaction은 제공하지 않는다.
- LINE 외부 Push까지 포함한 완전한 Exactly-once 처리는 제공하지 않으며 멱등 상태와 Retry
  Key로 중복 가능성을 낮춘다.
- V1 집계는 작은 데이터 규모를 전제로 Collection을 읽어 Application에서 계산한다.

## 13. 관련 파일

| 파일 | 역할 |
| --- | --- |
| `backend/firebase.json` | 명명된 Database의 Rules·Index 배포 설정 |
| `backend/firestore.rules` | Client 직접 접근 거부 정책 |
| `backend/firestore.indexes.json` | 명시적 복합 Index 정의 |
| `backend/scripts/local-firestore-start.ps1` | Local Emulator 실행 |
| `backend/scripts/local-restore-seed.ps1` | Emulator 초기화와 Seed 복원 |
| `backend/scripts/production-restore-gcs-report-data.ps1` | GCS 기반 Production 데이터 복원·검증 |
| `backend/scripts/production-restore-gcs-report-metadata.ps1` | 기존 GCS Report Metadata 복원·검증 |
| `backend/app/src/main/resources/application.yml` | Local·Production Firestore 설정 |
| `docs/003-firestore-data-model.md` | Collection과 문서 필드의 기준 모델 |
| `docs/015-firestore-production-cutover-validation.md` | 실제 Production 전환 검증 기록 |
| `docs/999-v1-completion-v2-backlog.md` | V1 구현·배포 완료 이력과 V2 후보 |

## 14. 참고 문서

- [Firestore 데이터 모델](https://firebase.google.com/docs/firestore/data-model)
- [Firestore 가격](https://firebase.google.com/docs/firestore/pricing)
- [Server Client Library 시작](https://cloud.google.com/firestore/docs/create-database-server-client-library)
- [Firestore IAM](https://cloud.google.com/firestore/docs/security/iam)
- [Database 관리](https://cloud.google.com/firestore/docs/manage-databases)
- [Emulator 연결](https://firebase.google.com/docs/emulator-suite/connect_firestore)
