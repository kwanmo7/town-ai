package com.townai.visit.parser;

import com.townai.common.openai.OpenAiResponse;
import com.townai.common.openai.OpenAiResponsesClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiVisitParserClientTest {

    @Test
    void loadsVisitParserPromptAndStrictSchema() {
        OpenAiResponsesClient responsesClient = mock(OpenAiResponsesClient.class);
        ObjectMapper objectMapper = JsonMapper.builder()
                .findAndAddModules()
                .build();
        when(responsesClient.generateStructured(
                anyString(),
                any(),
                eq("visit_parser_v1"),
                any(JsonNode.class)
        )).thenReturn(new OpenAiResponse("test-model", "{\"warnings\":[]}"));
        OpenAiVisitParserClient client = new OpenAiVisitParserClient(
                responsesClient,
                objectMapper
        );
        VisitParserInput input = new VisitParserInput(
                LocalDate.of(2026, 7, 24),
                "센터미나미 방문",
                List.of()
        );

        String result = client.parse(input, null);

        ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> schema = ArgumentCaptor.forClass(JsonNode.class);
        verify(responsesClient).generateStructured(
                instructions.capture(),
                eq(input),
                eq("visit_parser_v1"),
                schema.capture()
        );
        assertEquals("{\"warnings\":[]}", result);
        assertTrue(instructions.getValue().contains("자연어 방문 평가"));
        assertEquals("object", schema.getValue().path("type").asString());
        assertTrue(schema.getValue().path("properties").has("visitDate"));
        assertTrue(schema.getValue().path("properties").has("warnings"));
    }
}
