# Firestore Production 전환 검증

## 목차

1. 목적
2. 검증 환경
3. 전환 결정
4. 복원 범위와 기준
5. Firestore 복원 결과
6. GCS Report 보존 검증
7. Cloud SQL 종료
8. 운영 전환 경계
9. 백업 정책
10. 관련 파일

## 1. 목적

Town AI Production의 기존 Cloud SQL 데이터를 유료 전환 없이 종료하고, 보존된 GCS
Markdown Report를 근거로 핵심 Area와 Visit을 Firestore에 복원한 결과를 기록한다.

이 문서는 Firestore의 목표 구조를 정의하는 `014-firestore-migration-design.md`와 달리 실제
Production 데이터 전환의 입력, 변환 범위, 검증 결과와 Cloud SQL 제거 사실을 보존한다.

## 2. 검증 환경

| 항목 | 값 |
| --- | --- |
| 검증일 | 2026-08-30 |
| GCP Project | `town-ai` |
| Firestore Database | `town-ai` |
| Edition·Mode | Standard·Native |
| Location | `asia-northeast1` |
| GCS Bucket | `gs://town_ai` |
| Report Prefix | `reports/v1/**` |
| Legacy Cloud SQL Instance | `town-ai-api` |

Firestore Database는 Client 직접 접근 거부 Rules, V1 Index와 Delete Protection이 적용된
상태에서 복원했다.

## 3. 전환 결정

Legacy Cloud SQL은 30일 무료 평가 인스턴스였고 `SUSPENDED` 상태였다. 자동 백업은
비활성화되어 있었으며 보존된 Backup도 없었다. SQL 원본 조회를 위해서는 유료 인스턴스로
전환해야 했기 때문에 재기동이나 최종 Export를 수행하지 않았다.

개인용 V1의 데이터 규모와 남아 있는 GCS Report를 고려해 다음 정책을 선택했다.

- GCS Report에서 확인 가능한 Area 3개와 Visit 3개만 Firestore에 복원한다.
- 기존 숫자 ID를 유지하고 `counters`를 최대 ID 3으로 맞춘다.
- Area 복합 중복 방지용 `areaKeys`를 다시 계산한다.
- 초기 복원에서는 Report Metadata, LINE Draft와 Webhook Event 상태를 이전하지 않는다.
- 기존 GCS Markdown 객체는 수정하거나 이동하지 않는다.
- 복원 가능한 기존 Report Metadata는 GCS 객체 분석 후 별도 단계에서 복원한다.
- LINE 처리 문서는 실제 사용 시 Firestore에 새로 생성한다.

이 결정은 사용자가 관리하는 핵심 방문 데이터는 보존하면서, 복원이 불완전할 수 있는
처리 상태와 Metadata를 억지로 추정하지 않기 위한 것이다.

## 4. 복원 범위와 기준

복원 기준은 `gs://town_ai/reports/v1/**` 아래의 Markdown Report 9개이며, 가장 최근 ALL
Report와 각 AREA Report를 교차 확인했다. Report 내용에서 확인되지 않는 값은 생성하지
않았고, Area·Visit의 생성·수정 시각은 복원 실행 시각의 UTC Timestamp를 사용했다.

### 4.1 Area

| ID | 지역 | 도도부현 | 시·구 | 역 |
| ---: | --- | --- | --- | --- |
| 1 | 센터미나미 | 가나가와현 | 요코하마시 쓰즈키구 | 센터미나미역 |
| 2 | 카와구치 | 사이타마현 | 카와구치시 | 카와구치역 |
| 3 | 이나게카이간 | 치바현 | 치바시 미하마구 | 이나게카이간역 |

세 Area는 모두 활성 상태인 `deletedAt=null`로 복원했다.

### 4.2 Visit

| ID | Area ID | 방문일 | 분위기 | 생활 인프라 | 청결도 | 넓은 집 가능성 | 접근성 |
| ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: |
| 1 | 1 | 2026-07-25 | 8 | 8 | 8 | 7 | 7 |
| 2 | 2 | 2026-08-08 | 7 | 9 | 7 | 8 | 9 |
| 3 | 3 | 2026-08-09 | 7 | 4 | 9 | 9 | 6 |

각 Visit의 메모도 AREA Report와 기존 사용자 입력 기록을 기준으로 복원했다.

## 5. Firestore 복원 결과

복원 Script는 대상 Project와 Database를 `town-ai/town-ai`로 제한하고 다음 조건을 먼저
검사한다.

- Firestore가 Standard·Native이고 Location이 `asia-northeast1`인지 확인
- GCS Report 9개와 기준 ALL Report가 존재하는지 확인
- 빈 Firestore이거나 정확히 동일한 복원 Collection만 존재하는지 확인
- 새 문서는 `currentDocument.exists=false` 조건을 사용한 단일 Commit으로 생성

복원 후 다음 결과를 확인했다.

| Collection | 문서 수 | 검증 내용 |
| --- | ---: | --- |
| `areas` | 3 | ID, 지역명, 행정구역, 역, 활성 상태 |
| `visits` | 3 | ID, Area 참조, 방문일, 점수 5개, 메모 |
| `areaKeys` | 3 | 복합 Key SHA-256과 Area ID |
| `counters` | 2 | `area.lastId=3`, `visit.lastId=3` |

초기 복원에서는 `reports`, `lineVisitDrafts`, `lineWebhookEvents`를 제외했다. Firestore에는
빈 Collection을 미리 만드는 DDL이 없으므로 실제 Backend 요청과 후속 복원 Script가 첫
문서를 생성했다.

새 Runtime 데이터가 생성되기 전 복원 Script를 다시 실행하면 기존 값을 덮어쓰지 않고
동일한 복원 상태와 GCS 객체를 재검증한다. 이후 예상하지 않은 Collection이 존재하면
운영 데이터를 보호하기 위해 Script가 중단된다.

Cutover 후 LINE에서 Area·Visit과 Report를 새로 등록하고 Web에서 조회한 결과는 다음과
같다.

| Collection | 현재 문서 수 | 검증 내용 |
| --- | ---: | --- |
| `areas` | 4 | 기존 3개와 신규 Area ID 4 |
| `visits` | 4 | 기존 3개와 신규 Visit ID 4 |
| `reports` | 10 | 신규 ID 1과 복원된 기존 ID 2~10 |
| `lineVisitDrafts` | 1 | 저장 완료 Draft |
| `lineWebhookEvents` | 11 | 모두 `COMPLETED` |
| `areaKeys` | 4 | Area별 복합 Key |
| `counters` | 4 | Area 4, Visit 4, Report 10, LINE Draft 1 |

기존 Report Metadata는 `production-restore-gcs-report-metadata.ps1`로 복원했다. 파일명과
본문을 기준으로 ID, 유형, 대상 Area, 생성 시각, 생성 당시 모델 `gpt-5.4-mini`와 Prompt
Version을 기록했다. 기존 문서가 있으면 덮어쓰지 않고 검증하며, 더 큰 Counter가 있으면
낮추지 않는다. 두 번째 실행은 Firestore 쓰기 0건으로 완료되어 멱등성도 확인했다.

## 6. GCS Report 보존 검증

복원 전후 GCS 객체의 이름, generation, MD5, CRC32C, 크기와 수정 시각을 비교했다.

| Report Type | 객체 수 |
| --- | ---: |
| AREA | 5 |
| COMPARE | 1 |
| ALL | 3 |
| Legacy 합계 | 9 |

9개 객체의 모든 비교 값이 전후 동일했다. 따라서 Firestore 복원 과정에서 기존 Markdown
Report는 생성·수정·이동·삭제되지 않았다.

Cutover 후 새 AREA Report 1개가 추가되어 현재 GCS Report는 총 10개다. 신규 객체는
Firestore Report ID 1의 `storagePath`와 연결되며, 기존 9개 객체는 Report ID `2~10`의
Metadata와 다시 연결했다.

## 7. Cloud SQL 종료

Firestore 복원과 GCS 불변 검증을 완료한 뒤 2026-08-30에 Cloud SQL `town-ai-api`
인스턴스를 삭제했다.

삭제 직전 상태는 다음과 같았다.

| 항목 | 값 |
| --- | --- |
| 상태 | `SUSPENDED` |
| Region | `asia-northeast1` |
| 삭제 보호 | 비활성 |
| 자동 백업 | 비활성 |
| 보존 Backup | 0개 |

유료 전환과 최종 Backup은 수행하지 않았다. 삭제 후 Project의 Cloud SQL Instance 목록에서
`town-ai-api`가 존재하지 않음을 확인했다. 따라서 이전 Cloud SQL Revision으로의 데이터
Rollback은 더 이상 지원하지 않으며, 복원된 Firestore와 보존된 GCS Report가 V1 데이터의
운영 기준이다.

## 8. 운영 전환 경계

Firestore Backend는 Cloud Run Revision `town-ai-api-00028-pfz`에서 100% Traffic을 받고
있다. 적용·검증한 운영 설정은 다음과 같다.

- `FIRESTORE_PROJECT_ID=town-ai`, `FIRESTORE_DATABASE_ID=town-ai`
- Runtime Service Account `town-ai-runtime@town-ai.iam.gserviceaccount.com`
- Backend Build·배포 Service Account `town-ai-backend-deployer@town-ai.iam.gserviceaccount.com`
- Cloud SQL 연결, `DB_*` 환경변수와 `DB_PASSWORD` Secret 제거
- 기본 Compute Service Account의 기존 Project 역할 전부 제거
- Liveness·Readiness `200 UP`, 최신 Revision 오류 로그 없음
- Firebase Web 로그인, 주요 목록·통계·Report 조회와 비인증 API `401` 확인
- LINE Area·Visit 등록, Report 생성·조회, Cloud Tasks OIDC와 Push 응답 확인

LINE 동일 Event의 Production 강제 재전달은 별도 장애 유도 없이 진행하지 않았다.
Local·Emulator 자동 Test의 멱등성 검증을 V1 기준으로 수용하고 실제 재시도 발생 시
`lineWebhookEvents` 상태와 중복 Visit을 확인한다. Cloud Run은 최소 Instance 0으로
운영하며 새 Revision의 Cold Start와 Health를 검증했다. 장시간 유휴 후 첫 요청은 별도 완료
조건으로 두지 않고 실제 사용 중 관찰한다.

Cloud SQL이 삭제됐으므로 기존 SQL Revision은 Rollback 경로가 아니다. Application 배포
장애는 동일한 Firestore·GCS를 사용하는 직전 정상 Revision으로 Traffic을 되돌린다.

## 9. 백업 정책

Firestore Database Delete Protection은 활성화했다. PITR과 예약 Backup은 별도 과금 기능이며
개인용 V1의 필수 조건으로 두지 않는다. 현재는 둘 다 비활성 상태를 유지하고, 대량 수정·삭제,
데이터 재이전 또는 구조 변경 전에는 수동 Export 필요 여부를 검토한다.

Database의 `freeTier=true` 상태를 확인했다. 운영 중에는 Billing Report와 Budget 알림으로
실제 사용액을 확인하며, 데이터 중요도나 규모가 커지면 주 1회 Backup과 보존 기간을 별도로
결정한다.

## 10. 관련 파일

| 파일 | 역할 |
| --- | --- |
| `backend/scripts/production-restore-gcs-report-data.ps1` | Production 복원과 불변 검증 |
| `backend/scripts/production-restore-gcs-report-metadata.ps1` | 기존 GCS Report Metadata 복원과 불변 검증 |
| `docs/003-firestore-data-model.md` | Firestore Collection 기준 모델 |
| `docs/006-deployment-operations.md` | Production 배포·운영 기준 |
| `docs/legacy/010-legacy-cloud-sql-production-validation.md` | Legacy Cloud SQL 통합 검증 기록 |
| `docs/014-firestore-migration-design.md` | Firestore 전환 설계 |
| `docs/999-v1-completion-v2-backlog.md` | V1 배포·회귀 검증 완료 이력과 V2 후보 |
