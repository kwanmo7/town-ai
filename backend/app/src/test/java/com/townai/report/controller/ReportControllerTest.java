package com.townai.report.controller;

import com.townai.common.error.GlobalExceptionHandler;
import com.townai.report.dto.ReportDetailResponse;
import com.townai.report.dto.ReportResponse;
import com.townai.report.entity.ReportType;
import com.townai.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {

    private ReportService reportService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-24T10:20:30Z"),
                ZoneOffset.UTC
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReportController(reportService))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .build();
    }

    @Test
    void createsReportAndReturnsLocation() throws Exception {
        when(reportService.create(any())).thenReturn(new ReportResponse(
                10L,
                ReportType.AREA,
                "test-model",
                "area-v1",
                Instant.parse("2026-07-24T10:20:30Z")
        ));

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportType": "AREA",
                                  "areaIds": [1]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/reports/10"))
                .andExpect(jsonPath("$.reportType").value("AREA"))
                .andExpect(jsonPath("$.promptVersion").value("area-v1"));
    }

    @Test
    void rejectsMissingReportType() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void downloadsMarkdownWithAttachmentFilename() throws Exception {
        when(reportService.findById(10L)).thenReturn(new ReportDetailResponse(
                10L,
                ReportType.COMPARE,
                List.of(1L, 2L),
                "test-model",
                "compare-v1",
                Instant.parse("2026-07-24T10:20:30Z")
        ));
        when(reportService.getContent(10L)).thenReturn("# 지역 비교 리포트\n");

        mockMvc.perform(get("/api/reports/10/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"compare-report-10.md\""
                ))
                .andExpect(content().string("# 지역 비교 리포트\n"));
    }
}
