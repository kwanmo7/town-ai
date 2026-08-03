package com.townai.line.messaging;

import java.util.List;
import java.util.Map;

/**
 * Town AI LINE 화면에서 반복 사용하는 Flex Component 생성 규칙이다.
 */
final class LineFlexComponents {

    private LineFlexComponents() {
    }

    /**
     * 자식 Component를 배치하는 Box Builder를 생성한다.
     *
     * @param layout {@code vertical} 또는 {@code horizontal}
     * @param contents 자식 Component 목록
     * @return Box Builder
     */
    static LineFlexObjectBuilder box(
            String layout,
            List<?> contents
    ) {
        return LineFlexObjectBuilder.type("box")
                .property("layout", layout)
                .property("contents", contents);
    }

    /**
     * 필수 본문이 설정된 Text Builder를 생성한다.
     *
     * @param value 사용자 표시 문구
     * @return Text Builder
     */
    static LineFlexObjectBuilder text(String value) {
        return LineFlexObjectBuilder.type("text")
                .property("text", value);
    }

    /**
     * 앞 Component와 간격을 두는 구분선을 생성한다.
     *
     * @param margin LINE Flex 간격 Keyword
     * @return Separator Component
     */
    static Map<String, Object> separator(String margin) {
        return LineFlexObjectBuilder.type("separator")
                .property("margin", margin)
                .build();
    }

    /**
     * Flex Button을 생성한다.
     *
     * @param style {@code primary}, {@code secondary} 또는 {@code link}
     * @param color 선택 Button 색상. 기본 색상이면 {@code null}
     * @param action Button 동작
     * @return Button Component
     */
    static Map<String, Object> button(
            String style,
            String color,
            Map<String, Object> action
    ) {
        LineFlexObjectBuilder button = LineFlexObjectBuilder.type("button")
                .property("style", style)
                .property("height", "sm")
                .property("action", action);
        if (color != null) {
            button.property("color", color);
        }
        return button.build();
    }

    /**
     * Webhook Postback과 사용자 표시 문구를 함께 정의한다.
     *
     * @param label Button 문구
     * @param data Backend 명령 Data
     * @param displayText 버튼 선택 시 채팅에 표시할 문구
     * @return Postback Action
     */
    static Map<String, Object> postback(
            String label,
            String data,
            String displayText
    ) {
        return LineFlexObjectBuilder.type("postback")
                .property("label", label)
                .property("data", data)
                .property("displayText", displayText)
                .build();
    }
}
