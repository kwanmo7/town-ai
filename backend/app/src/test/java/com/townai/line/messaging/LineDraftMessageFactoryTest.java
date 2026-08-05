package com.townai.line.messaging;

import com.townai.area.entity.AreaEntity;
import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.visit.dto.VisitDraftResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineDraftMessageFactoryTest {

    private final LineDraftMessageFactory factory =
            new LineDraftMessageFactory();
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void createsSingleFlexMessageWithConfirmEditAndCancelActions()
            throws JacksonException {
        LineVisitDraftEntity draft = completeDraft();
        ReflectionTestUtils.setField(draft, "id", 42L);

        LinePushRequest request = factory.create(draft);

        assertEquals("user-1", request.to());
        assertEquals(1, request.messages().size());
        LineFlexMessage flex = assertInstanceOf(
                LineFlexMessage.class,
                request.messages().getFirst()
        );
        assertEquals("flex", flex.type());
        assertEquals("bubble", flex.contents().get("type"));
        assertEquals(
                "센터미나미 방문 기록 초안을 확인해주세요.",
                flex.altText()
        );

        String json = objectMapper.writeValueAsString(request);
        assertTrue(json.contains("\"지역\""));
        assertTrue(json.contains("\"센터미나미\""));
        assertTrue(json.contains("\"생활 인프라\""));
        assertTrue(json.contains("\"9 / 10\""));
        assertTrue(json.contains("\"type\":\"postback\""));
        assertTrue(json.contains(
                "\"data\":\"action=confirm&draftId=42\""
        ));
        assertTrue(json.contains(
                "\"data\":\"action=cancel&draftId=42\""
        ));
        assertTrue(json.contains(
                "\"data\":\"action=edit&draftId=42\""
        ));
        assertTrue(json.contains("\"displayText\":\"저장\""));
    }

    @Test
    void createsNeedsInputFlexMessageWithoutUnsupportedActions()
            throws JacksonException {
        VisitDraftResponse response = new VisitDraftResponse(
                null,
                LocalDate.parse("2026-07-24"),
                8,
                null,
                7,
                6,
                9,
                null,
                List.of("지역과 생활 인프라 점수를 확인해주세요.")
        );
        LineVisitDraftEntity draft = LineVisitDraftEntity.create(
                "event-1",
                "user-1",
                null,
                false,
                response,
                response.warnings(),
                Instant.parse("2026-07-25T10:00:00Z")
        );
        ReflectionTestUtils.setField(draft, "id", 11L);

        LinePushRequest request = factory.create(draft);

        assertEquals(1, request.messages().size());
        LineFlexMessage flex = assertInstanceOf(
                LineFlexMessage.class,
                request.messages().getFirst()
        );
        assertEquals(
                "방문 기록에 추가 입력이 필요합니다.",
                flex.altText()
        );
        String json = objectMapper.writeValueAsString(request);
        assertTrue(json.contains("추가 입력이 필요합니다"));
        assertTrue(json.contains("\"지역\""));
        assertTrue(json.contains("\"미확정\""));
        assertTrue(json.contains("\"생활 인프라\""));
        assertTrue(json.contains("\"미입력\""));
        assertTrue(json.contains(
                "지역과 생활 인프라 점수를 확인해주세요."
        ));
        assertFalse(json.contains("action=confirm"));
        assertFalse(json.contains("action=cancel"));
        assertTrue(json.contains("action=edit&draftId=11"));
        assertFalse(json.contains("\"inputOption\":\"openKeyboard\""));
        assertTrue(json.contains("action=menu&target=main"));
    }

    @Test
    void rejectsConfirmableDraftWithoutPersistedId() {
        LineVisitDraftEntity draft = completeDraft();

        assertThrows(
                IllegalStateException.class,
                () -> factory.create(draft)
        );
    }

    @Test
    void createsSaveResultWithContinueAndMainMenuActions()
            throws JacksonException {
        LinePushRequest request = factory.createSaveResult(
                "user-1",
                "방문 기록을 저장했습니다. Visit ID: 100"
        );

        String json = objectMapper.writeValueAsString(request);

        assertTrue(json.contains("방문 기록을 저장했습니다"));
        assertTrue(json.contains("Visit ID: 100"));
        assertTrue(json.contains("action=menu&target=visit-register"));
        assertTrue(json.contains("action=menu&target=main"));
    }

    @Test
    void hidesLiteralNullAndGenericLocationPlaceholders()
            throws JacksonException {
        VisitDraftResponse response = new VisitDraftResponse(
                new VisitDraftAreaResponse(
                        null,
                        "센터미나미",
                        "광역권",
                        "null",
                        null
                ),
                LocalDate.parse("2026-07-24"),
                8,
                8,
                8,
                7,
                7,
                "방문 메모",
                List.of("위치 정보를 확인해주세요.")
        );
        LineVisitDraftEntity draft = LineVisitDraftEntity.create(
                "event-placeholder",
                "user-1",
                null,
                true,
                response,
                response.warnings(),
                Instant.parse("2026-07-25T10:00:00Z")
        );
        ReflectionTestUtils.setField(draft, "id", 12L);

        String json = objectMapper.writeValueAsString(factory.create(draft));

        assertFalse(json.contains("광역권"));
        assertFalse(json.contains("\"text\":\"null\""));
        assertFalse(json.contains("\"text\":\"위치\""));
    }

    private LineVisitDraftEntity completeDraft() {
        AreaEntity area = AreaEntity.builder()
                .name("센터미나미")
                .prefecture("가나가와현")
                .city("요코하마시")
                .station("센터미나미역")
                .build();
        VisitDraftResponse response = new VisitDraftResponse(
                new VisitDraftAreaResponse(
                        1L,
                        "센터미나미",
                        "가나가와현",
                        "요코하마시",
                        "센터미나미역"
                ),
                LocalDate.parse("2026-07-24"),
                8,
                9,
                7,
                6,
                9,
                "걷기 편했음",
                List.of()
        );
        return LineVisitDraftEntity.create(
                "event-1",
                "user-1",
                area,
                false,
                response,
                response.warnings(),
                Instant.parse("2026-07-25T10:00:00Z")
        );
    }
}
