# Local MySQL API 통합 검증

> Legacy 검증 기록: 이 문서는 2026-07-27 MySQL·JPA 구현의 당시 결과를 보존한다.
> 현행 Local Database와 검증 방법은 Firestore Emulator 기반의
> `007-local-database.md`와 `014-firestore-migration.md`를 따른다.

## 목적

단위 Test의 Mock 경계를 넘어 HTTP Controller, Service, JPA, Flyway, MySQL 및
Local Report Storage가 함께 동작하는지 확인한다. 실제 사용 DB를 오염시키지 않도록
별도 Database와 외부 API Mock을 사용한다.

## 검증 환경

```text
검증일              : 2026-07-27
Java                : 25.0.3
Spring Boot         : 4.1.0
MySQL               : 8.4.10
Database            : town_ai_integration
Backend             : http://localhost:18080
OpenAI·LINE API Mock: http://localhost:18081
Report Storage      : app/build/local-integration-reports
```

- `town_ai`는 사용하지 않고 `town_ai_integration`을 생성해 Flyway V1·V2를 처음부터 적용했다.
- OpenAI Responses API와 LINE Messaging API만 Local HTTP Mock으로 대체했다.
- Controller부터 외부 Adapter 직전·직후까지는 실제 Bean과 HTTP 요청을 사용했다.
- 검증 후 Backend와 Mock을 종료하고 통합 검증 Database와 Report 파일을 삭제했다.

## 검증 항목

| 영역 | 주요 확인 사항 | 결과 |
|---|---|---|
| Health | Liveness와 MySQL을 포함한 Readiness | 통과 |
| Area | 생성, trim, 중복 409, 전체 수정, 목록, Soft Delete, 삭제 후 404 | 통과 |
| Visit | 생성, 단건·목록, 날짜 범위, 정렬, 전체 수정, 삭제 | 통과 |
| Statistics | 전체·Area별 집계, Visit 수, 소수점 첫째 자리 반올림 | 통과 |
| Visit Parser | Responses API 요청, Structured Output 검증, UTF-8 응답 | 통과 |
| SUMMARY Report | 통계 조립, AI Comment, Local Storage, 조회·다운로드·삭제 | 통과 |
| AREA Report | 대상 검증, `report_area` 저장, Markdown 구조 검증 | 통과 |
| COMPARE Report | 대상 순서 보존, Structured Output, Markdown 조립 | 통과 |
| ALL Report | Visit 없는 활성 Area 포함, 전체 대상 관계와 Markdown 저장 | 통과 |
| LINE Text | 원문 HMAC 검증, 이벤트 저장, Draft 생성, Push, 완료 전환 | 통과 |
| LINE Postback | Draft 소유자·상태 검증, Visit 저장, Draft 확정, Push | 통과 |
| LINE 멱등성 | 완료된 동일 `webhookEventId` 재전달 시 중복 Draft·Visit 방지 | 통과 |
| 오류 응답 | 날짜 범위, 중복 Area, 빈 Visit Report, 잘못된 LINE 서명 | 통과 |

## DB와 Storage 확인

- Flyway가 빈 Database에 V1과 V2를 순서대로 적용했다.
- Hibernate `ddl-auto=validate`가 실제 MySQL Schema 검증을 통과했다.
- Report 생성 시 `report`, `report_area` 및 Markdown 파일이 함께 생성됐다.
- Report 삭제 시 Local Storage 파일을 먼저 삭제하고 DB 관계와 메타데이터를 삭제했다.
- LINE 확인 Postback은 Draft를 `CONFIRMED`로 전환하고 `confirmed_visit_id`를 보존했다.
- 완료된 LINE Text 이벤트를 다시 전달해도 `attempt_count`, Draft 수 및 Visit 수가 증가하지 않았다.

## 검증 중 발견 및 수정

매핑되지 않은 URL이 `NoResourceFoundException`으로 처리될 때 기존 공통 예외 처리기가
이를 `500 INTERNAL_SERVER_ERROR`로 변환하는 문제가 발견됐다.

다음과 같이 수정했다.

```text
존재하지 않는 Endpoint
→ 404 Not Found
→ ENDPOINT_NOT_FOUND
```

회귀 Test를 추가해 미등록 Endpoint가 내부 서버 오류로 노출되지 않도록 고정했다.

## 별도 검증 항목

다음은 Local 통합 검증의 범위가 아니며 각각의 TODO에서 관리한다.

- 실제 OpenAI 모델의 Report 품질과 응답 편차
- 실제 GCP Bucket의 IAM·Region·네트워크를 포함한 GCS 통합
- Cloud Tasks의 실제 OIDC Token과 Cloud Run 호출
- Production Cloud SQL 사양과 비용
