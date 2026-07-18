# Town-AI 프로젝트 설계 대화 요약

> 현재까지 진행한 핵심 설계 내용을 요약한 문서입니다.

## 프로젝트 목적

-   일본 거주 지역을 평가하고 AI 리포트를 생성하는 개인 프로젝트
-   Frontend: React
-   Backend: Spring Boot
-   Database: MySQL
-   AI: OpenAI API
-   Cloud: GCP + Cloud Storage

## 설계 원칙

-   AI는 Business Logic에 관여하지 않는다.
-   Spring Boot가 비즈니스 로직을 담당한다.
-   Source of Truth는 MySQL이다.
-   Report는 Cloud Storage에 저장하고 DB에는 메타데이터만 저장한다.
-   미결사항은 반드시 TODO로 관리한다.

## 문서 구성

-   001 Requirements
-   002 Architecture
-   003 ERD
-   004 API
-   005 Prompt

## ERD

### area

-   id
-   name
-   prefecture
-   city
-   station
-   created_at
-   updated_at

### visit

-   id
-   area_id
-   visit_date
-   atmosphere_score
-   infra_score
-   clean_score
-   size_score
-   access_score
-   memo
-   created_at
-   updated_at

### report

-   id
-   report_type
-   model
-   prompt_version
-   storage_path
-   created_at
-   updated_at

## ReportType

-   SUMMARY : SQL 통계 + AI 요약
-   ALL : 전체 지역 개요
-   AREA : 특정 지역 상세 분석
-   COMPARE : 여러 지역 비교 분석

## API 방향

Base URL `/api`

### Area

-   GET /api/areas
-   GET /api/areas/{id}
-   POST /api/areas
-   PUT /api/areas/{id}
-   DELETE /api/areas/{id}

### Visit

-   GET /api/visits
-   GET /api/visits/{id}
-   POST /api/visits
-   PUT /api/visits/{id}
-   DELETE /api/visits/{id}

### Report

-   GET /api/reports
-   GET /api/reports/{id}
-   GET /api/reports/{id}/content
-   GET /api/reports/{id}/download
-   POST /api/reports
-   DELETE /api/reports/{id}

### Statistics

-   GET /api/statistics
-   GET /api/areas/{areaId}/statistics

Report 생성 요청은 AREA와 COMPARE 모두 areaIds 배열을 사용한다.

## Architecture

-   Component Diagram 작성 완료
-   Report Generation Sequence 작성 완료

## TODO

-   Area UNIQUE 정책
-   Station 모델 분리 여부
-   Pagination
-   Prompt Version 정책
-   JWT 적용 여부

## 개발 방향

-   설계 우선
-   문서를 프로젝트의 Memory로 활용
-   VS Code(OpenAI Extension)에서 docs를 기반으로 구현 진행
