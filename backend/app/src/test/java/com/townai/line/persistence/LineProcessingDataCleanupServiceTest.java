package com.townai.line.persistence;

import com.townai.line.repository.LineVisitDraftRepository;
import com.townai.line.repository.LineWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LineProcessingDataCleanupServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T10:00:00Z");
    private static final Instant CUTOFF =
            Instant.parse("2026-06-25T10:00:00Z");

    private final LineVisitDraftRepository draftRepository =
            mock(LineVisitDraftRepository.class);
    private final LineWebhookEventRepository eventRepository =
            mock(LineWebhookEventRepository.class);
    private final LineProcessingDataCleanupService service =
            new LineProcessingDataCleanupService(
                    draftRepository,
                    eventRepository,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void deletesDraftsBeforeUnreferencedTerminalEvents() {
        when(draftRepository.deleteCreatedBefore(CUTOFF))
                .thenReturn(3);
        when(eventRepository
                .deleteTerminalCreatedBeforeWithoutDraft(CUTOFF))
                .thenReturn(5);

        LineCleanupResult result = service.cleanupExpiredData();

        assertEquals(new LineCleanupResult(3, 5), result);
        InOrder ordered = inOrder(draftRepository, eventRepository);
        ordered.verify(draftRepository).deleteCreatedBefore(CUTOFF);
        ordered.verify(eventRepository)
                .deleteTerminalCreatedBeforeWithoutDraft(CUTOFF);
    }
}
