package com.townai.line.persistence;

import com.townai.line.entity.LineWebhookEventEntity;
import com.townai.line.model.LineWebhookEventPayload;
import com.townai.line.model.LineWebhookEventType;
import com.townai.line.repository.LineWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineWebhookEventPersistenceServiceTest {

    private final LineWebhookEventRepository repository =
            mock(LineWebhookEventRepository.class);
    private final LineWebhookEventPersistenceService service =
            new LineWebhookEventPersistenceService(repository);

    @Test
    void storesOnlyNewEventsAndReturnsExistingIdsForRedispatch() {
        LineWebhookEventPayload existingPayload = textPayload(
                "event-existing"
        );
        LineWebhookEventPayload newPayload = textPayload("event-new");
        LineWebhookEventEntity existing =
                LineWebhookEventEntity.received(existingPayload);
        when(repository.findAllById(anyCollection()))
                .thenReturn(List.of(existing));

        List<String> result = service.store(List.of(
                existingPayload,
                newPayload,
                newPayload
        ));

        assertEquals(
                List.of("event-existing", "event-new"),
                result
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LineWebhookEventEntity>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(
                "event-new",
                captor.getValue().getFirst().getWebhookEventId()
        );
    }

    @Test
    void doesNotAccessRepositoryForEmptyRequest() {
        assertEquals(List.of(), service.store(List.of()));

        verify(repository, never()).findAllById(anyCollection());
        verify(repository, never()).saveAll(anyList());
    }

    private LineWebhookEventPayload textPayload(String eventId) {
        return new LineWebhookEventPayload(
                eventId,
                "user-1",
                LineWebhookEventType.TEXT_MESSAGE,
                "방문 기록",
                null,
                Instant.parse("2026-07-24T10:11:12Z")
        );
    }
}
