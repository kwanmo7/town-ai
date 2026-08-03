# Production GCP 통합 검증

## 목적

Developer Connect로 배포한 실제 Cloud Run Backend가 Cloud SQL, OpenAI API,
Cloud Storage를 사용하는 Report 흐름을 정상 처리하는지 확인한다.

## 검증 환경

```text
검증일                : 2026-07-28
Cloud Run Service     : town-ai-api
Cloud Run Region      : asia-northeast1
Cloud SQL Connection  : town-ai:asia-northeast1:town-ai-api
Cloud Storage Bucket  : gs://town_ai
Report Storage Type   : gcs
```

Secret 값은 검증 결과와 문서에 기록하지 않는다.

## 사전 기동 확인

| Endpoint | 결과 |
|---|---|
| `GET /actuator/health/liveness` | `200`, `{"status":"UP"}` |
| `GET /actuator/health/readiness` | `200`, `{"status":"UP"}` |
| `GET /api/areas` | `200`, 빈 배열 |

Readiness에 DB Health가 포함되므로 `UP` 응답으로 Cloud SQL 연결과 애플리케이션
기동이 정상임을 확인했다. 빈 Cloud SQL에는 Flyway V1·V2가 자동 적용됐다.

## Report 통합 검증

식별 가능한 테스트 Area와 Visit을 생성하고 실제 AREA Report를 생성했다.

```text
Area   : GCS-Test-20260728, ID 1
Visit  : ID 1
Report : AREA, ID 1
```

| 단계 | 결과 |
|---|---|
| Area 생성 | `201 Created` |
| Visit 생성 | `201 Created` |
| 실제 OpenAI AREA Report 생성 | `201 Created` |
| Report 대상 Area 관계 조회 | `200`, `areaIds=[1]` |
| GCS Markdown 본문 조회 | `200`, UTF-8 Markdown 2,750자 |
| 대상 Area 이름 보존 | `GCS-Test-20260728` 포함 |
| Report 다운로드 | `200`, `attachment; filename="area-report-1.md"` |
| Report 및 GCS 객체 삭제 | `204 No Content` |
| Visit 삭제 | `204 No Content` |
| Area Soft Delete | `204 No Content` |

Report 본문 조회가 실제 GCS Adapter를 통해 성공했으므로 Service Account의
Storage 권한, 객체 저장 경로, UTF-8 저장·조회가 정상임을 확인했다. Report 삭제가
`204`로 완료되어 GCS 삭제와 DB 메타데이터 삭제 흐름도 정상 처리됐다.

## 정리 확인

```text
GET /api/areas     → []
GET /api/visits    → []
GET /api/reports   → []
GET /api/reports/1 → 404 REPORT_NOT_FOUND
```

Visit과 Report는 삭제됐으며 Area ID 1은 Soft Delete 정책에 따라 DB 이력으로만
남고 일반 목록에서는 제외된다.

## 남은 Production 검증

- 실제 LINE Messaging API Webhook 수신과 Push
- Cloud Tasks Queue 전달과 OIDC 인증
- SUMMARY, COMPARE, ALL을 포함한 반복 Prompt 품질 평가
- 무료 Cloud SQL 평가 종료 전 장기 운영 사양과 비용 확정
- 장애로 남을 수 있는 고아 GCS 객체의 운영 정리 정책
