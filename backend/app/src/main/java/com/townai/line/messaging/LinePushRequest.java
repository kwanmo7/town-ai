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

    /**
     * 확인 버튼을 포함하는 LINE Template Message Object이다.
     *
     * @param type 항상 {@code template}
     * @param altText Template을 표시할 수 없는 환경의 대체 문구
     * @param template Confirm Template
     */
    public record TemplateMessage(
            String type,
            String altText,
            ConfirmTemplate template
    ) {

        /**
         * Confirm Template Message를 생성한다.
         *
         * @param altText 대체 문구
         * @param template 확인 Template
         * @return LINE Template Message Object
         */
        public static TemplateMessage confirm(
                String altText,
                ConfirmTemplate template
        ) {
            return new TemplateMessage(
                    "template",
                    altText,
                    template
            );
        }
    }

    /**
     * 확인과 취소 두 동작을 제공하는 Confirm Template이다.
     *
     * @param type 항상 {@code confirm}
     * @param text 버튼 위에 표시할 최대 240자 문구
     * @param actions 정확히 두 개의 Postback 동작
     */
    public record ConfirmTemplate(
            String type,
            String text,
            List<PostbackAction> actions
    ) {

        /**
         * Confirm Template의 Action 목록을 불변으로 보존한다.
         */
        public ConfirmTemplate {
            actions = List.copyOf(actions);
        }

        /**
         * Confirm Template을 생성한다.
         *
         * @param text 사용자 확인 문구
         * @param actions 확인·취소 Postback 동작
         * @return LINE Confirm Template
         */
        public static ConfirmTemplate of(
                String text,
                List<PostbackAction> actions
        ) {
            return new ConfirmTemplate("confirm", text, actions);
        }
    }

    /**
     * 사용자가 누르면 Webhook Postback Event를 발생시키는 동작이다.
     *
     * @param type 항상 {@code postback}
     * @param label 버튼에 표시할 문구
     * @param data Webhook의 {@code postback.data}로 돌아올 값
     */
    public record PostbackAction(
            String type,
            String label,
            String data
    ) {

        /**
         * Postback 동작을 생성한다.
         *
         * @param label 버튼 표시 문구
         * @param data Backend가 해석할 Postback Data
         * @return LINE Postback Action
         */
        public static PostbackAction of(
                String label,
                String data
        ) {
            return new PostbackAction("postback", label, data);
        }
    }
}
