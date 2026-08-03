package com.townai.line.messaging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LINE Flex Container와 Component를 JSON 속성 단위로 구성한다.
 *
 * <p>속성 순서를 보존하고 null 속성을 만들지 않으며, 같은 속성을 두 번 지정하는
 * 실수를 즉시 거부한다. 반환 Map은 불변 Snapshot이므로 Factory 밖에서 변경할 수
 * 없다.</p>
 */
public final class LineFlexObjectBuilder {

    private final Map<String, Object> properties = new LinkedHashMap<>();

    private LineFlexObjectBuilder(String type) {
        property("type", requireNonBlank(type, "type"));
    }

    /**
     * 지정한 LINE Object Type으로 Builder를 시작한다.
     *
     * @param type {@code bubble}, {@code box}, {@code text} 등의 Type
     * @return 새 Builder
     */
    public static LineFlexObjectBuilder type(String type) {
        return new LineFlexObjectBuilder(type);
    }

    /**
     * 선택 속성을 추가한다.
     *
     * <p>목록 값은 외부 변경을 막기 위해 불변 목록으로 복사한다.</p>
     *
     * @param name LINE JSON 속성 이름
     * @param value null이 아닌 속성 값
     * @return 현재 Builder
     */
    public LineFlexObjectBuilder property(String name, Object value) {
        String propertyName = requireNonBlank(name, "property name");
        if (value == null) {
            throw new IllegalArgumentException(
                    "LINE Flex property value must not be null."
            );
        }
        Object safeValue = value instanceof List<?> list
                ? List.copyOf(list)
                : value;
        if (properties.putIfAbsent(propertyName, safeValue) != null) {
            throw new IllegalArgumentException(
                    "LINE Flex property is duplicated: " + propertyName
            );
        }
        return this;
    }

    /**
     * 현재 속성의 불변 Snapshot을 반환한다.
     *
     * @return JSON 직렬화 가능한 LINE Object
     */
    public Map<String, Object> build() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(properties)
        );
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
