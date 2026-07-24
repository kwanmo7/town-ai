package com.townai.line.messaging;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LineRetryKeyFactoryTest {

    private final LineRetryKeyFactory factory =
            new LineRetryKeyFactory();

    @Test
    void createsDeterministicUuidVersionFivePerPurpose() {
        UUID first = factory.create(
                "event-1",
                LineMessagePurpose.DRAFT_RESULT
        );
        UUID same = factory.create(
                "event-1",
                LineMessagePurpose.DRAFT_RESULT
        );
        UUID differentPurpose = factory.create(
                "event-1",
                LineMessagePurpose.CONFIRM_RESULT
        );

        assertEquals(first, same);
        assertNotEquals(first, differentPurpose);
        assertEquals(5, first.version());
        assertEquals(2, first.variant());
    }
}
