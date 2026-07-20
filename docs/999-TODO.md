# TODO

상태 표기:
- `[O]`: 해결 또는 설계 확정
- `[ ]`: 미해결 또는 후속 검토 필요

## 작업 순서

1. [O] API 설계 완료
2. [O] Prompt 설계 완료
3. [O] 배포 설계 완료
4. [ ] Backend 구현
   - REST API와 Report 생성 흐름 구현
   - LINE Webhook 서명 검증, Cloud Tasks 전달, Visit Draft 확인·취소 및 Visit 저장 구현
5. [ ] ERD PNG/XLSX 최종 동기화

- ERD의 기준 스키마는 개발 중 `ERD/town-ai-v1.sql`로 관리한다.
- `ERD/town-ai-v1.png`와 `ERD/town-ai-v1.xlsx`는 Backend 구현 이후 최종 스키마를 기준으로 갱신한다.

## V1 설계 결정

- [O] Area는 `(prefecture, city, name)` UNIQUE Key 사용
  - 반영 문서: `003-erd.md`, `004-api.md`, `ERD/town-ai-v1.sql`
- [O] Report Type은 Enum 사용
  - 반영 문서: `004-api.md`
- [O] Prompt Version은 기능별 `{type}-v1` 형식 사용
  - 값: `summary-v1`, `all-v1`, `area-v1`, `compare-v1`, `visit-parser-v1`
  - DB 컬럼은 `VARCHAR(30)` 사용
  - 반영 문서: `003-erd.md`, `004-api.md`, `ERD/town-ai-v1.sql`
- [O] PK 및 Timestamp 생성 정책 확정
  - Entity PK는 `BIGINT AUTO_INCREMENT`, `report_area`는 복합 PK 사용
  - `created_at`, `updated_at`은 DB의 `CURRENT_TIMESTAMP`를 사용하고 `updated_at`은 수정 시 자동 갱신
  - DB와 애플리케이션은 UTC를 사용하고 API는 ISO8601로 직렬화
  - Visit 점수는 DB와 Backend에서 0 이상 10 이하로 검증
  - 반영 문서: `003-erd.md`, `ERD/town-ai-v1.sql`
- [O] Report ID 선점 및 실패 보상 순서 확정
  - Transaction 내부에서 Report Row를 먼저 INSERT해 ID를 얻은 후 Storage 경로 생성
  - Storage 저장 후 DB 실패 시 Rollback하고 Storage 객체를 Best-effort로 삭제
  - 반영 문서: `003-erd.md`, `004-api.md`
- [O] LINE Bot Backend V1 범위 확정
  - Webhook 서명 검증, 텍스트 Visit Draft 처리 및 Push Message 결과 회신
  - 유효한 Draft에 확인·취소 Postback을 제공하고 확인된 Draft만 Visit으로 저장
  - `LINE_ALLOWED_USER_ID`로 개인 사용자만 허용
  - 반영 문서: `001-requirements.md`, `002-architecture.md`, `003-erd.md`, `004-api.md`, `006-deployment.md`, `ERD/town-ai-v1.sql`
- [O] LINE Webhook 비동기 처리 및 중복 방지 방식 확정
  - Production은 Cloud Tasks HTTP Target과 OIDC 인증 사용
  - Local은 `LocalLineEventDispatcher` 사용
  - `webhookEventId`를 DB PK 및 결정적 Task 이름으로 사용
  - 이벤트 처리와 Draft 확인은 DB 상태 전환으로 멱등성 보장
  - 기존 Draft는 `source_webhook_event_id`로 조회해 재사용
  - Push Message는 `webhookEventId`와 메시지 용도 기반 UUIDv5 Retry Key 사용
  - 다섯 번째 애플리케이션 처리 실패는 이벤트를 `FAILED`로 종료
  - 반영 문서: `002-architecture.md`, `003-erd.md`, `004-api.md`, `006-deployment.md`, `ERD/town-ai-v1.sql`
- [O] Backend 주요 Version 확정
  - Java 25 LTS
  - Spring Boot 4.1.x, 초기 고정 Version `4.1.0`
  - Gradle `9.6.1`
  - Local 및 Production MySQL 8.4 LTS
  - 반영 파일: `backend/app/build.gradle`, `backend/gradle/wrapper/gradle-wrapper.properties`
  - 반영 문서: `001-requirements.md`, `006-deployment.md`
- [O] 날짜 및 Timestamp 정책 확정
  - `visit_date`는 시각 없는 `DATE`
  - 시스템 감사·처리·만료 시각은 UTC 초 단위 `TIMESTAMP`
  - API는 UTC ISO 8601로 반환하고 Frontend에서 사용자 시간대로 변환
  - 반영 문서: `003-erd.md`, `004-api.md`, `006-deployment.md`, `ERD/town-ai-v1.sql`
- [O] Area는 `deleted_at TIMESTAMP NULL`을 사용해 Soft Delete
  - 반영 문서: `003-erd.md`, `004-api.md`, `ERD/town-ai-v1.sql`
- [O] Report와 생성 대상 Area는 `report_area` 연결 테이블로 관리
  - 복합 PK: `(report_id, area_id)`
  - 일반 컬럼: `display_order`
  - 반영 문서: `003-erd.md`, `004-api.md`, `ERD/town-ai-v1.sql`
- [O] Statistics 집계 및 정렬 정책 확정
  - 반영 문서: `004-api.md`
- [O] SUMMARY는 SQL 통계 결과에 짧은 AI Comment를 추가
  - 반영 문서: `004-api.md`
- [O] ALL은 모든 Area와 Visit을 기반으로 AI 상세 분석 리포트 생성
  - 반영 문서: `004-api.md`
- [O] V1에서는 주관 평가와 객관 평가의 70:30 점수화를 적용하지 않음
  - 객관 데이터가 없는 상태에서 임의의 점수를 생성하지 않음
  - AREA, COMPARE, ALL Report에 객관적으로 추가 확인할 체크리스트를 제공
  - 반영 문서: `005-prompt.md`
- [O] 공통 오류 코드 목록 정리
  - 반영 문서: `004-api.md`
- [O] Prompt System 지시문, Structured Outputs Schema, 재시도 정책 및 테스트 기준 확정
  - 실행 파일: `backend/app/src/main/resources/prompts/`
  - 반영 문서: `005-prompt.md`

## V1 미결 사항

- [ ] Local MySQL 8.4 환경 전환
  - 현재 PC에서 실행 중인 `MySQL83` Service는 MySQL 8.3을 사용 중
  - 기존 Database 백업 및 호환성 확인 후 MySQL 8.4로 전환
  - 사용하지 않는 MySQL 8.1 및 8.3 Service 정리는 데이터 보존 여부를 확인한 후 수행
  - 반영 대상: Local 개발 환경
- [O] Cloud Storage 디렉터리 및 파일명 정책 확정
  - 객체 경로: `reports/v1/{reportType-lowercase}/{filename}_{yyyy-MM-dd}_{reportId}.md`
  - AREA와 COMPARE의 `filename`에는 대상 지역명을 사용
  - SUMMARY와 ALL은 별도 파일명 없이 날짜와 Report ID를 사용
  - Bucket 이름은 `town-ai-reports-{uniqueSuffix}` 형식으로 생성하고 `GCS_BUCKET_NAME`으로 전달
  - 반영 문서: `003-erd.md`, `006-deployment.md`
- [O] Production GCP Region은 `asia-northeast1`(Tokyo)로 통일
  - 적용 대상: Cloud Run, Cloud SQL, Cloud Storage, Artifact Registry
  - 반영 문서: `006-deployment.md`
- [ ] Cloud SQL Instance 사양 및 월 비용 확정
  - Backend 구현 완료 후 Production 운영 직전에 Pricing Calculator로 확인
  - MySQL 8.4와 Enterprise Edition은 확정, CPU·Memory·Storage 사양만 결정
  - 그전까지 Local MySQL 8.4 사용
  - 결정 후 수정할 문서: `006-deployment.md`
- [ ] Report 생성 중 장애로 남은 고아 Storage 객체 정리 방식 확정
  - Storage 저장 직후 Process가 종료되면 보상 삭제가 실행되지 않을 수 있음
  - Production 운영 전 수동 점검 절차 또는 정리 Job 중 하나를 결정
  - 결정 후 수정할 문서: `004-api.md`, `006-deployment.md`
- [O] 초기 GCP 월 Budget과 알림 기준 확정
  - 월 Budget: `¥1,000`
  - 알림: 실제 사용액 `50%`, `80%`, `100%`, 예상 월말 사용액 `80%`
  - Cloud SQL 도입 후 정상 예상 월 비용의 120%로 재산정
  - 반영 문서: `006-deployment.md`
- [O] Firebase Hosting과 GCP 서비스를 하나의 Production GCP Project에서 운영
  - 기존 Town-AI GCP Project에 Firebase를 활성화
  - 반영 문서: `006-deployment.md`
- [O] Cloud Run Health Check Endpoint 및 Probe 정책 확정
  - Liveness: `/actuator/health/liveness`
  - Readiness: `/actuator/health/readiness`
  - Startup Probe는 Readiness, Liveness Probe는 Liveness Endpoint 사용
  - 반영 문서: `004-api.md`, `006-deployment.md`

## V2 검토 사항

- [ ] 출처가 있는 객관 데이터 수집 및 주관 70%·객관 30% 평가 모델
  - 후보 지표: 주거비, 통근, 안전, 재해 위험, 생활 편의
  - 검토 사항: 데이터 출처, 갱신 주기, 정규화, 누락 데이터, 위험 경고
  - 결정 후 수정할 문서: `001-requirements.md`, `003-erd.md`, `004-api.md`, `005-prompt.md`
- [ ] Area 복구 API 및 Hard Delete 보존 기간
  - 결정 후 수정할 문서: `003-erd.md`, `004-api.md`
- [ ] Area, Visit, Report 목록 페이지네이션
  - 결정 후 수정할 문서: `004-api.md`

## 최종 동기화

- [ ] ERD 다이어그램 산출물 갱신
  - Backend 구현 이후 최종 스키마를 기준으로 갱신
  - `ERD/town-ai-v1.sql`의 Area Soft Delete, UNIQUE Key, ReportArea, LineWebhookEvent, LineVisitDraft 등을 반영
  - 수정할 파일: `ERD/town-ai-v1.png`, `ERD/town-ai-v1.xlsx`
