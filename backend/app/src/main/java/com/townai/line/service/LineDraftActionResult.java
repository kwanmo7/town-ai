package com.townai.line.service;

import com.townai.line.messaging.LineMessagePurpose;

/**
 * Draft 확인·취소 처리 후 사용자에게 Push할 안정적인 결과이다.
 *
 * @param purpose Retry Key를 구분할 메시지 용도
 * @param message 사용자에게 표시할 결과 문구
 */
public record LineDraftActionResult(
        LineMessagePurpose purpose,
        String message
) {
}
