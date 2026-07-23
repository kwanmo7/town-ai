package com.townai.visit.controller;

import com.townai.common.error.GlobalExceptionHandler;
import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.service.VisitDraftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VisitDraftControllerTest {

    private VisitDraftService visitDraftService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        visitDraftService = mock(VisitDraftService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-24T10:20:30Z"),
                ZoneOffset.UTC
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VisitDraftController(visitDraftService))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .build();
    }

    @Test
    void returnsParsedVisitDraft() throws Exception {
        when(visitDraftService.create(any())).thenReturn(new VisitDraftResponse(
                new VisitDraftAreaResponse(1L, "센터미나미"),
                LocalDate.of(2026, 7, 12),
                9,
                null,
                null,
                null,
                7,
                null,
                List.of("일부 점수가 입력되지 않았습니다.")
        ));

        mockMvc.perform(post("/api/visit-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "7월 12일 센터미나미 분위기 9점, 접근성 7점"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area.id").value(1))
                .andExpect(jsonPath("$.area.name").value("센터미나미"))
                .andExpect(jsonPath("$.visitDate").value("2026-07-12"))
                .andExpect(jsonPath("$.atmosphereScore").value(9))
                .andExpect(jsonPath("$.infraScore").isEmpty())
                .andExpect(jsonPath("$.accessScore").value(7))
                .andExpect(jsonPath("$.warnings.length()").value(1));
    }

    @Test
    void rejectsBlankText() throws Exception {
        mockMvc.perform(post("/api/visit-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("text"));
    }
}
