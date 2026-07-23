package com.townai.visit.parser.validation;

/**
 * AI Parser 출력이 Backend 검증 계약을 위반했음을 나타내는 내부 제어 예외이다.
 *
 * <p>첫 실패는 교정 지시사항으로 사용되고, 두 번째 실패는
 * {@link com.townai.common.error.ErrorCode#OPENAI_API_ERROR}로 변환된다.</p>
 */
public class InvalidVisitDraftOutputException extends RuntimeException {

    /**
     * 검증 실패 사유로 내부 제어 예외를 생성한다.
     *
     * @param message 모델에 다시 전달할 수 있는 구체적인 검증 실패 사유
     */
    public InvalidVisitDraftOutputException(String message) {
        super(message);
    }
}
