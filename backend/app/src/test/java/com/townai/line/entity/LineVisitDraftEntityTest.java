package com.townai.line.entity;

import com.townai.area.entity.AreaEntity;
import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.visit.dto.VisitDraftResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LineVisitDraftEntityTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T10:00:00Z");

    @Test
    void createsAwaitingConfirmationWhenRequiredValuesExist() {
        AreaEntity area = area();
        VisitDraftResponse response = completeResponse();

        LineVisitDraftEntity draft = LineVisitDraftEntity.create(
                "event-1",
                "user-1",
                area,
                response,
                response.warnings(),
                NOW
        );

        assertEquals(
                LineVisitDraftStatus.AWAITING_CONFIRMATION,
                draft.getStatus()
        );
        assertEquals(area, draft.getArea());
        assertEquals(
                Instant.parse("2026-07-26T10:00:00Z"),
                draft.getExpiresAt()
        );
        assertEquals(List.of(), draft.getWarnings());
    }

    @Test
    void createsNeedsInputWhenRequiredValueIsMissing() {
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
                NOW
        );

        assertEquals(
                LineVisitDraftStatus.NEEDS_INPUT,
                draft.getStatus()
        );
        assertNull(draft.getArea());
        assertNull(draft.getInfraScore());
    }

    private VisitDraftResponse completeResponse() {
        return new VisitDraftResponse(
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
    }

    private AreaEntity area() {
        return AreaEntity.builder()
                .name("센터미나미")
                .prefecture("가나가와현")
                .city("요코하마시")
                .station("센터미나미역")
                .build();
    }
}
