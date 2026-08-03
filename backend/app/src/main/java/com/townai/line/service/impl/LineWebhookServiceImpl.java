package com.townai.line.service.impl;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.line.dispatcher.LineEventDispatchException;
import com.townai.line.dispatcher.LineEventDispatcher;
import com.townai.line.model.LineWebhookEventPayload;
import com.townai.line.persistence.LineCleanupResult;
import com.townai.line.persistence.LineWebhookEventPersistenceService;
import com.townai.line.persistence.LineProcessingDataCleanupService;
import com.townai.line.service.LineWebhookService;
import com.townai.line.webhook.LineWebhookRequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.util.List;

/**
 * LINE Webhook 수신 흐름을 조율하는 Application Service 구현이다.
 *
 * <p>서명 검증과 이벤트 선별 후 별도 Transaction Service에서 이벤트를 저장한다.
 * 해당 호출이 반환되어 Commit이 완료된 다음 Dispatcher를 실행하므로 비동기 Task가
 * 아직 존재하지 않는 DB Row를 조회하는 상황을 방지한다.</p>
 */
@Service
public class LineWebhookServiceImpl implements LineWebhookService {

    private static final Logger log =
            LoggerFactory.getLogger(LineWebhookServiceImpl.class);

    private final LineWebhookRequestProcessor requestProcessor;
    private final LineWebhookEventPersistenceService persistenceService;
    private final LineEventDispatcher eventDispatcher;
    private final LineProcessingDataCleanupService cleanupService;

    /**
     * LINE Webhook Application Service를 생성한다.
     *
     * @param requestProcessor 원문 서명 검증과 지원 이벤트 선별기
     * @param persistenceService 이벤트 Transaction 저장기
     * @param eventDispatcher Local 또는 Cloud Tasks Dispatcher
     * @param cleanupService 30일 경과 LINE 처리 데이터 정리 Service
     */
    public LineWebhookServiceImpl(
            LineWebhookRequestProcessor requestProcessor,
            LineWebhookEventPersistenceService persistenceService,
            LineEventDispatcher eventDispatcher,
            LineProcessingDataCleanupService cleanupService
    ) {
        this.requestProcessor = requestProcessor;
        this.persistenceService = persistenceService;
        this.eventDispatcher = eventDispatcher;
        this.cleanupService = cleanupService;
    }

    /**
     * 지원 이벤트를 저장하고 요청 순서대로 비동기 처리 경로에 전달한다.
     *
     * <p>비지원 이벤트만 포함된 요청과 빈 이벤트 검증 요청은 아무것도 저장하지
     * 않고 정상 종료한다. 기존 이벤트도 다시 전달해 이전 Dispatcher 실패를
     * 복구하며, 결정적 Task ID와 DB 상태가 중복 처리를 방지한다.</p>
     *
     * @param rawBody 가공하지 않은 HTTP Request Body
     * @param signature {@code X-Line-Signature} Header
     */
    @Override
    public void receive(byte[] rawBody, String signature) {
        List<LineWebhookEventPayload> payloads =
                requestProcessor.verifyAndSelect(rawBody, signature);

        if (payloads.isEmpty()) {
            return;
        }

        cleanupExpiredData();
        try {
            List<String> eventIds = persistenceService.store(payloads);
            eventIds.forEach(eventDispatcher::dispatch);
        } catch (DataAccessException
                 | TransactionException
                 | LineEventDispatchException exception) {
            throw new ApiException(ErrorCode.LINE_EVENT_DISPATCH_ERROR);
        }
    }

    private void cleanupExpiredData() {
        try {
            LineCleanupResult result = cleanupService.cleanupExpiredData();
            if (result.deletedDrafts() > 0
                    || result.deletedEvents() > 0) {
                log.info(
                        "Expired LINE processing data deleted. drafts={}, events={}",
                        result.deletedDrafts(),
                        result.deletedEvents()
                );
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "Expired LINE processing data cleanup failed. exceptionType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
