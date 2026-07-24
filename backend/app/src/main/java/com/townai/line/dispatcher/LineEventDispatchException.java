package com.townai.line.dispatcher;

/**
 * 저장된 LINE 이벤트를 Local 또는 Cloud Tasks 처리 경로로 전달하지 못한 경우의
 * 내부 예외이다.
 *
 * <p>Webhook Application Service에서
 * {@link com.townai.common.error.ErrorCode#LINE_EVENT_DISPATCH_ERROR}로 변환한다.</p>
 */
public class LineEventDispatchException extends RuntimeException {

    /**
     * 원인이 된 예외를 보존해 Dispatcher 오류를 생성한다.
     *
     * @param message 운영 로그에 사용할 민감정보 없는 설명
     * @param cause 실제 전달 실패 원인
     */
    public LineEventDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
