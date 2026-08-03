package com.townai.line.messaging;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineMenuMessageFactoryTest {

    private final LineMenuMessageFactory factory =
            new LineMenuMessageFactory();
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void createsMainMenuWithRegistrationAndReportActions()
            throws JacksonException {
        LinePushRequest request = factory.createMainMenu("user-1");

        assertFlexRequest(request);
        String json = objectMapper.writeValueAsString(request);
        assertTrue(json.contains("\"text\":\"Town AI\""));
        assertTrue(json.contains(
                "action=menu&target=visit-register"
        ));
        assertTrue(json.contains("action=menu&target=report"));
    }

    @Test
    void createsVisitGuideWithInputExampleAndMenuAction()
            throws JacksonException {
        LinePushRequest request = factory.createVisitRegistrationGuide(
                "user-1"
        );

        assertFlexRequest(request);
        String json = objectMapper.writeValueAsString(request);
        assertTrue(json.contains("방문한 지역의 평가를 자연어로 보내주세요."));
        assertTrue(json.contains("센터미나미를 오늘 방문했어."));
        assertTrue(json.contains("action=menu&target=main"));
    }

    @Test
    void createsReportTypeMenuWithAllSupportedTypes()
            throws JacksonException {
        LinePushRequest request = factory.createReportTypeMenu("user-1");

        assertFlexRequest(request);
        String json = objectMapper.writeValueAsString(request);
        assertTrue(json.contains("reportType=AREA"));
        assertTrue(json.contains("reportType=COMPARE"));
        assertTrue(json.contains("reportType=SUMMARY"));
        assertTrue(json.contains("reportType=ALL"));
    }

    @Test
    void rejectsBlankRecipient() {
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.createMainMenu(" ")
        );
    }

    private void assertFlexRequest(LinePushRequest request) {
        assertEquals("user-1", request.to());
        assertEquals(1, request.messages().size());
        LineFlexMessage message = assertInstanceOf(
                LineFlexMessage.class,
                request.messages().getFirst()
        );
        assertEquals("bubble", message.contents().get("type"));
    }
}
