package com.townai.line.service;

import com.townai.common.error.ApiException;

/**
 * LINE Platform Webhook의 검증, 저장 및 비동기 전달 Use Case이다.
 */
public interface LineWebhookService {

    /**
     * Webhook 원문을 검증하고 지원 이벤트를 저장한 뒤 내부 처리기로 전달한다.
     *
     * @param rawBody 가공하지 않은 HTTP Request Body
     * @param signature {@code X-Line-Signature} Header
     * @throws ApiException 서명·JSON 검증, DB 저장 또는 Dispatcher 전달에 실패한 경우
     */
    void receive(byte[] rawBody, String signature);
}
