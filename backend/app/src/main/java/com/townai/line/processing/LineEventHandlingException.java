package com.townai.line.processing;

/**
 * LINE 이벤트 처리 중 발생한 실패의 재시도 가능 여부와 안전한 오류 코드를
 * 전달하는 내부 예외이다.
 */
public class LineEventHandlingException extends RuntimeException {

    /** DB와 운영 로그에 기록할 민감정보 없는 오류 코드. */
    private final String errorCode;

    /** 동일 이벤트를 다시 처리할지 나타내는 값. */
    private final boolean retryable;

    /**
     * 분류 가능한 LINE 이벤트 처리 오류를 생성한다.
     *
     * @param errorCode DB와 로그에 기록할 민감정보 없는 오류 코드
     * @param retryable 같은 이벤트를 다시 처리할지 여부
     * @param cause 실제 실패 원인
     */
    public LineEventHandlingException(
            String errorCode,
            boolean retryable,
            Throwable cause
    ) {
        super(errorCode, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    /**
     * DB와 운영 로그에 기록할 오류 코드를 반환한다.
     *
     * @return 최대 50자의 안전한 오류 코드
     */
    public String errorCode() {
        return errorCode;
    }

    /**
     * 같은 이벤트를 다시 처리할 가치가 있는지 반환한다.
     *
     * @return 일시적인 실패이면 {@code true}
     */
    public boolean retryable() {
        return retryable;
    }
}
