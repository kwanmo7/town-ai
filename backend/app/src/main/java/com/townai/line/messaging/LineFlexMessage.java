package com.townai.line.messaging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LINE Flex Message Object이다.
 *
 * <p>Flex Container는 LINE이 확장하는 외부 JSON 스키마이므로 고정 Java 계층으로
 * 복제하지 않고 순서가 보존되는 불변 Map으로 관리한다. Container 생성 책임은
 * {@link LineFlexObjectBuilder}와 각 화면 Message Factory가 담당한다.</p>
 *
 * @param type 항상 {@code flex}
 * @param altText 알림·채팅 목록·인용에서 표시할 대체 문구
 * @param contents Bubble 또는 Carousel Container
 */
public record LineFlexMessage(
        String type,
        String altText,
        Map<String, Object> contents
) {

    private static final int MAX_ALT_TEXT_LENGTH = 1500;

    /**
     * 필수 값과 Container Type을 검증하고 외부 변경을 막는다.
     */
    public LineFlexMessage {
        if (!"flex".equals(type)) {
            throw new IllegalArgumentException(
                    "LINE Flex Message type must be flex."
            );
        }
        if (altText == null
                || altText.isBlank()
                || altText.length() > MAX_ALT_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "LINE Flex Message altText is invalid."
            );
        }
        if (contents == null
                || !("bubble".equals(contents.get("type"))
                || "carousel".equals(contents.get("type")))) {
            throw new IllegalArgumentException(
                    "LINE Flex Message contents must be a container."
            );
        }
        contents = Collections.unmodifiableMap(
                new LinkedHashMap<>(contents)
        );
    }

    /**
     * 검증된 Flex Message를 생성한다.
     *
     * @param altText 대체 문구
     * @param contents Bubble 또는 Carousel Container
     * @return LINE Flex Message Object
     */
    public static LineFlexMessage of(
            String altText,
            Map<String, Object> contents
    ) {
        return new LineFlexMessage("flex", altText, contents);
    }
}
