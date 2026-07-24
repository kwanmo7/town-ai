package com.townai.line.processing;

import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.messaging.LineDraftMessageFactory;
import com.townai.line.messaging.LineMessagePurpose;
import com.townai.line.messaging.LineMessagingException;
import com.townai.line.messaging.LinePushClient;
import com.townai.line.messaging.LinePushRequest;
import com.townai.line.messaging.LineRetryKeyFactory;
import com.townai.line.model.LineWebhookEventType;
import com.townai.line.model.LineWebhookEventWorkItem;
import com.townai.line.model.LineDraftAction;
import com.townai.line.model.LineDraftCommand;
import com.townai.line.service.LineDraftActionResult;
import com.townai.line.service.LineVisitDraftActionService;
import com.townai.line.service.LineVisitDraftService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineWebhookEventHandlerImplTest {

    private final LineVisitDraftService draftService =
            mock(LineVisitDraftService.class);
    private final LineDraftMessageFactory messageFactory =
            mock(LineDraftMessageFactory.class);
    private final LineVisitDraftActionService actionService =
            mock(LineVisitDraftActionService.class);
    private final LinePostbackCommandParser commandParser =
            mock(LinePostbackCommandParser.class);
    private final LineRetryKeyFactory retryKeyFactory =
            mock(LineRetryKeyFactory.class);
    private final LinePushClient pushClient = mock(LinePushClient.class);
    private final LineWebhookEventHandlerImpl handler =
            new LineWebhookEventHandlerImpl(
                    draftService,
                    actionService,
                    commandParser,
                    messageFactory,
                    retryKeyFactory,
                    pushClient
            );

    @Test
    void readinessFollowsChannelAccessTokenConfiguration() {
        when(pushClient.isConfigured()).thenReturn(false, true);

        assertFalse(handler.isReady());
        assertTrue(handler.isReady());
    }

    @Test
    void createsDraftAndPushesDeterministicResult() {
        LineWebhookEventWorkItem workItem = textWorkItem();
        LineVisitDraftEntity draft = mock(LineVisitDraftEntity.class);
        LinePushRequest request = new LinePushRequest(
                "user-1",
                List.of(LinePushRequest.TextMessage.of("초안"))
        );
        UUID retryKey = UUID.fromString(
                "123e4567-e89b-52d3-a456-426614174000"
        );
        when(draftService.getOrCreate(workItem)).thenReturn(draft);
        when(messageFactory.create(draft)).thenReturn(request);
        when(retryKeyFactory.create(
                "event-1",
                LineMessagePurpose.DRAFT_RESULT
        )).thenReturn(retryKey);

        handler.handle(workItem);

        verify(pushClient).push(request, retryKey);
    }

    @Test
    void preservesMessagingRetryClassification() {
        LineWebhookEventWorkItem workItem = textWorkItem();
        LineVisitDraftEntity draft = mock(LineVisitDraftEntity.class);
        LinePushRequest request = new LinePushRequest(
                "user-1",
                List.of(LinePushRequest.TextMessage.of("초안"))
        );
        UUID retryKey = UUID.randomUUID();
        when(draftService.getOrCreate(workItem)).thenReturn(draft);
        when(messageFactory.create(draft)).thenReturn(request);
        when(retryKeyFactory.create(
                "event-1",
                LineMessagePurpose.DRAFT_RESULT
        )).thenReturn(retryKey);
        doThrow(new LineMessagingException(
                "LINE_PUSH_REJECTED",
                false,
                null
        )).when(pushClient).push(request, retryKey);

        LineEventHandlingException exception = assertThrows(
                LineEventHandlingException.class,
                () -> handler.handle(workItem)
        );

        assertEquals("LINE_PUSH_REJECTED", exception.errorCode());
        assertFalse(exception.retryable());
    }

    @Test
    void processesPostbackAndPushesActionResult() {
        LineWebhookEventWorkItem workItem =
                new LineWebhookEventWorkItem(
                        "event-2",
                        "user-1",
                        LineWebhookEventType.POSTBACK,
                        null,
                        "action=confirm&draftId=42",
                        Instant.parse("2026-07-25T09:30:00Z"),
                        1
                );
        LineDraftCommand command = new LineDraftCommand(
                LineDraftAction.CONFIRM,
                42L
        );
        LineDraftActionResult result = new LineDraftActionResult(
                LineMessagePurpose.CONFIRM_RESULT,
                "방문 기록을 저장했습니다. Visit ID: 100"
        );
        UUID retryKey = UUID.fromString(
                "123e4567-e89b-52d3-a456-426614174000"
        );
        when(commandParser.parse(workItem.postbackData()))
                .thenReturn(command);
        when(actionService.execute(command, "user-1"))
                .thenReturn(result);
        when(retryKeyFactory.create(
                "event-2",
                LineMessagePurpose.CONFIRM_RESULT
        )).thenReturn(retryKey);

        handler.handle(workItem);

        org.mockito.ArgumentCaptor<LinePushRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(
                        LinePushRequest.class
                );
        verify(pushClient).push(requestCaptor.capture(), eq(retryKey));
        LinePushRequest.TextMessage message =
                (LinePushRequest.TextMessage) requestCaptor
                        .getValue()
                        .messages()
                        .getFirst();
        assertEquals(result.message(), message.text());
    }

    private LineWebhookEventWorkItem textWorkItem() {
        return new LineWebhookEventWorkItem(
                "event-1",
                "user-1",
                LineWebhookEventType.TEXT_MESSAGE,
                "센터미나미 방문",
                null,
                Instant.parse("2026-07-25T09:00:00Z"),
                1
        );
    }
}
