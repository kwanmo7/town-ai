package com.townai.common.error;

/**
 * 예상 가능한 애플리케이션 오류를 공통 API 오류 응답으로 전달한다.
 *
 * <p>Controller 밖의 계층은 HTTP 상태를 직접 다루지 않고 {@link ErrorCode}만 선택한다.
 * {@link GlobalExceptionHandler}가 코드에 정의된 상태와 메시지를 응답으로 변환한다.</p>
 */
public class ApiException extends RuntimeException {

    /** HTTP 상태와 클라이언트용 오류 식별자를 결정한다. */
    private final ErrorCode errorCode;

    /**
     * 오류 코드에 정의된 기본 메시지로 예외를 생성한다.
     *
     * @param errorCode 응답 상태와 기본 메시지를 가진 오류 코드
     */
    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    /**
     * 오류 코드의 상태는 유지하면서 이번 오류에 사용할 메시지를 지정한다.
     *
     * @param errorCode 응답 상태를 결정할 오류 코드
     * @param message 공통 오류 응답에 포함할 메시지
     */
    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 이 예외에 연결된 오류 코드를 반환한다.
     *
     * @return 이 예외를 공통 오류 응답으로 변환할 때 사용할 오류 코드
     */
    public ErrorCode errorCode() {
        return errorCode;
    }
}
