package com.townai.line.processing;

import com.townai.line.model.LineDraftAction;
import com.townai.line.model.LineDraftCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinePostbackCommandParserTest {

    private final LinePostbackCommandParser parser =
            new LinePostbackCommandParser();

    @Test
    void parsesConfirmAndCancelCommands() {
        assertEquals(
                new LineDraftCommand(LineDraftAction.CONFIRM, 42L),
                parser.parse("action=confirm&draftId=42")
        );
        assertEquals(
                new LineDraftCommand(LineDraftAction.CANCEL, 42L),
                parser.parse("action=cancel&draftId=42")
        );
    }

    @Test
    void rejectsUnsupportedOrOverflowingDataPermanently() {
        LineEventHandlingException unsupported = assertThrows(
                LineEventHandlingException.class,
                () -> parser.parse("action=delete&draftId=42")
        );
        assertEquals(
                "INVALID_POSTBACK_DATA",
                unsupported.errorCode()
        );
        assertFalse(unsupported.retryable());

        assertThrows(
                LineEventHandlingException.class,
                () -> parser.parse(
                        "action=confirm&draftId=999999999999999999999"
                )
        );
    }
}
