package com.townai.line.processing;

import com.townai.line.entity.LineWebhookEventEntity;
import com.townai.line.entity.LineWebhookEventStatus;
import com.townai.line.model.LineWebhookEventPayload;
import com.townai.line.model.LineWebhookEventType;
import com.townai.line.repository.LineWebhookEventRepository;
import com.townai.persistence.firestore.FirestoreTransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LineWebhookEventStateServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T10:00:00Z");

    private final LineWebhookEventRepository repository =
            mock(LineWebhookEventRepository.class);
    private final FirestoreTransactionRunner transactions =
            mock(FirestoreTransactionRunner.class);
    private final LineWebhookEventStateService service =
            new LineWebhookEventStateService(
                    repository,
                    transactions,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    LineWebhookEventStateServiceTest() {
        when(transactions.execute(
                org.mockito.ArgumentMatchers.<Supplier<Object>>any()
        )).thenAnswer(invocation -> invocation
                .<Supplier<?>>getArgument(0)
                .get());
    }

    @Test
    void claimsReceivedEventAndIncrementsAttempt() {
        LineWebhookEventEntity event = event();
        when(repository.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(event));

        LineEventClaim claim = service.claim("event-1");

        assertEquals(LineEventClaimStatus.ACQUIRED, claim.status());
        assertEquals(1, claim.workItem().attemptCount());
        assertEquals(LineWebhookEventStatus.PROCESSING, event.getStatus());
        assertEquals(NOW, event.getProcessingStartedAt());
        assertNull(event.getLastErrorCode());
    }

    @Test
    void rejectsDuplicateProcessingWhileLeaseIsActive() {
        LineWebhookEventEntity event = event();
        event.startProcessing(NOW.minusSeconds(359));
        when(repository.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(event));

        LineEventClaim claim = service.claim("event-1");

        assertEquals(LineEventClaimStatus.BUSY, claim.status());
        assertEquals(1, event.getAttemptCount());
    }

    @Test
    void reclaimsProcessingEventWhenSixMinuteLeaseExpires() {
        LineWebhookEventEntity event = event();
        event.startProcessing(NOW.minusSeconds(360));
        when(repository.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(event));

        LineEventClaim claim = service.claim("event-1");

        assertEquals(LineEventClaimStatus.ACQUIRED, claim.status());
        assertEquals(2, claim.workItem().attemptCount());
        assertEquals(NOW, event.getProcessingStartedAt());
    }

    @Test
    void doesNotOverwriteNewerAttemptCompletion() {
        LineWebhookEventEntity event = event();
        event.startProcessing(NOW.minusSeconds(360));
        event.startProcessing(NOW);
        when(repository.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(event));

        boolean completed = service.complete("event-1", 1);

        assertFalse(completed);
        assertEquals(LineWebhookEventStatus.PROCESSING, event.getStatus());
        assertEquals(2, event.getAttemptCount());
    }

    @Test
    void releasesRetryableFailureBeforeMaximumAttempt() {
        LineWebhookEventEntity event = event();
        event.startProcessing(NOW);
        when(repository.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(event));

        LineEventFailureResult result = service.fail(
                "event-1",
                1,
                "OPENAI_API_ERROR",
                true
        );

        assertEquals(LineEventFailureResult.RETRY, result);
        assertEquals(LineWebhookEventStatus.RECEIVED, event.getStatus());
        assertEquals("OPENAI_API_ERROR", event.getLastErrorCode());
        assertNull(event.getProcessingStartedAt());
    }

    @Test
    void terminatesRetryableFailureOnFifthAttempt() {
        LineWebhookEventEntity event = event();
        for (int attempt = 1; attempt <= 5; attempt++) {
            event.startProcessing(NOW);
            if (attempt < 5) {
                event.releaseForRetry("TEMPORARY_ERROR");
            }
        }
        when(repository.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(event));

        LineEventFailureResult result = service.fail(
                "event-1",
                5,
                "OPENAI_API_ERROR",
                true
        );

        assertEquals(LineEventFailureResult.TERMINAL, result);
        assertEquals(LineWebhookEventStatus.FAILED, event.getStatus());
        assertEquals(NOW, event.getProcessedAt());
    }

    @Test
    void completesCurrentAttempt() {
        LineWebhookEventEntity event = event();
        event.startProcessing(NOW);
        when(repository.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(event));

        assertTrue(service.complete("event-1", 1));
        assertEquals(LineWebhookEventStatus.COMPLETED, event.getStatus());
        assertEquals(NOW, event.getProcessedAt());
        assertNull(event.getProcessingStartedAt());
    }

    private LineWebhookEventEntity event() {
        return LineWebhookEventEntity.received(
                new LineWebhookEventPayload(
                        "event-1",
                        "user-1",
                        LineWebhookEventType.TEXT_MESSAGE,
                        "방문 기록",
                        null,
                        NOW.minusSeconds(600)
                )
        );
    }
}
