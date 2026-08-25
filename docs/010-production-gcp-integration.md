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

## LINE 및 Report 링크 추가 검증

2026-08-05 실제 LINE 모바일 대화에서 다음 흐름을 확인했다.

- LINE Webhook 수신, Cloud Tasks 전달과 OIDC 인증
- 신규 Area 후보 Draft 생성과 자연어 부분 수정
- 신규 Area와 Visit 동시 저장
- LINE에서 AREA·ALL Report 생성과 GCS 객체 저장
- 당시 공개 상태였던 Cloud Run `GET /api/reports/2/content`, `/download` 응답 `200`
- 당시 공개 상태였던 Cloud Run `GET /api/reports/3/content`, `/download` 응답 `200`

GCS 객체와 Cloud Run 조회 Endpoint는 정상이지만 LINE 완료 메시지의 Report Base
URL이 Local 기본값을 사용할 수 있는 설정 문제를 확인했다. `LINE_REPORT_BASE_URL`을
공개 Cloud Run Origin으로 지정하고, 값이 없으면 이미 검증된
`LINE_CLOUD_TASKS_OIDC_AUDIENCE`를 재사용하도록 수정했다.

LINE 화면에서 `광역권 null` 같은 AI 자리표시자 노출과 Visit 저장 후 이동 버튼이
없는 문제도 확인했다. 자리표시자 무효화, 신규 Area 위치 Best-effort 보완과 저장
완료 메뉴 버튼을 반영했다. 두 수정 사항의 재검증 결과는 다음 절에 기록한다.

### LINE 수정본 재검증

2026-08-11 수정본을 Production에 배포하고 실제 LINE 모바일 대화에서 다시
사용해 다음 흐름이 정상 동작함을 확인했다.

- 행정구역 일부를 생략한 자연어 입력의 Area 위치 보완과 Visit 저장
- Draft 확인·부분 수정·저장·취소와 저장 완료 후 메인 메뉴 이동
- Report 결과의 `리포트 보기`와 `Markdown 다운로드` 공개 HTTPS 링크
- 입력 데이터와 Prompt 조건이 바뀌지 않은 기존 Report 재사용
- 새 Area 또는 Visit 등 분석 입력이 변경된 경우 새 Report 생성
- Report 완료 화면에서 다른 Report 조회와 메인 메뉴 이동

따라서 LINE Webhook, Cloud Tasks·OIDC, OpenAI Parser·Report 생성, Cloud SQL,
GCS 저장 및 Cloud Run 공개 Report Endpoint로 이어지는 Production 핵심 흐름은
실제 모바일 사용 기준으로 검증 완료했다. Report 재사용은 Report Type, Prompt
Version, Model, 대상 Area와 Prompt 입력을 기반으로 만든 SHA-256 지문을 사용한다.

## 남은 Production 검증

- SUMMARY, COMPARE, ALL을 포함한 반복 Prompt 품질 평가
- 무료 Cloud SQL 평가 종료 전 장기 운영 사양과 비용 확정
- 장애로 남을 수 있는 고아 GCS 객체의 운영 정리 정책
- 구현된 Web Firebase 인증과 LINE 30일 만료 서명 URL의 Production 재검증
- 오래된 LINE 메시지의 장기 재클릭 동작
