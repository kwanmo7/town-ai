package com.townai.line.processing;

import com.townai.common.error.ApiException;
import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.messaging.LineDraftMessageFactory;
import com.townai.line.messaging.LineMessagePurpose;
import com.townai.line.messaging.LineMessagingException;
import com.townai.line.messaging.LinePushClient;
import com.townai.line.messaging.LinePushRequest;
import com.townai.line.messaging.LineRetryKeyFactory;
import com.townai.line.model.LineDraftCommand;
import com.townai.line.model.LineWebhookEventType;
import com.townai.line.model.LineWebhookEventWorkItem;
import com.townai.line.service.LineDraftActionResult;
import com.townai.line.service.LineVisitDraftService;
import com.townai.line.service.LineVisitDraftActionService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * LINE Text Message와 확인·취소 Postback을 처리하고 Push Message로 회신한다.
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
    private final LineRetryKeyFactory retryKeyFactory;
    private final LinePushClient pushClient;

    /**
     * LINE Webhook 이벤트 업무 처리기를 생성한다.
     *
     * @param lineVisitDraftService Text Message Parser와 Draft 저장 Service
     * @param draftActionService Draft 확인·취소 및 Visit 저장 Service
     * @param commandParser Postback Data Parser
     * @param messageFactory 저장된 Draft 기반 Push Message Factory
     * @param retryKeyFactory 결정적 UUIDv5 Retry Key Factory
     * @param pushClient LINE Messaging API Client
     */
    public LineWebhookEventHandlerImpl(
            LineVisitDraftService lineVisitDraftService,
            LineVisitDraftActionService draftActionService,
            LinePostbackCommandParser commandParser,
            LineDraftMessageFactory messageFactory,
            LineRetryKeyFactory retryKeyFactory,
            LinePushClient pushClient
    ) {
        this.lineVisitDraftService = lineVisitDraftService;
        this.draftActionService = draftActionService;
        this.commandParser = commandParser;
        this.messageFactory = messageFactory;
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
            if (workItem.eventType()
                    == LineWebhookEventType.TEXT_MESSAGE) {
                handleTextMessage(workItem);
            } else {
                handlePostback(workItem);
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

    private void handleTextMessage(LineWebhookEventWorkItem workItem) {
        LineVisitDraftEntity draft =
                lineVisitDraftService.getOrCreate(workItem);
        LinePushRequest request = messageFactory.create(draft);
        push(
                workItem,
                LineMessagePurpose.DRAFT_RESULT,
                request
        );
    }

    private void handlePostback(LineWebhookEventWorkItem workItem) {
        LineDraftCommand command = commandParser.parse(
                workItem.postbackData()
        );
        LineDraftActionResult result = draftActionService.execute(
                command,
                workItem.lineUserId()
        );
        LinePushRequest request = new LinePushRequest(
                workItem.lineUserId(),
                java.util.List.of(
                        LinePushRequest.TextMessage.of(result.message())
                )
        );
        push(workItem, result.purpose(), request);
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
