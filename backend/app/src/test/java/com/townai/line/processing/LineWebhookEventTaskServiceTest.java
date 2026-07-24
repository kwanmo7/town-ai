package com.townai.line.processing;

import com.townai.line.messaging.LineFailureNoticeSender;
import com.townai.line.messaging.LineMessagingException;
import com.townai.line.model.LineWebhookEventWorkItem;
import com.townai.line.model.LineWebhookEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class LineWebhookEventTaskServiceTest {

    private final LineWebhookEventStateService stateService =
            mock(LineWebhookEventStateService.class);
    private final ObjectProvider<LineWebhookEventHandler> handlerProvider =
            mock(ObjectProvider.class);
    private final LineWebhookEventHandler handler =
            mock(LineWebhookEventHandler.class);
    private final LineFailureNoticeSender failureNoticeSender =
            mock(LineFailureNoticeSender.class);
    private final LineWebhookEventTaskService service =
            new LineWebhookEventTaskService(
                    stateService,
                    handlerProvider,
                    failureNoticeSender
            );

    @Test
    void retriesWithoutClaimWhenHandlerIsNotReady() {
        when(handlerProvider.getIfAvailable()).thenReturn(null);

        LineEventTaskResult result = service.process("event-1");

        assertEquals(LineEventTaskResult.RETRY, result);
        verify(stateService, never()).claim("event-1");
    }

    @Test
    void handlesAndCompletesAcquiredEvent() {
        LineWebhookEventWorkItem workItem = workItem();
        when(handlerProvider.getIfAvailable()).thenReturn(handler);
        when(handler.isReady()).thenReturn(true);
        when(stateService.claim("event-1"))
                .thenReturn(LineEventClaim.acquired(workItem));

        LineEventTaskResult result = service.process("event-1");

        assertEquals(LineEventTaskResult.ACKNOWLEDGED, result);
        verify(handler).handle(workItem);
        verify(stateService).complete("event-1", 1);
    }

    @Test
    void retriesBusyLeaseWithoutCallingHandler() {
        when(handlerProvider.getIfAvailable()).thenReturn(handler);
        when(handler.isReady()).thenReturn(true);
        when(stateService.claim("event-1")).thenReturn(
                LineEventClaim.withoutWork(LineEventClaimStatus.BUSY)
        );

        LineEventTaskResult result = service.process("event-1");

        assertEquals(LineEventTaskResult.RETRY, result);
        verify(handler, never()).handle(workItem());
    }

    @Test
    void returnsRetryWhenRetryableHandlingFailureIsReleased() {
        LineWebhookEventWorkItem workItem = workItem();
        when(handlerProvider.getIfAvailable()).thenReturn(handler);
        when(handler.isReady()).thenReturn(true);
        when(stateService.claim("event-1"))
                .thenReturn(LineEventClaim.acquired(workItem));
        doThrow(new LineEventHandlingException(
                "OPENAI_API_ERROR",
                true,
                new IllegalStateException()
        )).when(handler).handle(workItem);
        when(stateService.fail(
                "event-1",
                1,
                "OPENAI_API_ERROR",
                true
        )).thenReturn(LineEventFailureResult.RETRY);

        LineEventTaskResult result = service.process("event-1");

        assertEquals(LineEventTaskResult.RETRY, result);
    }

    @Test
    void acknowledgesPermanentHandlingFailure() {
        LineWebhookEventWorkItem workItem = workItem();
        when(handlerProvider.getIfAvailable()).thenReturn(handler);
        when(handler.isReady()).thenReturn(true);
        when(stateService.claim("event-1"))
                .thenReturn(LineEventClaim.acquired(workItem));
        doThrow(new LineEventHandlingException(
                "INVALID_POSTBACK",
                false,
                new IllegalArgumentException()
        )).when(handler).handle(workItem);
        when(stateService.fail(
                "event-1",
                1,
                "INVALID_POSTBACK",
                false
        )).thenReturn(LineEventFailureResult.TERMINAL);

        LineEventTaskResult result = service.process("event-1");

        assertEquals(LineEventTaskResult.ACKNOWLEDGED, result);
        verify(failureNoticeSender).send(workItem);
    }

    @Test
    void acknowledgesTerminalFailureWhenFailureNoticeAlsoFails() {
        LineWebhookEventWorkItem workItem = workItem();
        when(handlerProvider.getIfAvailable()).thenReturn(handler);
        when(handler.isReady()).thenReturn(true);
        when(stateService.claim("event-1"))
                .thenReturn(LineEventClaim.acquired(workItem));
        doThrow(new LineEventHandlingException(
                "OPENAI_API_ERROR",
                true,
                new IllegalStateException()
        )).when(handler).handle(workItem);
        when(stateService.fail(
                "event-1",
                1,
                "OPENAI_API_ERROR",
                true
        )).thenReturn(LineEventFailureResult.TERMINAL);
        doThrow(new LineMessagingException(
                "LINE_MESSAGING_API_ERROR",
                true,
                null
        )).when(failureNoticeSender).send(workItem);

        LineEventTaskResult result = service.process("event-1");

        assertEquals(LineEventTaskResult.ACKNOWLEDGED, result);
        verify(failureNoticeSender).send(workItem);
    }

    private LineWebhookEventWorkItem workItem() {
        return new LineWebhookEventWorkItem(
                "event-1",
                "user-1",
                LineWebhookEventType.TEXT_MESSAGE,
                "방문 기록",
                null,
                Instant.parse("2026-07-25T09:00:00Z"),
                1
        );
    }
}
