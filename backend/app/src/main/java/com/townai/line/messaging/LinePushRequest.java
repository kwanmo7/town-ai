package com.townai.line.messaging;

import java.util.List;

/**
 * LINE Push Message API 요청이다.
 *
 * @param to 메시지를 받을 LINE User ID
 * @param messages 최대 5개의 LINE Message Object
 */
public record LinePushRequest(
        String to,
        List<Object> messages
) {

    /**
     * 외부 변경을 막기 위해 Message 목록을 불변으로 보존한다.
     */
    public LinePushRequest {
        messages = List.copyOf(messages);
    }

    /**
     * LINE Text Message Object이다.
     *
     * @param type 항상 {@code text}
     * @param text 사용자에게 표시할 본문
     */
    public record TextMessage(String type, String text) {

        /**
         * Text Message를 생성한다.
         *
         * @param text 사용자 표시 본문
         * @return LINE Text Message Object
         */
        public static TextMessage of(String text) {
            return new TextMessage("text", text);
        }
    }

}
