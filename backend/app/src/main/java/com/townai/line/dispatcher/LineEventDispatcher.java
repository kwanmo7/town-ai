package com.townai.line.dispatcher;

/**
 * 저장이 완료된 LINE Webhook 이벤트를 내부 비동기 처리 경로로 전달한다.
 */
public interface LineEventDispatcher {

    /**
     * 하나의 Webhook 이벤트를 처리 대상으로 전달한다.
     *
     * <p>동일 ID를 여러 번 전달해도 구현체 또는 내부 처리기가 중복 실행 결과를
     * 만들지 않아야 한다.</p>
     *
     * @param webhookEventId DB에 저장된 LINE Webhook Event ID
     * @throws LineEventDispatchException 안전한 전달을 확인할 수 없는 경우
     */
    void dispatch(String webhookEventId);
}
