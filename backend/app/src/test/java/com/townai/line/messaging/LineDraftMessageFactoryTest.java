package com.townai.line.messaging;

import com.townai.area.entity.AreaEntity;
import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.visit.dto.VisitDraftResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineDraftMessageFactoryTest {

    private final LineDraftMessageFactory factory =
            new LineDraftMessageFactory();

    @Test
    void addsConfirmAndCancelActionsForCompleteDraft() {
        LineVisitDraftEntity draft = completeDraft();
        ReflectionTestUtils.setField(draft, "id", 42L);

        LinePushRequest request = factory.create(draft);

        assertEquals("user-1", request.to());
        assertEquals(2, request.messages().size());
        LinePushRequest.TextMessage text = assertInstanceOf(
                LinePushRequest.TextMessage.class,
                request.messages().getFirst()
        );
        assertTrue(text.text().contains("지역: 센터미나미"));
        assertTrue(text.text().contains("생활 인프라: 9/10"));

        LinePushRequest.TemplateMessage template = assertInstanceOf(
                LinePushRequest.TemplateMessage.class,
                request.messages().get(1)
        );
        assertEquals(2, template.template().actions().size());
        assertEquals(
                "action=confirm&draftId=42",
                template.template().actions().getFirst().data()
        );
        assertEquals(
                "action=cancel&draftId=42",
                template.template().actions().get(1).data()
        );
    }

    @Test
    void omitsConfirmationActionsForIncompleteDraft() {
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
                response,
                response.warnings(),
                Instant.parse("2026-07-25T10:00:00Z")
        );

        LinePushRequest request = factory.create(draft);

        assertEquals(1, request.messages().size());
        LinePushRequest.TextMessage text = assertInstanceOf(
                LinePushRequest.TextMessage.class,
                request.messages().getFirst()
        );
        assertTrue(text.text().contains("지역: 미확정"));
        assertTrue(text.text().contains("생활 인프라: 미입력"));
        assertTrue(text.text().contains("자연어 평가를 다시 보내주세요"));
        assertFalse(text.text().contains("action=confirm"));
    }

    private LineVisitDraftEntity completeDraft() {
        AreaEntity area = AreaEntity.builder()
                .name("센터미나미")
                .prefecture("가나가와현")
                .city("요코하마시")
                .station("센터미나미역")
                .build();
        VisitDraftResponse response = new VisitDraftResponse(
                new VisitDraftAreaResponse(1L, "센터미나미"),
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
                response,
                response.warnings(),
                Instant.parse("2026-07-25T10:00:00Z")
        );
    }
}
