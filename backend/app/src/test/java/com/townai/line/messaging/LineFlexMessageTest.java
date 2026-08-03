package com.townai.line.messaging;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LineFlexMessageTest {

    @Test
    void preservesContainerAndListAsImmutableSnapshots() {
        Map<String, Object> body = LineFlexObjectBuilder.type("box")
                .property("layout", "vertical")
                .property("contents", List.of(
                        LineFlexObjectBuilder.type("text")
                                .property("text", "테스트")
                                .build()
                ))
                .build();
        Map<String, Object> bubble = LineFlexObjectBuilder.type("bubble")
                .property("body", body)
                .build();

        LineFlexMessage message = LineFlexMessage.of(
                "테스트 메시지",
                bubble
        );

        assertEquals("bubble", message.contents().get("type"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> message.contents().put("size", "mega")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> ((List<?>) body.get("contents")).clear()
        );
    }

    @Test
    void rejectsInvalidMessageAndDuplicateProperties() {
        Map<String, Object> text = LineFlexObjectBuilder.type("text")
                .property("text", "invalid container")
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> LineFlexMessage.of("테스트", text)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> LineFlexObjectBuilder.type("text")
                        .property("text", "first")
                        .property("text", "second")
        );
    }
}
