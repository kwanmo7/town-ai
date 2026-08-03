package com.townai.line.processing;

import com.townai.line.model.LineDraftAction;
import com.townai.line.model.LineCompareToggleCommand;
import com.townai.line.model.LineDraftCommand;
import com.townai.line.model.LineMenuCommand;
import com.townai.line.model.LineMenuTarget;
import com.townai.line.model.LinePostbackCommand;
import com.townai.line.model.LineReportGenerateCommand;
import com.townai.line.model.LineReportTypeCommand;
import com.townai.report.entity.ReportType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LINE Postback의 Key-Value Data를 내부 명령으로 변환한다.
 */
@Component
public class LinePostbackCommandParser {

    /**
     * LINE Postback 명령 Parser를 생성한다.
     */
    public LinePostbackCommandParser() {
    }

    /**
     * 지원하는 Postback Data를 파싱한다.
     *
     * @param postbackData LINE Webhook Postback Data
     * @return Draft 처리 또는 메뉴 이동 명령
     * @throws LineEventHandlingException 형식 또는 ID가 올바르지 않은 경우
     */
    public LinePostbackCommand parse(String postbackData) {
        Map<String, String> parameters = parseParameters(postbackData);
        String action = parameters.get("action");

        return switch (action == null ? "" : action) {
            case "confirm" -> draftCommand(
                    parameters,
                    LineDraftAction.CONFIRM
            );
            case "cancel" -> draftCommand(
                    parameters,
                    LineDraftAction.CANCEL
            );
            case "menu" -> menuCommand(parameters);
            case "report-type" -> reportTypeCommand(parameters);
            case "compare-toggle" -> compareToggleCommand(parameters);
            case "report-generate" -> reportGenerateCommand(parameters);
            default -> throw invalidPostback();
        };
    }

    private LineReportTypeCommand reportTypeCommand(
            Map<String, String> parameters
    ) {
        requireKeys(parameters, "action", "reportType");
        return new LineReportTypeCommand(reportType(parameters));
    }

    private LineCompareToggleCommand compareToggleCommand(
            Map<String, String> parameters
    ) {
        if (parameters.size() != 2 && parameters.size() != 3) {
            throw invalidPostback();
        }
        if (!parameters.containsKey("action")
                || !parameters.containsKey("areaId")
                || (parameters.size() == 3
                && !parameters.containsKey("selectedAreaIds"))) {
            throw invalidPostback();
        }
        return new LineCompareToggleCommand(
                positiveId(parameters.get("areaId")),
                idList(parameters.get("selectedAreaIds"), 5)
        );
    }

    private LineReportGenerateCommand reportGenerateCommand(
            Map<String, String> parameters
    ) {
        ReportType reportType = reportType(parameters);
        return switch (reportType) {
            case AREA -> {
                requireKeys(
                        parameters,
                        "action",
                        "reportType",
                        "areaId"
                );
                yield new LineReportGenerateCommand(
                        reportType,
                        List.of(positiveId(parameters.get("areaId")))
                );
            }
            case COMPARE -> {
                requireKeys(
                        parameters,
                        "action",
                        "reportType",
                        "areaIds"
                );
                List<Long> areaIds = idList(
                        parameters.get("areaIds"),
                        5
                );
                if (areaIds.size() < 2) {
                    throw invalidPostback();
                }
                yield new LineReportGenerateCommand(reportType, areaIds);
            }
            case SUMMARY, ALL -> throw invalidPostback();
        };
    }

    private ReportType reportType(Map<String, String> parameters) {
        try {
            return ReportType.valueOf(parameters.get("reportType"));
        } catch (NullPointerException | IllegalArgumentException exception) {
            throw invalidPostback();
        }
    }

    private long positiveId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw invalidPostback();
            }
            return id;
        } catch (NullPointerException | NumberFormatException exception) {
            throw invalidPostback();
        }
    }

    private List<Long> idList(String value, int maximumSize) {
        if (value == null) {
            return List.of();
        }
        List<Long> ids = java.util.Arrays.stream(value.split(",", -1))
                .map(this::positiveId)
                .distinct()
                .toList();
        if (ids.isEmpty() || ids.size() > maximumSize) {
            throw invalidPostback();
        }
        return ids;
    }

    private LineDraftCommand draftCommand(
            Map<String, String> parameters,
            LineDraftAction action
    ) {
        requireKeys(parameters, "action", "draftId");
        try {
            long draftId = Long.parseLong(parameters.get("draftId"));
            if (draftId <= 0) {
                throw invalidPostback();
            }
            return new LineDraftCommand(action, draftId);
        } catch (NumberFormatException exception) {
            throw invalidPostback();
        }
    }

    private LineMenuCommand menuCommand(Map<String, String> parameters) {
        requireKeys(parameters, "action", "target");
        LineMenuTarget target = switch (parameters.get("target")) {
            case "main" -> LineMenuTarget.MAIN;
            case "visit-register" -> LineMenuTarget.VISIT_REGISTER;
            case "report" -> LineMenuTarget.REPORT;
            default -> throw invalidPostback();
        };
        return new LineMenuCommand(target);
    }

    private Map<String, String> parseParameters(String postbackData) {
        if (postbackData == null || postbackData.isBlank()) {
            throw invalidPostback();
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        for (String pair : postbackData.split("&", -1)) {
            int separator = pair.indexOf('=');
            if (separator <= 0 || separator == pair.length() - 1) {
                throw invalidPostback();
            }
            String key = pair.substring(0, separator);
            String value = pair.substring(separator + 1);
            if (parameters.putIfAbsent(key, value) != null) {
                throw invalidPostback();
            }
        }
        return Map.copyOf(parameters);
    }

    private void requireKeys(
            Map<String, String> parameters,
            String... expectedKeys
    ) {
        if (parameters.size() != expectedKeys.length) {
            throw invalidPostback();
        }
        for (String key : expectedKeys) {
            if (!parameters.containsKey(key)) {
                throw invalidPostback();
            }
        }
    }

    private LineEventHandlingException invalidPostback() {
        return new LineEventHandlingException(
                "INVALID_POSTBACK_DATA",
                false,
                null
        );
    }
}
