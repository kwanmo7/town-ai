package com.townai.line.messaging;

/**
 * LINE Push Message 요청 실패의 안전한 오류 코드와 재시도 가능 여부를 전달한다.
 */
public class LineMessagingException extends RuntimeException {

    /** DB에 기록할 민감정보 없는 오류 코드. */
    private final String errorCode;

    /** 동일 요청을 Retry Key와 함께 다시 보낼지 여부. */
    private final boolean retryable;

    /**
     * LINE Messaging API 실패를 생성한다.
     *
     * @param errorCode 내부 오류 코드
     * @param retryable 일시적인 실패 여부
     * @param cause HTTP 또는 설정 실패 원인
     */
    public LineMessagingException(
            String errorCode,
            boolean retryable,
            Throwable cause
    ) {
        super(errorCode, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    /**
     * DB에 기록할 안전한 오류 코드를 반환한다.
     *
     * @return 최대 50자의 내부 오류 코드
     */
    public String errorCode() {
        return errorCode;
    }

    /**
     * 같은 Push 요청을 다시 시도할지 반환한다.
     *
     * @return Retry Key 재시도가 필요하면 {@code true}
     */
    public boolean retryable() {
        return retryable;
    }
}
