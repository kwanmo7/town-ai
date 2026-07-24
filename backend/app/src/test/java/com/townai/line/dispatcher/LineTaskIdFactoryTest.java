package com.townai.line.dispatcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineTaskIdFactoryTest {

    private final LineTaskIdFactory factory = new LineTaskIdFactory();

    @Test
    void createsDeterministicSafeTaskId() {
        String first = factory.create("01J3M7Y8A/unsafe:event");
        String second = factory.create("01J3M7Y8A/unsafe:event");
        String different = factory.create("different-event");

        assertEquals(first, second);
        assertNotEquals(first, different);
        assertTrue(first.matches("^line-[0-9a-f]{64}$"));
    }
}
