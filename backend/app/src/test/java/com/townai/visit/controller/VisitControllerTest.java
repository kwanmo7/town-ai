package com.townai.visit.controller;

import com.townai.common.error.GlobalExceptionHandler;
import com.townai.visit.dto.VisitAreaSummaryResponse;
import com.townai.visit.dto.VisitMutationResponse;
import com.townai.visit.service.VisitService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VisitControllerTest {

    private VisitService visitService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        visitService = mock(VisitService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-24T10:20:30Z"),
                ZoneOffset.UTC
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VisitController(visitService))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .build();
    }

    @Test
    void createsVisitAndReturnsLocation() throws Exception {
        Instant createdAt = Instant.parse("2026-07-24T10:20:30Z");
        when(visitService.create(any())).thenReturn(new VisitMutationResponse(
                1L,
                new VisitAreaSummaryResponse(1L, "센터미나미"),
                LocalDate.of(2026, 7, 24),
                9,
                8,
                7,
                6,
                5,
                "재방문 메모",
                createdAt,
                createdAt
        ));

        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/visits/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.area.name").value("센터미나미"))
                .andExpect(jsonPath("$.visitDate").value("2026-07-24"));
    }

    @Test
    void rejectsMissingRequiredScores() throws Exception {
        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "areaId": 1,
                                  "visitDate": "2026-07-24"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(5));
    }

    @Test
    void parsesAndPassesListFilters() throws Exception {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        when(visitService.findAll(1L, from, to)).thenReturn(List.of());

        mockMvc.perform(get("/api/visits")
                        .queryParam("areaId", "1")
                        .queryParam("from", "2026-07-01")
                        .queryParam("to", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(visitService).findAll(1L, from, to);
    }

    @Test
    void returnsValidationErrorForInvalidQueryDate() throws Exception {
        mockMvc.perform(get("/api/visits")
                        .queryParam("from", "2026-99-99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("from"));
    }

    private String validRequestJson() {
        return """
                {
                  "areaId": 1,
                  "visitDate": "2026-07-24",
                  "atmosphereScore": 9,
                  "infraScore": 8,
                  "cleanScore": 7,
                  "sizeScore": 6,
                  "accessScore": 5,
                  "memo": "재방문 메모"
                }
                """;
    }
}
