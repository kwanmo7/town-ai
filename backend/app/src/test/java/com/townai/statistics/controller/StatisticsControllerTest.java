package com.townai.statistics.controller;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.common.error.GlobalExceptionHandler;
import com.townai.statistics.model.AreaStatistics;
import com.townai.statistics.model.OverallStatistics;
import com.townai.statistics.model.ScoreAverages;
import com.townai.statistics.model.TopArea;
import com.townai.statistics.model.TopFive;
import com.townai.statistics.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatisticsControllerTest {

    private StatisticsService statisticsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        statisticsService = mock(StatisticsService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-24T10:20:30Z"),
                ZoneOffset.UTC
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StatisticsController(statisticsService))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .build();
    }

    @Test
    void returnsOverallStatistics() throws Exception {
        List<TopArea> atmosphereTop = List.of(
                new TopArea(1L, "센터미나미", 9.4)
        );
        when(statisticsService.getOverallStatistics()).thenReturn(
                new OverallStatistics(
                        6,
                        12,
                        new ScoreAverages(8.4, 8.1, 8.8, 8.3, 7.2),
                        new TopFive(
                                atmosphereTop,
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()
                        )
                )
        );

        mockMvc.perform(get("/api/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaCount").value(6))
                .andExpect(jsonPath("$.visitCount").value(12))
                .andExpect(jsonPath("$.averageScores.clean").value(8.8))
                .andExpect(jsonPath("$.top5.atmosphere[0].areaId").value(1))
                .andExpect(jsonPath("$.top5.atmosphere[0].score").value(9.4))
                .andExpect(jsonPath("$.top5.infra").isEmpty());
    }

    @Test
    void returnsAreaStatistics() throws Exception {
        when(statisticsService.getAreaStatistics(1L)).thenReturn(
                new AreaStatistics(
                        1L,
                        "센터미나미",
                        3,
                        new ScoreAverages(8.7, 9.0, 8.3, 8.0, 7.7)
                )
        );

        mockMvc.perform(get("/api/areas/1/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").value(1))
                .andExpect(jsonPath("$.areaName").value("센터미나미"))
                .andExpect(jsonPath("$.visitCount").value(3))
                .andExpect(jsonPath("$.averageScores.access").value(7.7));
    }

    @Test
    void returnsNotFoundForMissingOrDeletedArea() throws Exception {
        when(statisticsService.getAreaStatistics(99L))
                .thenThrow(new ApiException(ErrorCode.AREA_NOT_FOUND));

        mockMvc.perform(get("/api/areas/99/statistics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AREA_NOT_FOUND"));
    }
}
