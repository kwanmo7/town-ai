package com.townai.report.service;

import com.townai.common.error.ApiException;
import com.townai.report.dto.ReportCreateRequest;
import com.townai.report.dto.ReportDetailResponse;
import com.townai.report.dto.ReportResponse;

import java.util.List;
import java.util.Optional;

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
     * LINE Task 재처리에도 같은 Report를 반환하도록 멱등 생성한다.
     *
     * @param request Report 유형과 생성 대상
     * @param sourceWebhookEventId 생성을 요청한 LINE Webhook Event ID
     * @return 새로 생성했거나 기존에 생성된 Report 메타데이터
     */
    ReportResponse createForLine(
            ReportCreateRequest request,
            String sourceWebhookEventId
    );

    /**
     * LINE 재시도에서 생성 완료된 기존 Report를 조회한다.
     *
     * @param sourceWebhookEventId 원본 LINE Webhook Event ID
     * @return 같은 이벤트가 이미 생성한 Report
     */
    Optional<ReportResponse> findBySourceWebhookEventId(
            String sourceWebhookEventId
    );

    /**
     * 현재 DB 입력과 Prompt 버전이 같은 기존 Report를 조회한다.
     *
     * @param request Report 유형과 조회 대상
     * @return 새 AI 호출 없이 재사용할 수 있는 Report
     * @throws ApiException 현재 대상이나 Visit 데이터가 유효하지 않은 경우
     */
    Optional<ReportResponse> findReusable(ReportCreateRequest request);

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
