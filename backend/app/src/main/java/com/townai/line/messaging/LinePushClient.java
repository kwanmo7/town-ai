package com.townai.line.messaging;

import java.util.UUID;

/**
 * LINE Messaging API Push 요청 Port이다.
 */
public interface LinePushClient {

    /**
     * Channel Access Token이 설정되어 실제 Push를 보낼 수 있는지 반환한다.
     *
     * @return Push 준비가 완료됐으면 {@code true}
     */
    boolean isConfigured();

    /**
     * 최초 요청부터 결정적 Retry Key를 포함해 Push Message를 보낸다.
     *
     * @param request 수신자와 Message Object
     * @param retryKey LINE 중복 수락 방지 UUID
     * @throws LineMessagingException 요청이 수락되지 않은 경우
     */
    void push(LinePushRequest request, UUID retryKey);
}
