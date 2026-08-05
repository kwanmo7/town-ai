package com.townai.line.service;

import com.townai.line.messaging.LineMessagePurpose;

/**
 * Draft 저장·수정·취소 처리 후 사용자에게 Push할 안정적인 결과이다.
 *
 * @param purpose Retry Key를 구분할 메시지 용도
 * @param message 사용자에게 표시할 결과 문구
 * @param visitSaved Visit 저장이 완료돼 저장 완료 화면을 표시해야 하면 {@code true}
 */
public record LineDraftActionResult(
        LineMessagePurpose purpose,
        String message,
        boolean visitSaved
) {

    /**
     * 저장 완료가 아닌 일반 Draft 동작 결과를 생성한다.
     *
     * @param purpose Retry Key를 구분할 메시지 용도
     * @param message 사용자에게 표시할 결과 문구
     */
    public LineDraftActionResult(
            LineMessagePurpose purpose,
            String message
    ) {
        this(purpose, message, false);
    }
}
