package com.townai.line.processing;

import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.messaging.LineDraftMessageFactory;
import com.townai.line.messaging.LineMessagePurpose;
import com.townai.line.messaging.LineMenuMessageFactory;
import com.townai.line.messaging.LineMessagingException;
import com.townai.line.messaging.LinePushClient;
import com.townai.line.messaging.LinePushRequest;
import com.townai.line.messaging.LineRetryKeyFactory;
import com.townai.line.model.LineWebhookEventType;
import com.townai.line.model.LineWebhookEventWorkItem;
import com.townai.line.model.LineDraftAction;
import com.townai.line.model.LineDraftCommand;
import com.townai.line.model.LineMenuCommand;
import com.townai.line.model.LineMenuTarget;
import com.townai.line.model.LineReportGenerateCommand;
import com.townai.line.model.LineReportTypeCommand;
import com.townai.report.entity.ReportType;
import com.townai.line.service.LineDraftActionResult;
import com.townai.line.service.LineReportInteractionService;
import com.townai.line.service.LineVisitDraftActionService;
import com.townai.line.service.LineVisitDraftService;
import com.townai.line.service.LineVisitDraftResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class LineWebhookEventHandlerImplTest {

    private final LineVisitDraftService draftService =
            mock(LineVisitDraftService.class);
    private final LineDraftMessageFactory messageFactory =
            mock(LineDraftMessageFactory.class);
    private final LineMenuMessageFactory menuMessageFactory =
            mock(LineMenuMessageFactory.class);
    private final LineReportInteractionService reportInteractionService =
            mock(LineReportInteractionService.class);
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
                    menuMessageFactory,
                    reportInteractionService,
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
    void pushesMainMenuForFollowEvent() {
        LineWebhookEventWorkItem workItem = new LineWebhookEventWorkItem(
                "event-follow",
                "user-1",
                LineWebhookEventType.FOLLOW,
                null,
                null,
                Instant.parse("2026-07-25T08:00:00Z"),
                1
        );
        LinePushRequest request = new LinePushRequest(
                "user-1",
                List.of(LinePushRequest.TextMessage.of("메뉴"))
        );
        UUID retryKey = UUID.randomUUID();
        when(menuMessageFactory.createMainMenu("user-1"))
                .thenReturn(request);
        when(retryKeyFactory.create(
                "event-follow",
                LineMessagePurpose.MENU_RESULT
        )).thenReturn(retryKey);

        handler.handle(workItem);

        verify(pushClient).push(request, retryKey);
    }

    @Test
    void routesMenuPostbackToRequestedFlexScreen() {
        LineWebhookEventWorkItem workItem = new LineWebhookEventWorkItem(
                "event-menu",
                "user-1",
                LineWebhookEventType.POSTBACK,
                null,
                "action=menu&target=visit-register",
                Instant.parse("2026-07-25T08:30:00Z"),
                1
        );
        LineMenuCommand command = new LineMenuCommand(
                LineMenuTarget.VISIT_REGISTER
        );
        LinePushRequest request = new LinePushRequest(
                "user-1",
                List.of(LinePushRequest.TextMessage.of("등록 안내"))
        );
        UUID retryKey = UUID.randomUUID();
        when(commandParser.parse(workItem.postbackData()))
                .thenReturn(command);
        when(menuMessageFactory.createVisitRegistrationGuide("user-1"))
                .thenReturn(request);
        when(retryKeyFactory.create(
                "event-menu",
                LineMessagePurpose.MENU_RESULT
        )).thenReturn(retryKey);

        handler.handle(workItem);

        verify(pushClient).push(request, retryKey);
    }

    @Test
    void routesAreaReportTypeToTargetSelection() {
        LineWebhookEventWorkItem workItem = postbackWorkItem(
                "event-report-type",
                "action=report-type&reportType=AREA"
        );
        LineReportTypeCommand command = new LineReportTypeCommand(
                ReportType.AREA
        );
        LinePushRequest request = textRequest("지역 선택");
        UUID retryKey = UUID.randomUUID();
        when(commandParser.parse(workItem.postbackData()))
                .thenReturn(command);
        when(reportInteractionService.createSelection(
                "user-1",
                ReportType.AREA
        )).thenReturn(request);
        when(retryKeyFactory.create(
                "event-report-type",
                LineMessagePurpose.REPORT_SELECTION
        )).thenReturn(retryKey);

        handler.handle(workItem);

        verify(pushClient).push(request, retryKey);
    }

    @Test
    void pushesGeneratingThenIdempotentReportResult() {
        LineWebhookEventWorkItem workItem = postbackWorkItem(
                "event-summary",
                "action=report-type&reportType=SUMMARY"
        );
        LineReportTypeCommand command = new LineReportTypeCommand(
                ReportType.SUMMARY
        );
        LineReportGenerateCommand generateCommand =
                new LineReportGenerateCommand(
                        ReportType.SUMMARY,
                        List.of()
                );
        LinePushRequest generating = textRequest("생성 중");
        LinePushRequest result = textRequest("완료");
        UUID generatingKey = UUID.randomUUID();
        UUID resultKey = UUID.randomUUID();
        when(commandParser.parse(workItem.postbackData()))
                .thenReturn(command);
        when(reportInteractionService.createGenerating(
                "user-1",
                ReportType.SUMMARY
        )).thenReturn(generating);
        when(reportInteractionService.generate(
                "user-1",
                "event-summary",
                generateCommand
        )).thenReturn(result);
        when(retryKeyFactory.create(
                "event-summary",
                LineMessagePurpose.REPORT_GENERATING
        )).thenReturn(generatingKey);
        when(retryKeyFactory.create(
                "event-summary",
                LineMessagePurpose.REPORT_RESULT
        )).thenReturn(resultKey);

        handler.handle(workItem);

        var order = org.mockito.Mockito.inOrder(
                pushClient,
                reportInteractionService
        );
        order.verify(pushClient).push(generating, generatingKey);
        order.verify(reportInteractionService).generate(
                "user-1",
                "event-summary",
                generateCommand
        );
        order.verify(pushClient).push(result, resultKey);
    }

    @Test
    void reportsNoDataWithoutSendingGeneratingMessage() {
        LineWebhookEventWorkItem workItem = postbackWorkItem(
                "event-all-empty",
                "action=report-type&reportType=ALL"
        );
        LineReportTypeCommand command = new LineReportTypeCommand(
                ReportType.ALL
        );
        LineReportGenerateCommand generateCommand =
                new LineReportGenerateCommand(ReportType.ALL, List.of());
        LinePushRequest unavailable = textRequest("방문 기록 없음");
        UUID resultKey = UUID.randomUUID();
        when(commandParser.parse(workItem.postbackData()))
                .thenReturn(command);
        when(reportInteractionService.findUnavailableMessage(
                "user-1",
                generateCommand
        )).thenReturn(Optional.of(unavailable));
        when(retryKeyFactory.create(
                "event-all-empty",
                LineMessagePurpose.REPORT_RESULT
        )).thenReturn(resultKey);

        handler.handle(workItem);

        verify(pushClient).push(unavailable, resultKey);
        verify(reportInteractionService, never()).createGenerating(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(reportInteractionService, never()).generate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void pushesExistingReportBeforeMutableAvailabilityCheck() {
        LineWebhookEventWorkItem workItem = postbackWorkItem(
                "event-all-retry",
                "action=report-type&reportType=ALL"
        );
        LineReportTypeCommand command = new LineReportTypeCommand(
                ReportType.ALL
        );
        LineReportGenerateCommand generateCommand =
                new LineReportGenerateCommand(ReportType.ALL, List.of());
        LinePushRequest existing = textRequest("기존 Report");
        UUID resultKey = UUID.randomUUID();
        when(commandParser.parse(workItem.postbackData()))
                .thenReturn(command);
        when(reportInteractionService.findExistingResult(
                "user-1",
                "event-all-retry",
                generateCommand
        )).thenReturn(Optional.of(existing));
        when(retryKeyFactory.create(
                "event-all-retry",
                LineMessagePurpose.REPORT_RESULT
        )).thenReturn(resultKey);

        handler.handle(workItem);

        verify(pushClient).push(existing, resultKey);
        verify(reportInteractionService, never()).findUnavailableMessage(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(reportInteractionService, never()).generate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
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
        when(draftService.getOrCreate(workItem)).thenReturn(
                LineVisitDraftResult.draft(draft)
        );
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
        when(draftService.getOrCreate(workItem)).thenReturn(
                LineVisitDraftResult.draft(draft)
        );
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

    private LineWebhookEventWorkItem postbackWorkItem(
            String eventId,
            String data
    ) {
        return new LineWebhookEventWorkItem(
                eventId,
                "user-1",
                LineWebhookEventType.POSTBACK,
                null,
                data,
                Instant.parse("2026-07-25T09:00:00Z"),
                1
        );
    }

    private LinePushRequest textRequest(String text) {
        return new LinePushRequest(
                "user-1",
                List.of(LinePushRequest.TextMessage.of(text))
        );
    }
}
