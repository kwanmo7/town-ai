package com.townai.line.service;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.line.dispatcher.LineEventDispatchException;
import com.townai.line.dispatcher.LineEventDispatcher;
import com.townai.line.model.LineWebhookEventPayload;
import com.townai.line.model.LineWebhookEventType;
import com.townai.line.persistence.LineWebhookEventPersistenceService;
import com.townai.line.persistence.LineProcessingDataCleanupService;
import com.townai.line.service.impl.LineWebhookServiceImpl;
import com.townai.line.webhook.LineWebhookRequestProcessor;
import com.townai.persistence.firestore.FirestorePersistenceException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineWebhookServiceImplTest {

    private final LineWebhookRequestProcessor requestProcessor =
            mock(LineWebhookRequestProcessor.class);
    private final LineWebhookEventPersistenceService persistenceService =
            mock(LineWebhookEventPersistenceService.class);
    private final LineEventDispatcher dispatcher =
            mock(LineEventDispatcher.class);
    private final LineProcessingDataCleanupService cleanupService =
            mock(LineProcessingDataCleanupService.class);
    private final LineWebhookService service = new LineWebhookServiceImpl(
            requestProcessor,
            persistenceService,
            dispatcher,
            cleanupService
    );

    @Test
    void storesBeforeDispatchingEveryEventInRequestOrder() {
        byte[] body = "{}".getBytes();
        List<LineWebhookEventPayload> payloads = List.of(
                payload("event-1"),
                payload("event-2")
        );
        when(requestProcessor.verifyAndSelect(body, "signature"))
                .thenReturn(payloads);
        when(persistenceService.store(payloads))
                .thenReturn(List.of("event-1", "event-2"));

        service.receive(body, "signature");

        var ordered = inOrder(
                cleanupService,
                persistenceService,
                dispatcher
        );
        ordered.verify(cleanupService).cleanupExpiredData();
        ordered.verify(persistenceService).store(payloads);
        ordered.verify(dispatcher).dispatch("event-1");
        ordered.verify(dispatcher).dispatch("event-2");
    }

    @Test
    void acceptsEmptyVerificationRequestWithoutDispatch() {
        byte[] body = "{\"events\":[]}".getBytes();
        when(requestProcessor.verifyAndSelect(body, "signature"))
                .thenReturn(List.of());

        service.receive(body, "signature");

        verify(cleanupService, never()).cleanupExpiredData();
        verify(persistenceService, never()).store(List.of());
        verify(dispatcher, never()).dispatch("event-1");
    }

    @Test
    void continuesStorageAndDispatchWhenCleanupFails() {
        byte[] body = "{}".getBytes();
        List<LineWebhookEventPayload> payloads =
                List.of(payload("event-1"));
        when(requestProcessor.verifyAndSelect(body, "signature"))
                .thenReturn(payloads);
        when(cleanupService.cleanupExpiredData()).thenThrow(
                new FirestorePersistenceException(
                        "cleanup database unavailable",
                        new IllegalStateException()
                )
        );
        when(persistenceService.store(payloads))
                .thenReturn(List.of("event-1"));

        service.receive(body, "signature");

        verify(persistenceService).store(payloads);
        verify(dispatcher).dispatch("event-1");
    }

    @Test
    void convertsDatabaseFailureToDispatchApiError() {
        byte[] body = "{}".getBytes();
        List<LineWebhookEventPayload> payloads =
                List.of(payload("event-1"));
        when(requestProcessor.verifyAndSelect(body, "signature"))
                .thenReturn(payloads);
        when(persistenceService.store(payloads)).thenThrow(
                new FirestorePersistenceException(
                        "database unavailable",
                        new IllegalStateException()
                )
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.receive(body, "signature")
        );

        assertEquals(
                ErrorCode.LINE_EVENT_DISPATCH_ERROR,
                exception.errorCode()
        );
        verify(dispatcher, never()).dispatch("event-1");
    }

    @Test
    void convertsDispatcherFailureToDispatchApiError() {
        byte[] body = "{}".getBytes();
        List<LineWebhookEventPayload> payloads =
                List.of(payload("event-1"));
        when(requestProcessor.verifyAndSelect(body, "signature"))
                .thenReturn(payloads);
        when(persistenceService.store(payloads))
                .thenReturn(List.of("event-1"));
        doThrow(new LineEventDispatchException(
                "failed",
                new IllegalStateException()
        )).when(dispatcher).dispatch("event-1");

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.receive(body, "signature")
        );

        assertEquals(
                ErrorCode.LINE_EVENT_DISPATCH_ERROR,
                exception.errorCode()
        );
    }

    private LineWebhookEventPayload payload(String eventId) {
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
