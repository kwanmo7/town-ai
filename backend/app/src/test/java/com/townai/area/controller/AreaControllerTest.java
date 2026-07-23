package com.townai.area.controller;

import com.townai.area.dto.AreaDetailResponse;
import com.townai.area.service.AreaService;
import com.townai.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AreaControllerTest {

    private AreaService areaService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        areaService = mock(AreaService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-23T10:20:30Z"),
                ZoneOffset.UTC
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AreaController(areaService))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .build();
    }

    @Test
    void createsAreaAndReturnsLocation() throws Exception {
        Instant createdAt = Instant.parse("2026-07-23T10:20:30Z");
        when(areaService.create(any())).thenReturn(new AreaDetailResponse(
                1L,
                "센터미나미",
                "가나가와현",
                "요코하마시",
                "센터미나미역",
                createdAt,
                createdAt
        ));

        mockMvc.perform(post("/api/areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "센터미나미",
                                  "prefecture": "가나가와현",
                                  "city": "요코하마시",
                                  "station": "센터미나미역"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/areas/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("센터미나미"))
                .andExpect(jsonPath("$.createdAt").value("2026-07-23T10:20:30Z"));
    }

    @Test
    void rejectsBlankRequiredFields() throws Exception {
        mockMvc.perform(post("/api/areas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "prefecture": "",
                                  "city": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }
}
