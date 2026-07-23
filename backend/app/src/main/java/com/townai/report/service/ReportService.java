package com.townai.report.service;

import com.townai.common.error.ApiException;
import com.townai.report.dto.ReportCreateRequest;
import com.townai.report.dto.ReportDetailResponse;
import com.townai.report.dto.ReportResponse;

import java.util.List;

/**
 * Report 생성·조회·본문 접근·삭제의 비즈니스 계약을 정의한다.
 */
public interface ReportService {

    /**
     * 요청을 검증하고 AI 생성부터 Storage·DB 저장까지 동기로 완료한다.
     *
     * @param request Report 유형과 생성 대상
     * @return 저장된 Report 메타데이터
     * @throws ApiException 대상, AI 출력 또는 Storage 처리에 실패한 경우
     */
    ReportResponse create(ReportCreateRequest request);

    /**
     * 선택적인 유형 조건으로 Report 목록을 조회한다.
     *
     * @param reportType 선택적인 Report 유형 문자열
     * @return 최근 생성 순의 Report 메타데이터 목록
     * @throws ApiException 지원하지 않는 유형을 전달한 경우
     */
    List<ReportResponse> findAll(String reportType);

    /**
     * Report 상세 메타데이터를 조회한다.
     *
     * @param reportId 조회할 Report ID
     * @return 생성 당시 대상 Area를 포함한 상세 메타데이터
     * @throws ApiException Report가 존재하지 않는 경우
     */
    ReportDetailResponse findById(Long reportId);

    /**
     * Report의 Markdown 본문을 조회한다.
     *
     * @param reportId 본문을 조회할 Report ID
     * @return Storage에 저장된 UTF-8 Markdown
     * @throws ApiException Report가 없거나 Storage를 읽을 수 없는 경우
     */
    String getContent(Long reportId);

    /**
     * Storage 객체를 먼저 삭제한 뒤 DB 메타데이터를 제거한다.
     *
     * @param reportId 삭제할 Report ID
     * @throws ApiException Report가 없거나 Storage 삭제에 실패한 경우
     */
    void delete(Long reportId);
}
