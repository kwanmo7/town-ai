# TODO

상태 표기:
- `[O]`: 해결 또는 설계 확정
- `[ ]`: 미해결 또는 후속 검토 필요

## 작업 순서

1. [O] API 설계 완료
2. [ ] Prompt 설계
3. [ ] 배포 설계
4. [ ] Backend 구현
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
  - 반영 문서: `004-api.md`
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

## V1 미결 사항

- [ ] Cloud Storage 디렉터리 및 파일명 정책 확정
  - 후보 1: `reports/{yyyy}/{MM}/{reportType}-{reportId}.md`
  - 후보 2: `reports/V1/{filename}-{yyyy-MM-dd}.md`
  - 검토 사항: 동일 날짜 파일명 충돌 방지, Report ID 추적성, 애플리케이션 버전과 Prompt Version 구분
  - 결정 후 수정할 문서: `003-erd.md`, `004-api.md`, `006-deployment.md`

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
  - `ERD/town-ai-v1.sql`의 Area Soft Delete, UNIQUE Key, ReportArea 등을 반영
  - 수정할 파일: `ERD/town-ai-v1.png`, `ERD/town-ai-v1.xlsx`
