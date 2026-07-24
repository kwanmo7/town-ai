package com.townai.line.processing;

import com.townai.line.model.LineWebhookEventWorkItem;

/**
 * 점유된 LINE 이벤트의 Text Message 또는 Postback 업무 처리를 수행한다.
 *
 * <p>구현체는 처리 결과를 DB에 먼저 저장하고 LINE Push 요청이 수락된 뒤에만
 * 정상 반환해야 한다. 이벤트 자체의 완료·재시도 상태 전환은 Task Service가
 * 담당한다.</p>
 */
public interface LineWebhookEventHandler {

    /**
     * 실제 이벤트를 처리하는 데 필요한 Secret과 의존성이 준비됐는지 반환한다.
     *
     * @return 이벤트 점유를 시작해도 되면 {@code true}
     */
    boolean isReady();

    /**
     * 하나의 점유된 이벤트를 처리한다.
     *
     * @param workItem 현재 처리 시도가 소유한 이벤트 Snapshot
     * @throws LineEventHandlingException 분류 가능한 업무 또는 외부 API 실패
     */
    void handle(LineWebhookEventWorkItem workItem);
}
