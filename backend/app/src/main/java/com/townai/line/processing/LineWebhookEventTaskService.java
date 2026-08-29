package com.townai.line.processing;

import com.townai.line.messaging.LineFailureNoticeSender;
import com.townai.line.messaging.LineMessagingException;
import com.townai.line.model.LineWebhookEventWorkItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 내부 Task 요청의 점유, 업무 처리 및 최종 상태 전환을 조율한다.
 *
 * <p>업무 Handler 호출은 Firestore Transaction과 Lease Lock 밖에서 수행한다.
 * Handler가 아직 구현되지 않은 개발 단계에서는 이벤트를 점유하지 않고 재시도를
 * 반환해 Attempt를 소모하거나 이벤트를 최종 실패시키지 않는다.</p>
 */
@Service
public class LineWebhookEventTaskService {

    private static final Logger log =
            LoggerFactory.getLogger(LineWebhookEventTaskService.class);
    private static final String UNEXPECTED_ERROR_CODE =
            "UNEXPECTED_PROCESSING_ERROR";

    private final LineWebhookEventStateService stateService;
    private final ObjectProvider<LineWebhookEventHandler> handlerProvider;
    private final LineFailureNoticeSender failureNoticeSender;

    /**
     * LINE 내부 Task Service를 생성한다.
     *
     * @param stateService 이벤트 Lease 및 상태 전환 Service
     * @param handlerProvider Text·Postback 업무 처리기 Provider
     * @param failureNoticeSender 최종 처리 실패 사용자 안내 Sender
     */
    public LineWebhookEventTaskService(
            LineWebhookEventStateService stateService,
            ObjectProvider<LineWebhookEventHandler> handlerProvider,
            LineFailureNoticeSender failureNoticeSender
    ) {
        this.stateService = stateService;
        this.handlerProvider = handlerProvider;
        this.failureNoticeSender = failureNoticeSender;
    }

    /**
     * 이벤트를 한 번 처리하고 HTTP 응답에 사용할 결과를 반환한다.
     *
     * @param webhookEventId 처리할 LINE Webhook Event ID
     * @return 2xx 승인 또는 5xx 재시도 결과
     */
    public LineEventTaskResult process(String webhookEventId) {
        LineWebhookEventHandler handler = handlerProvider.getIfAvailable();
        if (handler == null || !handler.isReady()) {
            log.warn(
                    "LINE event handler is not available. webhookEventId={}",
                    webhookEventId
            );
            return LineEventTaskResult.RETRY;
        }

        LineEventClaim claim = stateService.claim(webhookEventId);
        return switch (claim.status()) {
            case TERMINAL -> LineEventTaskResult.ACKNOWLEDGED;
            case BUSY, MISSING -> LineEventTaskResult.RETRY;
            case ACQUIRED -> handle(handler, claim.workItem());
        };
    }

    private LineEventTaskResult handle(
            LineWebhookEventHandler handler,
            LineWebhookEventWorkItem workItem
    ) {
        try {
            handler.handle(workItem);
            stateService.complete(
                    workItem.webhookEventId(),
                    workItem.attemptCount()
            );
            return LineEventTaskResult.ACKNOWLEDGED;
        } catch (LineEventHandlingException exception) {
            log.warn(
                    "LINE event processing failed. webhookEventId={}, errorCode={}, retryable={}",
                    workItem.webhookEventId(),
                    exception.errorCode(),
                    exception.retryable()
            );
            return failureResult(
                    workItem,
                    exception.errorCode(),
                    exception.retryable()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Unexpected LINE event processing failure. webhookEventId={}, exceptionType={}",
                    workItem.webhookEventId(),
                    exception.getClass().getSimpleName()
            );
            return failureResult(
                    workItem,
                    UNEXPECTED_ERROR_CODE,
                    true
            );
        }
    }

    private LineEventTaskResult failureResult(
            LineWebhookEventWorkItem workItem,
            String errorCode,
            boolean retryable
    ) {
        LineEventFailureResult result = stateService.fail(
                workItem.webhookEventId(),
                workItem.attemptCount(),
                errorCode,
                retryable
        );
        if (result == LineEventFailureResult.TERMINAL) {
            sendFailureNotice(workItem);
        }
        return result == LineEventFailureResult.RETRY
                ? LineEventTaskResult.RETRY
                : LineEventTaskResult.ACKNOWLEDGED;
    }

    private void sendFailureNotice(LineWebhookEventWorkItem workItem) {
        try {
            failureNoticeSender.send(workItem);
        } catch (LineMessagingException exception) {
            log.warn(
                    "LINE failure notice was not accepted. webhookEventId={}, errorCode={}",
                    workItem.webhookEventId(),
                    exception.errorCode()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Unexpected LINE failure notice error. webhookEventId={}, exceptionType={}",
                    workItem.webhookEventId(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
