package com.townai.report.generation.ai;

import com.townai.common.openai.OpenAiResponse;
import com.townai.common.openai.OpenAiResponsesClient;
import com.townai.report.entity.ReportType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiReportClientTest {

    @Test
    void usesAllV1StructuredOutputSchema() {
        OpenAiResponsesClient responsesClient = mock(OpenAiResponsesClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiReportClient client = new OpenAiReportClient(
                responsesClient,
                objectMapper
        );
        Object input = new Object();
        when(responsesClient.generateStructured(
                anyString(),
                same(input),
                eq("all_v1"),
                any(JsonNode.class)
        )).thenReturn(new OpenAiResponse("test-model", "{\"overall\":\"result\"}"));

        AiReportResult result = client.generate(ReportType.ALL, input, null);

        assertEquals("test-model", result.model());
        verify(responsesClient).generateStructured(
                anyString(),
                same(input),
                eq("all_v1"),
                any(JsonNode.class)
        );
    }
}
