package com.townai.report.controller;

import com.townai.common.error.GlobalExceptionHandler;
import com.townai.report.dto.ReportDetailResponse;
import com.townai.report.entity.ReportType;
import com.townai.report.link.ReportLinkAction;
import com.townai.report.link.ReportSignedLinkService;
import com.townai.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicReportControllerTest {

    private ReportService reportService;
    private ReportSignedLinkService signedLinkService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        signedLinkService = mock(ReportSignedLinkService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-26T01:00:00Z"),
                ZoneOffset.UTC
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PublicReportController(
                        reportService,
                        signedLinkService
                ))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .build();
    }

    @Test
    void returnsMarkdownAfterVerifyingSignedContentLink() throws Exception {
        when(reportService.getContent(10L)).thenReturn("# 지역 리포트\n");

        mockMvc.perform(get("/api/public/reports/10/content")
                        .queryParam("expires", "1790384400")
                        .queryParam("signature", "valid-signature"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(content().string("# 지역 리포트\n"));

        verify(signedLinkService).verify(
                10L,
                ReportLinkAction.CONTENT,
                1790384400L,
                "valid-signature"
        );
    }

    @Test
    void downloadsMarkdownAfterVerifyingSignedDownloadLink() throws Exception {
        when(reportService.findById(10L)).thenReturn(new ReportDetailResponse(
                10L,
                ReportType.ALL,
                List.of(1L, 2L),
                "test-model",
                "all-v1",
                Instant.parse("2026-08-26T01:00:00Z")
        ));
        when(reportService.getContent(10L)).thenReturn("# 전체 상세 분석\n");

        mockMvc.perform(get("/api/public/reports/10/download")
                        .queryParam("expires", "1790384400")
                        .queryParam("signature", "valid-signature"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"all-report-10.md\""
                ));

        verify(signedLinkService).verify(
                10L,
                ReportLinkAction.DOWNLOAD,
                1790384400L,
                "valid-signature"
        );
    }
}
