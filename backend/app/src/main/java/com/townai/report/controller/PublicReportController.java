package com.townai.report.controller;

import com.townai.report.dto.ReportDetailResponse;
import com.townai.report.link.ReportLinkAction;
import com.townai.report.link.ReportSignedLinkService;
import com.townai.report.service.ReportService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Firebase 로그인을 사용할 수 없는 LINE 브라우저에 만료 서명 Report 링크를 제공한다.
 */
@RestController
@RequestMapping("/api/public/reports")
public class PublicReportController {

    private static final MediaType MARKDOWN_UTF8 =
            new MediaType("text", "markdown", StandardCharsets.UTF_8);

    private final ReportService reportService;
    private final ReportSignedLinkService signedLinkService;

    /**
     * 공개 Report Controller를 생성한다.
     *
     * @param reportService Report 조회 Service
     * @param signedLinkService 만료 시각과 HMAC 서명 검증 Service
     */
    public PublicReportController(
            ReportService reportService,
            ReportSignedLinkService signedLinkService
    ) {
        this.reportService = reportService;
        this.signedLinkService = signedLinkService;
    }

    /**
     * 서명이 유효한 경우 Markdown 본문을 브라우저에 표시한다.
     *
     * @param reportId Report ID
     * @param expires Unix Epoch Seconds 만료 시각
     * @param signature URL-safe Base64 HMAC 서명
     * @return UTF-8 Markdown 본문
     */
    @GetMapping("/{reportId}/content")
    public ResponseEntity<String> getContent(
            @PathVariable Long reportId,
            @RequestParam long expires,
            @RequestParam String signature
    ) {
        signedLinkService.verify(
                reportId,
                ReportLinkAction.CONTENT,
                expires,
                signature
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex, nofollow")
                .contentType(MARKDOWN_UTF8)
                .body(reportService.getContent(reportId));
    }

    /**
     * 서명이 유효한 경우 Markdown 파일을 다운로드한다.
     *
     * @param reportId Report ID
     * @param expires Unix Epoch Seconds 만료 시각
     * @param signature URL-safe Base64 HMAC 서명
     * @return attachment Content-Disposition이 적용된 UTF-8 Markdown
     */
    @GetMapping("/{reportId}/download")
    public ResponseEntity<String> download(
            @PathVariable Long reportId,
            @RequestParam long expires,
            @RequestParam String signature
    ) {
        signedLinkService.verify(
                reportId,
                ReportLinkAction.DOWNLOAD,
                expires,
                signature
        );
        ReportDetailResponse report = reportService.findById(reportId);
        String filename = report.reportType().pathName()
                + "-report-" + report.id() + ".md";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex, nofollow")
                .contentType(MARKDOWN_UTF8)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .body(reportService.getContent(reportId));
    }
}
