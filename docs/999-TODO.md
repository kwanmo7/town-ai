# TODO

상태 표기:
- `[O]`: 해결 또는 설계 확정
- `[ ]`: 미해결 또는 후속 검토 필요

## 작업 순서

1. [O] API 설계 완료
2. [O] Prompt 설계 완료
3. [O] 배포 설계 완료
4. [ ] Backend 구현 및 Production 연동 검증
   - [O] Backend 핵심 기능 구현
     - [O] Area REST API
     - [O] Visit REST API
     - [O] Report REST API와 Local Report 생성·저장 흐름
     - [O] Statistics API와 SUMMARY 공통 집계 흐름
     - [O] 자연어 Visit Parser API
     - [O] LINE Webhook, 비동기 전달 및 Visit Draft 처리
       - [O] 원문 Body HMAC-SHA256 서명 검증과 요청 DTO 역직렬화
       - [O] `LINE_ALLOWED_USER_ID` 기반 1:1 사용자 및 지원 이벤트 선별
       - [O] Webhook 이벤트 저장과 Local·Cloud Tasks Dispatcher 연결
       - [O] 내부 Task Endpoint, OIDC 인증, 처리 Lease 및 최대 5회 상태 관리
       - [O] Visit Draft 생성, LINE Push, 저장·부분 수정·취소 및 Visit 저장
       - [O] 등록 Area 0건에서 신규 Area 후보 확인 후 Area·Visit 동시 저장
       - [O] 최대 처리 실패 안내 Push 및 30일이 지난 처리 데이터의 기회적 정리
     - [O] LINE Bot 등록·조회 화면과 Rich Menu 디자인
       - [O] 등록·조회·Report 결과 Flex Message JSON
       - [O] `2500 x 843`, 1MB 이하 Rich Menu 이미지와 터치 영역 정의
       - 반영 파일: `linebotdesign/`
       - 반영 문서: `011-line-bot-design.md`
     - [O] LINE 메뉴·Report 조회 Backend 연동
       - [O] Flex Message 공통 모델과 Serialization 구조
       - [O] Draft 확인 화면 및 고정 메뉴·안내 화면 Factory
       - [O] Follow·메뉴·Report Type·Area 선택 Postback 처리
       - [O] LINE용 Report 생성 결과 Push 및 Webhook Event 기반 중복 방지
       - [O] 활성 Visit 0건의 SUMMARY·ALL 생성 사전 차단
       - [O] Draft 저장 전 자연어 부분 수정 및 누락값 병합
     - [ ] LINE 화면 Production 마무리
       - 안전한 Report 보기·다운로드 URL
       - 실제 모바일 Rich Menu와 오래된 메시지 재클릭 검증
       - Flyway V5~V7 배포 후 신규 Area·Visit 동시 등록, Draft 부분 수정·경합 처리와 빈 Report 차단 재검증
     - [O] Production용 GCS Report Storage 구현체
     - [O] Local MySQL 기반 전체 API 통합 검증
       - 격리된 `town_ai_integration` Database에 당시 Flyway V1·V2 적용
       - Area, Visit, Statistics, Parser, Report 4종과 LINE 확인 흐름 검증
       - OpenAI Responses API와 LINE Messaging API는 Local HTTP Mock 사용
       - 검증 결과: `009-local-api-integration.md`
   - [O] Production용 Backend Dockerfile과 Docker Build Context 제외 정책
     - Java 25 Build·Runtime Multi-stage Image 및 Non-root 사용자 적용
     - Cloud Run `PORT` 환경변수 연동
     - Docker Build Context와 Spring Boot 실행 JAR에서 Local Secret·환경 파일 제외
     - 현재 개발 PC에는 Docker Engine이 없어 실제 Image Build는 후속 검증
   - [O] GitHub Actions Backend CI Workflow 구현
     - Java 25, Gradle Test·Build, Javadoc 및 Docker Image Build 검증
     - `main` 대상 모든 Pull Request와 수동 실행에서 검증
     - Required Status Check 누락 방지를 위해 경로 필터를 사용하지 않음
   - [O] Developer Connect·Cloud Build 기반 Backend CD 실제 배포 검증
     - GitHub Actions CD와 중복 구성하지 않음
     - Build Type은 Dockerfile, Source Location은 `backend/Dockerfile` 사용
     - `town-ai-api` 실제 Backend 배포 및 Liveness·Readiness `UP` 확인
   - [ ] 실제 외부 서비스 및 Production GCP 통합 검증
     - [O] Cloud Run, Cloud SQL, 실제 OpenAI AREA Report와 GCS 저장·조회·삭제
     - [ ] LINE Messaging API, Cloud Tasks와 OIDC
     - 검증 결과: `010-production-gcp-integration.md`
5. [ ] ERD PNG/XLSX 최종 동기화

- ERD의 기준 스키마는 개발 중 `ERD/town-ai-v1.sql`로 관리한다.
- `ERD/town-ai-v1.png`와 `ERD/town-ai-v1.xlsx`는 Backend 구현 이후 최종 스키마를 기준으로 갱신한다.

## V1 설계 결정

- [O] Area는 `(prefecture, city, name)` UNIQUE Key 사용
  - 반영 문서: `003-erd.md`, `004-api.md`, `ERD/town-ai-v1.sql`
- [O] Report Type은 Enum 사용
  - 반영 문서: `004-api.md`
- [O] Prompt Version은 기능별 독립 증가 형식 사용
  - 현재 값: `summary-v1`, `all-v1`, `area-v1`, `compare-v1`, `visit-parser-v2`
  - `visit-parser-v2`는 미등록 Area 후보와 위치 확인 규칙을 추가
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
  - 유효한 Draft에 저장·부분 수정·취소 Postback을 제공하고 확인된 Draft만 Visit으로 저장
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

- [O] Local MySQL 8.4 환경 전환
  - MySQL 8.4.10과 Local `town_ai` Database 사용
  - Flyway V1·V2, Hibernate Schema 검증 및 Health Check 기동 확인 완료
  - 실행 절차 및 결과 문서: `007-local-database.md`
- [O] Cloud Storage 디렉터리 및 파일명 정책 확정
  - 객체 경로: `reports/v1/{reportType-lowercase}/{filename}_{yyyy-MM-dd}_{reportId}.md`
  - 파일명 날짜는 `USER_TIME_ZONE`의 사용자 생활권 날짜 사용
  - AREA와 COMPARE의 `filename`에는 대상 지역명을 사용
  - SUMMARY와 ALL은 별도 파일명 없이 날짜와 Report ID를 사용
  - Production Bucket은 `gs://town_ai`, `GCS_BUCKET_NAME=town_ai`로 사용
  - 반영 문서: `003-erd.md`, `006-deployment.md`
- [O] Production용 `GcsReportStorage` 구현
  - `ReportStorage`를 구현해 Markdown 객체 저장, UTF-8 조회 및 멱등 삭제를 지원
  - `REPORT_STORAGE_TYPE=gcs`일 때만 활성화하고 `GCS_BUCKET_NAME`을 필수로 검증
  - Google Cloud Storage Client 의존성과 `Storage` Bean 구성 추가
  - Cloud Run에서는 Service Account와 Application Default Credentials를 사용하고 JSON Key를 저장하지 않음
  - Bucket은 애플리케이션이 생성하지 않고 배포 단계에서 `asia-northeast1`에 비공개로 생성
  - 저장 시 `Content-Type: text/markdown; charset=UTF-8` 적용
  - 객체가 이미 없으면 삭제 성공으로 처리하고 그 외 GCS 오류는 `ReportStorageException`으로 변환
  - Mock 기반 저장·조회·삭제 및 Local·GCS 조건 전환 Test 추가
  - 구현 대상: `backend/app/build.gradle`, `backend/app/src/main/java/com/townai/report/storage/GcsReportStorage.java`, GCP Storage 설정 Class 및 Test
  - 확인 및 반영 문서: `006-deployment.md`
- [O] 실제 GCP Bucket을 사용한 `GcsReportStorage` 통합 검증
  - `gs://town_ai`와 Cloud Run Runtime Service Account의 ADC 사용
  - 실제 OpenAI AREA Report 저장, UTF-8 조회·다운로드 및 삭제 성공
  - 테스트 Report·Visit 삭제와 Area Soft Delete 완료
  - 검증 결과: `010-production-gcp-integration.md`
- [O] Production GCP Region은 `asia-northeast1`(Tokyo)로 통일
  - 적용 대상: Cloud Run, Cloud SQL, Cloud Storage, Artifact Registry
  - 반영 문서: `006-deployment.md`
- [ ] Cloud SQL Instance 사양 및 월 비용 확정
  - 30일 무료 평가 Instance `town-ai-api` 생성
  - 연결 이름: `town-ai:asia-northeast1:town-ai-api`
  - Cloud SQL Java Connector `1.29.0`과 Production `DB_URL` 적용
  - Cloud Run Readiness `UP`, Flyway V1·V2 및 실제 API CRUD로 연결 검증 완료
  - 무료 평가 종료 전 Pricing Calculator로 장기 운영 사양과 비용 확정
  - MySQL 8.4와 Enterprise Edition 기준으로 CPU·Memory·Storage 사양 확인
  - 결정 후 수정할 문서: `006-deployment.md`
- [ ] Report 생성 중 장애로 남은 고아 Storage 객체 정리 방식 확정
  - Storage 저장 직후 Process가 종료되면 보상 삭제가 실행되지 않을 수 있음
  - Production 운영 전 수동 점검 절차 또는 정리 Job 중 하나를 결정
  - 결정 후 수정할 문서: `004-api.md`, `006-deployment.md`
- [ ] 실제 OpenAI API를 사용한 Report Prompt 품질 평가
  - `backend`에서 `.\gradlew.bat :app:promptEval` 실행
  - SUMMARY, AREA, COMPARE, ALL 결과의 사실성, 균형성, 실용성 및 가독성을 평가
  - 동일 Fixture를 최소 3회 실행해 모델 응답 편차 확인
  - Report별 평균 4.0 이상, 입력에 없는 사실 단정 0건을 합격 기준으로 사용
  - 결과 위치: `backend/app/build/prompt-eval/`
  - 실행 및 평가 기준 문서: `008-report-quality-evaluation.md`
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
