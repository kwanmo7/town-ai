package com.townai.line.entity;

import com.townai.line.model.LineWebhookEventPayload;
import com.townai.line.model.LineWebhookEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LineWebhookEventEntityTest {

    @Test
    void createsReceivedEventWithoutProcessingState() {
        LineWebhookEventPayload payload = new LineWebhookEventPayload(
                "event-1",
                "user-1",
                LineWebhookEventType.TEXT_MESSAGE,
                "센터미나미 방문",
                null,
                Instant.parse("2026-07-24T10:11:12.987Z")
        );

        LineWebhookEventEntity entity =
                LineWebhookEventEntity.received(payload);

        assertEquals("event-1", entity.getWebhookEventId());
        assertEquals("user-1", entity.getLineUserId());
        assertEquals(
                LineWebhookEventType.TEXT_MESSAGE,
                entity.getEventType()
        );
        assertEquals("센터미나미 방문", entity.getMessageText());
        assertNull(entity.getPostbackData());
        assertEquals(
                LineWebhookEventStatus.RECEIVED,
                entity.getStatus()
        );
        assertEquals(0, entity.getAttemptCount());
        assertEquals(
                Instant.parse("2026-07-24T10:11:12Z"),
                entity.getOccurredAt()
        );
        assertNull(entity.getProcessingStartedAt());
        assertNull(entity.getProcessedAt());
    }
}
