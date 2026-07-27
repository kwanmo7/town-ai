package com.townai.common.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-23T10:20:30Z"), ZoneOffset.UTC);
        exceptionHandler = new GlobalExceptionHandler(clock);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    void returnsDocumentedResponseForApiException() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").value("2026-07-23T10:20:30Z"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("AREA_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("지역을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.path").value("/test/not-found"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void returnsMalformedRequestWithoutInternalExceptionDetails() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.path").value("/test/body"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void returnsNotFoundForUnmappedResourceInsteadOfInternalServerError() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/reports/");
        NoResourceFoundException exception =
                new NoResourceFoundException(
                        HttpMethod.GET,
                        "api/reports/",
                        "/api/reports/"
                );

        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleNoResourceFound(exception, request);

        assertEquals(404, response.getStatusCode().value());
        assertEquals(
                ErrorCode.ENDPOINT_NOT_FOUND.name(),
                response.getBody().code()
        );
        assertEquals("/api/reports/", response.getBody().path());
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/not-found")
        void notFound() {
            throw new ApiException(ErrorCode.AREA_NOT_FOUND);
        }

        @PostMapping("/body")
        void body(@RequestBody TestRequest request) {
        }
    }

    record TestRequest(String value) {
    }
}
