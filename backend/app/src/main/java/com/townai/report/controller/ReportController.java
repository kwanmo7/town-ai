package com.townai.report.controller;

import com.townai.report.dto.ReportCreateRequest;
import com.townai.report.dto.ReportDetailResponse;
import com.townai.report.dto.ReportResponse;
import com.townai.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Report의 동기 생성, 메타데이터 조회, Markdown 조회·다운로드와 삭제를 노출한다.
 *
 * <p>Storage의 내부 객체 경로는 응답에 노출하지 않는다. 클라이언트는 Report ID로
 * 본문 또는 다운로드 Endpoint를 호출하며, 모든 Markdown 응답은 UTF-8이다.</p>
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final MediaType MARKDOWN_UTF8 =
            new MediaType("text", "markdown", StandardCharsets.UTF_8);

    private final ReportService reportService;

    /**
     * Report Controller를 생성한다.
     *
     * @param reportService Report Use Case를 처리할 Service
     */
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 요청 유형에 맞는 AI Report를 동기로 생성하고 저장한다.
     *
     * @param request Report 유형과 선택적인 대상 Area 목록
     * @return {@code 201 Created}, Report 상세 위치와 생성 메타데이터
     */
    @PostMapping
    public ResponseEntity<ReportResponse> create(
            @Valid @RequestBody ReportCreateRequest request
    ) {
        ReportResponse response = reportService.create(request);
        return ResponseEntity
                .created(URI.create("/api/reports/" + response.id()))
                .body(response);
    }

    /**
     * Report 메타데이터를 최근 생성 순으로 조회한다.
     *
     * @param reportType 선택적인 Report 유형 필터
     * @return 생성 시각 내림차순, 동률이면 ID 내림차순인 목록
     */
    @GetMapping
    public List<ReportResponse> findAll(
            @RequestParam(required = false) String reportType
    ) {
        return reportService.findAll(reportType);
    }

    /**
     * Report와 생성 당시 분석한 Area ID를 조회한다.
     *
     * @param reportId 조회할 Report ID
     * @return Report 상세 메타데이터
     */
    @GetMapping("/{reportId}")
    public ReportDetailResponse findById(@PathVariable Long reportId) {
        return reportService.findById(reportId);
    }

    /**
     * 저장된 Markdown을 브라우저에서 읽을 수 있는 본문으로 반환한다.
     *
     * @param reportId 조회할 Report ID
     * @return UTF-8 Markdown 본문
     */
    @GetMapping("/{reportId}/content")
    public ResponseEntity<String> getContent(@PathVariable Long reportId) {
        return ResponseEntity.ok()
                .contentType(MARKDOWN_UTF8)
                .body(reportService.getContent(reportId));
    }

    /**
     * 저장된 Markdown을 영문 안전 파일명으로 다운로드한다.
     *
     * @param reportId 다운로드할 Report ID
     * @return attachment Content-Disposition이 적용된 UTF-8 Markdown
     */
    @GetMapping("/{reportId}/download")
    public ResponseEntity<String> download(@PathVariable Long reportId) {
        ReportDetailResponse report = reportService.findById(reportId);
        String filename = report.reportType().pathName()
                + "-report-" + report.id() + ".md";
        return ResponseEntity.ok()
                .contentType(MARKDOWN_UTF8)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .body(reportService.getContent(reportId));
    }

    /**
     * Storage 객체를 먼저 삭제한 뒤 DB 메타데이터를 삭제한다.
     *
     * @param reportId 삭제할 Report ID
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{reportId}")
    public ResponseEntity<Void> delete(@PathVariable Long reportId) {
        reportService.delete(reportId);
        return ResponseEntity.noContent().build();
    }
}
