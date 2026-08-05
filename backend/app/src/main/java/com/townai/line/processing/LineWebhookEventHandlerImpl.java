package com.townai.line.processing;

import com.townai.common.error.ApiException;
import com.townai.line.messaging.LineDraftMessageFactory;
import com.townai.line.messaging.LineMessagePurpose;
import com.townai.line.messaging.LineMenuMessageFactory;
import com.townai.line.messaging.LineMessagingException;
import com.townai.line.messaging.LinePushClient;
import com.townai.line.messaging.LinePushRequest;
import com.townai.line.messaging.LineRetryKeyFactory;
import com.townai.line.model.LineDraftCommand;
import com.townai.line.model.LineCompareToggleCommand;
import com.townai.line.model.LineMenuCommand;
import com.townai.line.model.LinePostbackCommand;
import com.townai.line.model.LineReportGenerateCommand;
import com.townai.line.model.LineReportTypeCommand;
import com.townai.line.model.LineWebhookEventType;
import com.townai.line.model.LineWebhookEventWorkItem;
import com.townai.line.service.LineDraftActionResult;
import com.townai.line.service.LineReportInteractionService;
import com.townai.line.service.LineVisitDraftService;
import com.townai.line.service.LineVisitDraftResult;
import com.townai.line.service.LineVisitDraftActionService;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * LINE Follow, Text Message와 지원 Postback을 처리하고 Push Message로 회신한다.
 *
 * <p>처리 결과가 저장된 후에만 Push를 전송하며, Push가 수락돼 정상 반환된 경우
 * Task Service가 Webhook 이벤트를 {@code COMPLETED}로 전환한다.</p>
 */
@Component
public class LineWebhookEventHandlerImpl
        implements LineWebhookEventHandler {

    private final LineVisitDraftService lineVisitDraftService;
    private final LineVisitDraftActionService draftActionService;
    private final LinePostbackCommandParser commandParser;
    private final LineDraftMessageFactory messageFactory;
    private final LineMenuMessageFactory menuMessageFactory;
    private final LineReportInteractionService reportInteractionService;
    private final LineRetryKeyFactory retryKeyFactory;
    private final LinePushClient pushClient;

    /**
     * LINE Webhook 이벤트 업무 처리기를 생성한다.
     *
     * @param lineVisitDraftService Text Message Parser와 Draft 저장 Service
     * @param draftActionService Draft 저장·수정·취소 처리 Service
     * @param commandParser Postback Data Parser
     * @param messageFactory 저장된 Draft 기반 Push Message Factory
     * @param menuMessageFactory Follow와 메뉴 이동용 Flex Message Factory
     * @param reportInteractionService Report 선택과 생성 상호작용 Service
     * @param retryKeyFactory 결정적 UUIDv5 Retry Key Factory
     * @param pushClient LINE Messaging API Client
     */
    public LineWebhookEventHandlerImpl(
            LineVisitDraftService lineVisitDraftService,
            LineVisitDraftActionService draftActionService,
            LinePostbackCommandParser commandParser,
            LineDraftMessageFactory messageFactory,
            LineMenuMessageFactory menuMessageFactory,
            LineReportInteractionService reportInteractionService,
            LineRetryKeyFactory retryKeyFactory,
            LinePushClient pushClient
    ) {
        this.lineVisitDraftService = lineVisitDraftService;
        this.draftActionService = draftActionService;
        this.commandParser = commandParser;
        this.messageFactory = messageFactory;
        this.menuMessageFactory = menuMessageFactory;
        this.reportInteractionService = reportInteractionService;
        this.retryKeyFactory = retryKeyFactory;
        this.pushClient = pushClient;
    }

    /**
     * Channel Access Token이 설정된 경우에만 이벤트 점유를 허용한다.
     *
     * @return LINE Push를 보낼 수 있으면 {@code true}
     */
    @Override
    public boolean isReady() {
        return pushClient.isConfigured();
    }

    /**
     * Text Message Draft를 저장하고 같은 결과를 안전하게 Push한다.
     *
     * @param workItem 현재 처리 시도가 소유한 이벤트 Snapshot
     */
    @Override
    public void handle(LineWebhookEventWorkItem workItem) {
        try {
            switch (workItem.eventType()) {
                case FOLLOW -> handleFollow(workItem);
                case TEXT_MESSAGE -> handleTextMessage(workItem);
                case POSTBACK -> handlePostback(workItem);
            }
        } catch (LineMessagingException exception) {
            throw new LineEventHandlingException(
                    exception.errorCode(),
                    exception.retryable(),
                    exception
            );
        } catch (ApiException exception) {
            throw new LineEventHandlingException(
                    exception.errorCode().name(),
                    true,
                    exception
            );
        }
    }

    private void handleFollow(LineWebhookEventWorkItem workItem) {
        push(
                workItem,
                LineMessagePurpose.MENU_RESULT,
                menuMessageFactory.createMainMenu(workItem.lineUserId())
        );
    }

    private void handleTextMessage(LineWebhookEventWorkItem workItem) {
        LineVisitDraftResult result =
                lineVisitDraftService.getOrCreate(workItem);
        LinePushRequest request = result.hasDraft()
                ? messageFactory.create(result.draft())
                : messageFactory.createNotice(
                        workItem.lineUserId(),
                        result.notice()
                );
        push(
                workItem,
                LineMessagePurpose.DRAFT_RESULT,
                request
        );
    }

    private void handlePostback(LineWebhookEventWorkItem workItem) {
        LinePostbackCommand command = commandParser.parse(
                workItem.postbackData()
        );
        if (command instanceof LineMenuCommand menuCommand) {
            handleMenu(workItem, menuCommand);
            return;
        }
        if (command instanceof LineReportTypeCommand reportTypeCommand) {
            handleReportType(workItem, reportTypeCommand);
            return;
        }
        if (command instanceof LineCompareToggleCommand toggleCommand) {
            push(
                    workItem,
                    LineMessagePurpose.REPORT_SELECTION,
                    reportInteractionService.toggleCompareSelection(
                            workItem.lineUserId(),
                            toggleCommand
                    )
            );
            return;
        }
        if (command instanceof LineReportGenerateCommand generateCommand) {
            generateReport(workItem, generateCommand);
            return;
        }
        LineDraftCommand draftCommand = (LineDraftCommand) command;
        LineDraftActionResult result = draftActionService.execute(
                draftCommand,
                workItem.lineUserId()
        );
        LinePushRequest request = result.visitSaved()
                ? messageFactory.createSaveResult(
                        workItem.lineUserId(),
                        result.message()
                )
                : messageFactory.createNotice(
                        workItem.lineUserId(),
                        result.message()
                );
        push(workItem, result.purpose(), request);
    }

    private void handleReportType(
            LineWebhookEventWorkItem workItem,
            LineReportTypeCommand command
    ) {
        switch (command.reportType()) {
            case AREA, COMPARE -> push(
                    workItem,
                    LineMessagePurpose.REPORT_SELECTION,
                    reportInteractionService.createSelection(
                            workItem.lineUserId(),
                            command.reportType()
                    )
            );
            case SUMMARY, ALL -> generateReport(
                    workItem,
                    new LineReportGenerateCommand(
                            command.reportType(),
                            java.util.List.of()
                    )
            );
        }
    }

    private void generateReport(
            LineWebhookEventWorkItem workItem,
            LineReportGenerateCommand command
    ) {
        Optional<LinePushRequest> existing =
                reportInteractionService.findExistingResult(
                        workItem.lineUserId(),
                        workItem.webhookEventId(),
                        command
                );
        if (existing.isPresent()) {
            push(
                    workItem,
                    LineMessagePurpose.REPORT_RESULT,
                    existing.get()
            );
            return;
        }
        Optional<LinePushRequest> unavailable =
                reportInteractionService.findUnavailableMessage(
                        workItem.lineUserId(),
                        command
                );
        if (unavailable.isPresent()) {
            push(
                    workItem,
                    LineMessagePurpose.REPORT_RESULT,
                    unavailable.get()
            );
            return;
        }
        push(
                workItem,
                LineMessagePurpose.REPORT_GENERATING,
                reportInteractionService.createGenerating(
                        workItem.lineUserId(),
                        command.reportType()
                )
        );
        push(
                workItem,
                LineMessagePurpose.REPORT_RESULT,
                reportInteractionService.generate(
                        workItem.lineUserId(),
                        workItem.webhookEventId(),
                        command
                )
        );
    }

    private void handleMenu(
            LineWebhookEventWorkItem workItem,
            LineMenuCommand command
    ) {
        LinePushRequest request = switch (command.target()) {
            case MAIN -> menuMessageFactory.createMainMenu(
                    workItem.lineUserId()
            );
            case VISIT_REGISTER ->
                    menuMessageFactory.createVisitRegistrationGuide(
                            workItem.lineUserId()
                    );
            case REPORT -> menuMessageFactory.createReportTypeMenu(
                    workItem.lineUserId()
            );
        };
        push(workItem, LineMessagePurpose.MENU_RESULT, request);
    }

    private void push(
            LineWebhookEventWorkItem workItem,
            LineMessagePurpose purpose,
            LinePushRequest request
    ) {
        UUID retryKey = retryKeyFactory.create(
                workItem.webhookEventId(),
                purpose
        );
        pushClient.push(request, retryKey);
    }
}
