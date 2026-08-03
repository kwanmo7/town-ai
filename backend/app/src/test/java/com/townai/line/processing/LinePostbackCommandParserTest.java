package com.townai.line.processing;

import com.townai.line.model.LineDraftAction;
import com.townai.line.model.LineDraftCommand;
import com.townai.line.model.LineMenuCommand;
import com.townai.line.model.LineMenuTarget;
import com.townai.line.model.LineCompareToggleCommand;
import com.townai.line.model.LineReportGenerateCommand;
import com.townai.line.model.LineReportTypeCommand;
import com.townai.report.entity.ReportType;

import java.util.List;
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
    void parsesReportSelectionAndGenerationCommands() {
        assertEquals(
                new LineReportTypeCommand(ReportType.AREA),
                parser.parse("action=report-type&reportType=AREA")
        );
        assertEquals(
                new LineCompareToggleCommand(2L, List.of(1L, 3L)),
                parser.parse(
                        "action=compare-toggle&areaId=2&selectedAreaIds=1,3"
                )
        );
        assertEquals(
                new LineReportGenerateCommand(
                        ReportType.AREA,
                        List.of(7L)
                ),
                parser.parse(
                        "action=report-generate&reportType=AREA&areaId=7"
                )
        );
        assertEquals(
                new LineReportGenerateCommand(
                        ReportType.COMPARE,
                        List.of(1L, 3L)
                ),
                parser.parse(
                        "action=report-generate&reportType=COMPARE&areaIds=1,3"
                )
        );
    }

    @Test
    void parsesSupportedMenuTargets() {
        assertEquals(
                new LineMenuCommand(LineMenuTarget.MAIN),
                parser.parse("action=menu&target=main")
        );
        assertEquals(
                new LineMenuCommand(LineMenuTarget.VISIT_REGISTER),
                parser.parse("action=menu&target=visit-register")
        );
        assertEquals(
                new LineMenuCommand(LineMenuTarget.REPORT),
                parser.parse("action=menu&target=report")
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
        assertThrows(
                LineEventHandlingException.class,
                () -> parser.parse("action=menu&target=unknown")
        );
        assertThrows(
                LineEventHandlingException.class,
                () -> parser.parse(
                        "action=menu&target=main&target=report"
                )
        );
    }
}
