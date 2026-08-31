# Firestore 데이터 모델

## Version

- Town AI V1
- Cloud Firestore Standard, Native mode
- Production Database ID: `town-ai`
- Production Location: `asia-northeast1`

Town AI V1의 현행 Source of Truth는 Firestore이다. Cloud SQL 운영 당시의 MySQL
SQL·PNG·XLSX 산출물은 현재 Tree에서 제거했으며 필요한 경우 Git 이력에서 확인한다.
신규 데이터 구조 변경은 이 문서와 Firestore Repository를 기준으로 한다.

## 컬렉션 구조

```text
areas/{areaId}
visits/{visitId}
reports/{reportId}
lineWebhookEvents/{webhookEventId}
lineVisitDrafts/{draftId}
counters/{namespace}
areaKeys/{sha256(prefecture + NUL + city + NUL + name)}
```

### `areas`

| 필드 | 형식 | 설명 |
| --- | --- | --- |
| `id` | number | 기존 API와 호환되는 숫자 ID |
| `name` | string | 지역 표시명 |
| `prefecture` | string | 도도부현 |
| `city` | string | 시구정촌 |
| `station` | string/null | 대표 역 |
| `createdAt` | timestamp | 생성 시각 |
| `updatedAt` | timestamp | 최종 수정 시각 |
| `deletedAt` | timestamp/null | Soft Delete 시각 |

### `visits`

| 필드 | 형식 | 설명 |
| --- | --- | --- |
| `id` | number | 숫자 ID |
| `areaId` | number | `areas` 문서 ID |
| `visitDate` | string | `yyyy-MM-dd` 형식의 방문일 |
| `atmosphereScore` | number | 분위기 0~10 |
| `infraScore` | number | 생활 인프라 0~10 |
| `cleanScore` | number | 청결도 0~10 |
| `sizeScore` | number | 넓은 집 가능성 0~10 |
| `accessScore` | number | 접근성 0~10 |
| `memo` | string/null | 사용자 메모 |
| `createdAt` | timestamp | 생성 시각 |
| `updatedAt` | timestamp | 최종 수정 시각 |

### `reports`

| 필드 | 형식 | 설명 |
| --- | --- | --- |
| `id` | number | 숫자 ID |
| `reportType` | string | `AREA`, `COMPARE`, `SUMMARY`, `ALL` |
| `model` | string | 생성에 사용한 OpenAI 모델 |
| `promptVersion` | string | Prompt 식별자 |
| `sourceFingerprint` | string/null | 입력 데이터 SHA-256 지문 |
| `sourceWebhookEventId` | string/null | LINE 생성 멱등 키 |
| `storagePath` | string/null | GCS Markdown 객체 경로 |
| `targetAreaIds` | array<number> | 표시 순서를 보존한 대상 Area ID |
| `createdAt` | timestamp | 생성 시각 |
| `updatedAt` | timestamp | 최종 수정 시각 |

MySQL의 `report_area` 연결 테이블은 별도 컬렉션으로 만들지 않는다. 대상이 최대
5개이거나 전체 Area인 작은 개인 데이터이므로 Report 문서의 `targetAreaIds` 배열로
관계와 순서를 함께 보존한다.

### `lineWebhookEvents`

LINE Webhook 이벤트 ID를 문서 ID로 사용한다. 이벤트 유형·사용자·메시지·Postback,
처리 상태, 시도 횟수, Lease 시각, 실패 정보, 수정 대상 Draft ID 및 생성·수정 시각을
저장한다. 같은 이벤트 ID 저장은 문서 ID와 트랜잭션으로 멱등 처리한다.

| 필드 | 형식 | 설명 |
| --- | --- | --- |
| `webhookEventId` | string | LINE Event ID이자 Document ID |
| `lineUserId` | string | 허용 사용자 확인용 LINE User ID |
| `eventType` | string | `TEXT_MESSAGE`, `POSTBACK`, `FOLLOW` |
| `messageText` | string/null | Text Event의 입력 내용 |
| `postbackData` | string/null | Postback Event의 Command Data |
| `status` | string | `RECEIVED`, `PROCESSING`, `COMPLETED`, `FAILED` |
| `attemptCount` | number | Application 처리 시도 횟수 |
| `lastErrorCode` | string/null | 마지막 실패의 내부 분류 Code |
| `revisionSourceDraftId` | number/null | 부분 수정 입력이 참조하는 원본 Draft ID |
| `occurredAt` | timestamp | LINE Event 발생 시각 |
| `processingStartedAt` | timestamp/null | 현재 처리 Lease 시작 시각 |
| `processedAt` | timestamp/null | 완료·실패 확정 시각 |
| `createdAt` | timestamp | 문서 생성 시각 |
| `updatedAt` | timestamp | 마지막 상태 변경 시각 |

### `lineVisitDrafts`

LINE 자연어 Parser 결과와 Area 후보 Snapshot, 다섯 점수, 방문일, 메모, 경고,
상태, 만료 시각, 수정 이벤트 ID와 확정 Visit ID를 저장한다.

| 필드 | 형식 | 설명 |
| --- | --- | --- |
| `id` | number | 숫자 Draft ID이자 Document ID |
| `sourceWebhookEventId` | string | Draft를 생성한 원본 Event ID |
| `lineUserId` | string | Draft 소유자 |
| `areaId` | number/null | 기존 Area ID |
| `areaRegistrationRequired` | boolean | 저장 시 신규 Area 생성 필요 여부 |
| `areaName` | string/null | 확인 화면용 Area 이름 Snapshot |
| `areaPrefecture` | string/null | 확인 화면용 도도부현 Snapshot |
| `areaCity` | string/null | 확인 화면용 시구정촌 Snapshot |
| `areaStation` | string/null | 확인 화면용 대표 역 Snapshot |
| `visitDate` | string/null | `yyyy-MM-dd` 방문일 |
| `atmosphereScore` 외 4개 | number/null | 파싱된 다섯 평가 점수 |
| `memo` | string/null | 파싱된 메모 |
| `warnings` | array<string> | 누락·확인 필요 내용 |
| `status` | string | `NEEDS_INPUT`, `AWAITING_CONFIRMATION`, `AWAITING_REVISION`, `REVISION_PROCESSING`, `SUPERSEDED`, `CONFIRMED`, `CANCELLED`, `EXPIRED` |
| `expiresAt` | timestamp | 확인 가능 만료 시각 |
| `revisionWebhookEventId` | string/null | Draft를 만든 마지막 수정 Event ID |
| `confirmedVisitId` | number/null | 확정 저장된 Visit ID |
| `createdAt` | timestamp | 생성 시각 |
| `updatedAt` | timestamp | 마지막 상태 변경 시각 |

### `counters`

기존 REST API와 LINE Postback의 숫자 ID 호환성을 유지하기 위해 다음 Namespace별
`lastId`를 저장한다.

- `area`
- `visit`
- `report`
- `lineVisitDraft`

ID는 Firestore 트랜잭션으로 예약한다. 다건 생성은 모든 읽기가 쓰기보다 먼저
실행되도록 필요한 연속 범위를 한 번에 예약한다.

각 Counter 문서에는 마지막으로 발급한 숫자 `lastId`만 저장한다. 복원 작업은 현재
문서의 최대 ID보다 Counter를 낮추지 않는다.

### `areaKeys`

Firestore에는 관계형 DB의 복합 UNIQUE 제약이 없으므로 정규화된
`(prefecture, city, name)`을 SHA-256으로 계산한 문서 ID를 사용한다. Area 저장
트랜잭션이 Key 문서와 Area 문서를 함께 처리해 동시 중복 생성을 막는다.
문서에는 예약 대상 숫자 `areaId`만 저장하며 Area 이름이나 위치가 바뀌면 이전 Key를
삭제하고 새 Key를 같은 트랜잭션에서 예약한다.

## 관계와 삭제 정책

- Visit은 `areaId`로 Area를 참조한다.
- Area는 Soft Delete하고 기존 Visit 문서는 보존한다.
- 삭제된 Area의 Visit은 일반 목록·통계·Report 입력에서 제외한다.
- Visit과 Report는 사용자 요청 시 Hard Delete한다.
- Report Markdown 본문은 GCS에 저장하고 Firestore에는 경로만 저장한다.
- Report 삭제는 GCS 객체 삭제 후 Firestore Metadata를 삭제한다.
- 30일이 지난 LINE Draft와 더 이상 참조되지 않는 완료·실패 이벤트는 기회적으로 정리한다.

Firestore에는 Foreign Key가 없으므로 Backend가 참조 무결성을 검증한다. 참조 대상이
없는 손상 문서는 일반 조회 결과에서 제외하고 운영 점검 대상으로 기록한다.

## 시간과 값 정책

- 시스템 시각은 Firestore Timestamp와 UTC `Instant`를 사용한다.
- 방문일은 시각 없는 `yyyy-MM-dd` 문자열로 저장한다.
- 점수는 Backend Validation에서 0 이상 10 이하로 검증한다.
- API 응답의 시스템 시각은 UTC ISO 8601로 직렬화한다.
- 사용자 표시 시 Frontend가 사용자 시간대로 변환한다.

## 조회 전략

Town AI는 개인용이며 V1 데이터가 수십 건을 넘지 않을 것으로 예상한다. 따라서
복합 Index와 여러 Query 조합을 늘리는 대신 Collection을 읽은 후 Backend에서 필터,
정렬, 통계를 수행한다. 데이터 규모가 수백~수천 건으로 커지면 Query와 Composite
Index, Pagination 도입을 다시 검토한다.

## 보안

- Browser와 LINE Client는 Firestore에 직접 접근하지 않는다.
- `firestore.rules`는 모든 Client read/write를 거부한다.
- Cloud Run Backend는 Runtime Service Account와 IAM으로 접근한다.
- Runtime Service Account에는 최소 `roles/datastore.user`만 부여한다.
- Service Account JSON Key는 생성하거나 Repository에 저장하지 않는다.

## 관련 문서

- 로컬 실행: `007-local-firestore-emulator.md`
- 배포 구성: `006-deployment-operations.md`
- 전환 설계: `014-firestore-migration-design.md`
