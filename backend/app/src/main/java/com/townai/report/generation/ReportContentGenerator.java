package com.townai.report.generation;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.report.entity.ReportType;
import com.townai.report.generation.ReportDataAssembler.AllInput;
import com.townai.report.generation.ReportDataAssembler.AreaInput;
import com.townai.report.generation.ReportDataAssembler.CompareAreaInput;
import com.townai.report.generation.ReportDataAssembler.CompareInput;
import com.townai.report.generation.ReportDataAssembler.ScoreAverages;
import com.townai.report.generation.ReportDataAssembler.SummaryInput;
import com.townai.report.generation.ReportDataAssembler.TopArea;
import com.townai.report.generation.ai.AiReportResult;
import com.townai.report.generation.ai.ReportAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 원본 출력을 유형별 계약으로 검증하고 저장 가능한 Markdown으로 변환한다.
 *
 * <p>SUMMARY·COMPARE는 Structured Output의 AI 문장과 Backend 계산 표를 조립한다.
 * AREA·ALL은 AI Markdown의 제목 순서, 대상 Area, 체크리스트와 사용자 전제를
 * 검증한다. 첫 출력이 형식 또는 분량 계약을 위반하면 같은 Prompt 입력으로 한 번만
 * 교정 요청하며, 두 번째 실패는 외부 AI 오류로 변환한다.</p>
 */
@Component
public class ReportContentGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportContentGenerator.class);

    // 사용자용 Report에 Backend 내부 필드명이 그대로 노출되는 것을 차단한다.
    private static final Pattern INTERNAL_SCORE_FIELD_PATTERN = Pattern.compile(
            "(?i)(?<![a-z0-9_])"
                    + "(atmosphere(?:Score)?|infra(?:Score)?|clean(?:Score)?"
                    + "|size(?:Score)?|access(?:Score)?)"
                    + "(?![a-z0-9_])"
    );

    // 거의 전면 재택근무인 사용자의 기본 전제와 맞지 않는 통근 중심 문구를 탐지한다.
    private static final Pattern DAILY_COMMUTE_PATTERN = Pattern.compile(
            "(?:출|퇴)\\s*근|통근"
    );

    // 사용자가 원하지 않은 '피로도 기록' 지시를 더 자연스러운 확인·비교 표현으로 유도한다.
    private static final Pattern FATIGUE_RECORDING_PATTERN = Pattern.compile(
            "(?:피로(?:도|감)?)[^\\r\\n]{0,20}기록"
                    + "|기록[^\\r\\n]{0,20}(?:피로(?:도|감)?)"
    );

    private final ReportAiClient reportAiClient;
    private final ObjectMapper objectMapper;

    /**
     * Report 출력 생성기와 유형별 Validator를 구성한다.
     *
     * @param reportAiClient AI 모델 호출 Port
     * @param objectMapper Structured Output을 내부 Record로 변환할 ObjectMapper
     */
    public ReportContentGenerator(
            ReportAiClient reportAiClient,
            ObjectMapper objectMapper
    ) {
        this.reportAiClient = reportAiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * AI 출력을 생성하고 유형별 검증을 통과한 최종 Markdown을 반환한다.
     *
     * @param data 검증된 대상 Area와 유형별 Prompt 입력
     * @return 실제 모델명, Prompt 버전과 최종 Markdown
     * @throws ApiException AI 호출에 실패하거나 두 번 연속 출력 계약을 위반한 경우
     */
    public GeneratedReportContent generate(ReportGenerationData data) {
        AiReportResult firstResult = reportAiClient.generate(
                data.reportType(),
                data.promptInput(),
                null
        );
        try {
            return toFinalContent(data, firstResult);
        } catch (InvalidReportOutputException firstFailure) {
            log.warn(
                    "Report output validation failed. Retrying correction once. type={}, reason={}",
                    data.reportType(),
                    firstFailure.getMessage()
            );
            AiReportResult correctedResult = reportAiClient.generate(
                    data.reportType(),
                    data.promptInput(),
                    firstFailure.getMessage()
            );
            try {
                return toFinalContent(data, correctedResult);
            } catch (InvalidReportOutputException secondFailure) {
                log.error(
                        "Corrected Report output validation failed. type={}, reason={}",
                        data.reportType(),
                        secondFailure.getMessage()
                );
                throw new ApiException(ErrorCode.OPENAI_API_ERROR);
            }
        }
    }

    /**
     * AI 결과를 유형별 Renderer 또는 Markdown Validator로 전달하고 공통 계약을 검사한다.
     */
    private GeneratedReportContent toFinalContent(
            ReportGenerationData data,
            AiReportResult aiResult
    ) {
        requireText(aiResult.model(), "응답의 모델명이 비어 있습니다.");
        requireText(aiResult.output(), "응답 본문이 비어 있습니다.");

        String markdown = switch (data.reportType()) {
            case SUMMARY -> renderSummary(
                    cast(data.promptInput(), SummaryInput.class),
                    parse(aiResult.output(), SummaryAiOutput.class)
            );
            case COMPARE -> renderCompare(
                    cast(data.promptInput(), CompareInput.class),
                    parse(aiResult.output(), CompareAiOutput.class)
            );
            case AREA -> validateAreaMarkdown(
                    cast(data.promptInput(), AreaInput.class),
                    aiResult.output()
            );
            case ALL -> validateAllMarkdown(
                    cast(data.promptInput(), AllInput.class),
                    aiResult.output()
            );
        };
        validateCommonMarkdown(markdown);
        return new GeneratedReportContent(
                aiResult.model(),
                data.reportType().promptVersion(),
                markdown.strip() + System.lineSeparator()
        );
    }

    /**
     * SQL 통계 표와 최대 500자의 AI Comment를 SUMMARY Markdown으로 조립한다.
     */
    private String renderSummary(SummaryInput input, SummaryAiOutput output) {
        if (output == null) {
            throw invalid("SUMMARY 필수 필드가 누락되었습니다.");
        }
        requireLength(output.comment(), 1, 500, "SUMMARY comment");

        StringBuilder markdown = new StringBuilder();
        markdown.append("# 전체 통계 요약\n\n")
                .append("## 주요 통계\n\n")
                .append("- 등록 지역 수: ").append(input.areaCount()).append("\n")
                .append("- 전체 방문 수: ").append(input.visitCount()).append("\n\n")
                .append("| 평가 항목 | 전체 평균 |\n")
                .append("|---|---:|\n");
        appendScoreRows(markdown, input.averageScores());

        markdown.append("\n## 항목별 Top 5\n");
        appendTopFive(markdown, "분위기", input.top5().atmosphere());
        appendTopFive(markdown, "생활 인프라", input.top5().infra());
        appendTopFive(markdown, "청결도", input.top5().clean());
        appendTopFive(markdown, "넓은 집 가능성", input.top5().size());
        appendTopFive(markdown, "접근성", input.top5().access());

        markdown.append("\n## AI 평가\n\n")
                .append(output.comment().strip());
        return markdown.toString();
    }

    /**
     * Backend 비교 표와 검증된 AI 평가를 COMPARE Markdown으로 조립한다.
     */
    private String renderCompare(CompareInput input, CompareAiOutput output) {
        validateCompareOutput(input, output);

        StringBuilder markdown = new StringBuilder();
        markdown.append("# 지역 비교 리포트\n\n")
                .append("## 비교 대상\n\n")
                .append("| 순서 | 지역 | 방문 수 | 분위기 | 생활 인프라 | 청결도 | 넓은 집 가능성 | 접근성 |\n")
                .append("|---:|---|---:|---:|---:|---:|---:|---:|\n");
        for (CompareAreaInput area : input.areas()) {
            markdown.append("| ").append(area.displayOrder())
                    .append(" | ").append(escapeTableCell(area.name()))
                    .append(" | ").append(area.visitCount());
            appendScoreCells(markdown, area.averageScores());
            markdown.append(" |\n");
        }

        markdown.append("\n## 항목별 비교\n");
        appendAnalysis(markdown, "분위기", output.criteria().atmosphere());
        appendAnalysis(markdown, "생활 인프라", output.criteria().infra());
        appendAnalysis(markdown, "청결도", output.criteria().clean());
        appendAnalysis(markdown, "넓은 집 가능성", output.criteria().size());
        appendAnalysis(markdown, "접근성", output.criteria().access());

        markdown.append("\n## 지역별 장단점\n");
        for (CompareAiOutput.AreaAssessment assessment : output.areaAssessments()) {
            markdown.append("\n### ").append(assessment.areaName()).append("\n\n")
                    .append(assessment.content().strip()).append("\n");
        }

        markdown.append("\n## 객관적으로 추가 확인할 사항\n\n");
        for (CompareAiOutput.VerificationItem item : output.verificationChecklist()) {
            markdown.append("- **").append(item.category().strip()).append("**: ")
                    .append(item.content().strip()).append("\n");
        }
        markdown.append("\n## 종합 평가\n\n")
                .append(output.overall().strip());
        return markdown.toString();
    }

    /**
     * COMPARE의 분량, 대상 ID·이름·순서와 체크리스트 개수를 검증한다.
     */
    private void validateCompareOutput(CompareInput input, CompareAiOutput output) {
        if (output == null || output.criteria() == null
                || output.areaAssessments() == null
                || output.verificationChecklist() == null) {
            throw invalid("COMPARE 필수 필드가 누락되었습니다.");
        }

        requireLength(output.criteria().atmosphere(), 1, 300, "분위기 비교");
        requireLength(output.criteria().infra(), 1, 300, "생활 인프라 비교");
        requireLength(output.criteria().clean(), 1, 300, "청결도 비교");
        requireLength(output.criteria().size(), 1, 300, "넓은 집 가능성 비교");
        requireLength(output.criteria().access(), 1, 300, "접근성 비교");
        requireLength(output.overall(), 1, 600, "종합 평가");

        if (output.areaAssessments().size() != input.areas().size()) {
            throw invalid("COMPARE 지역별 평가 개수가 입력 지역 수와 다릅니다.");
        }
        for (int index = 0; index < input.areas().size(); index++) {
            CompareAreaInput expected = input.areas().get(index);
            CompareAiOutput.AreaAssessment actual = output.areaAssessments().get(index);
            if (actual == null
                    || !expected.id().equals(actual.areaId())
                    || !expected.name().equals(actual.areaName())) {
                throw invalid("COMPARE 지역 ID, 이름 또는 순서가 입력과 다릅니다.");
            }
            requireLength(actual.content(), 1, 300, expected.name() + " 지역별 평가");
        }

        int checklistSize = output.verificationChecklist().size();
        if (checklistSize < 3 || checklistSize > 7) {
            throw invalid("COMPARE 확인 체크리스트는 3개 이상 7개 이하여야 합니다.");
        }
        for (CompareAiOutput.VerificationItem item : output.verificationChecklist()) {
            if (item == null) {
                throw invalid("COMPARE 확인 체크리스트에 빈 항목이 있습니다.");
            }
            requireText(item.category(), "COMPARE 확인 분류가 비어 있습니다.");
            requireText(item.content(), "COMPARE 확인 내용이 비어 있습니다.");
            validateRemoteWorkChecklist(item.category() + ": " + item.content());
        }
    }

    /**
     * AREA Markdown의 필수 제목 순서와 3~7개 확인 항목을 검증한다.
     */
    private String validateAreaMarkdown(AreaInput input, String output) {
        String markdown = output.strip();
        String areaName = input.area().name();
        List<String> requiredHeadings = List.of(
                "# " + areaName + " 지역 상세 분석",
                "## 평가 요약",
                "## 항목별 분석",
                "### 분위기",
                "### 생활 인프라",
                "### 청결도",
                "### 넓은 집 가능성",
                "### 접근성",
                "## 방문별 변화",
                "## 주요 장점",
                "## 주요 단점",
                "## 거주지 선택 시 고려사항",
                "## 객관적으로 추가 확인할 사항",
                "## 종합 평가"
        );
        validateHeadingOrder(markdown, requiredHeadings);
        validateListItemCount(
                markdown,
                "## 객관적으로 추가 확인할 사항",
                "## 종합 평가",
                3,
                7
        );
        validateRemoteWorkChecklistSection(
                markdown,
                "## 객관적으로 추가 확인할 사항",
                "## 종합 평가"
        );
        return markdown;
    }

    /**
     * ALL Markdown에 모든 입력 Area가 순서와 개수대로 한 번씩 포함됐는지 검증한다.
     */
    private String validateAllMarkdown(AllInput input, String output) {
        String markdown = output.strip();
        validateHeadingOrder(markdown, List.of(
                "# 전체 지역 분석 리포트",
                "## 전체 경향",
                "## 지역별 분석"
        ));
        int previousIndex = indexOfExactLine(markdown, "## 지역별 분석", 0);
        for (var area : input.areas()) {
            String heading = "### " + area.name();
            int headingIndex = indexOfExactLine(markdown, heading, previousIndex + 1);
            if (headingIndex < 0) {
                throw invalid("ALL 지역 제목이 누락되었습니다: " + area.name());
            }
            int nextAreaOrSection = markdown.length();
            int nextSection = indexOfExactLine(
                    markdown,
                    "## 항목별 주요 후보",
                    headingIndex + 1
            );
            if (nextSection >= 0) {
                nextAreaOrSection = nextSection;
            }
            if (area.displayOrder() < input.areas().size()) {
                String nextAreaHeading = "### "
                        + input.areas().get(area.displayOrder()).name();
                int nextArea = indexOfExactLine(
                        markdown,
                        nextAreaHeading,
                        headingIndex + 1
                );
                if (nextArea >= 0) {
                    nextAreaOrSection = Math.min(nextAreaOrSection, nextArea);
                }
            }
            validateHeadingOrder(
                    markdown.substring(headingIndex, nextAreaOrSection),
                    List.of(
                            "#### 평가 요약",
                            "#### 주요 장점",
                            "#### 주요 단점",
                            "#### 고려사항"
                    )
            );
            previousIndex = headingIndex;
        }
        for (String areaName : input.areas().stream().map(area -> area.name()).distinct().toList()) {
            String heading = "### " + areaName;
            long expectedCount = input.areas().stream()
                    .filter(area -> area.name().equals(areaName))
                    .count();
            if (countExactLines(markdown, heading) != expectedCount) {
                throw invalid("ALL 지역 제목의 개수가 입력과 다릅니다: " + areaName);
            }
        }
        validateHeadingOrder(markdown, List.of(
                "## 항목별 주요 후보",
                "## 우선순위별 후보",
                "## 객관적으로 추가 확인할 사항",
                "## 종합 평가"
        ), previousIndex);
        validateListItemCount(
                markdown,
                "## 객관적으로 추가 확인할 사항",
                "## 종합 평가",
                5,
                10
        );
        validateRemoteWorkChecklistSection(
                markdown,
                "## 객관적으로 추가 확인할 사항",
                "## 종합 평가"
        );
        return markdown;
    }

    /**
     * 모든 유형에 공통인 최상위 제목, 코드 블록 금지와 사용자용 점수명을 검증한다.
     */
    private void validateCommonMarkdown(String markdown) {
        if (markdown.isBlank() || !markdown.stripLeading().startsWith("# ")) {
            throw invalid("Markdown 최상위 제목이 없습니다.");
        }
        if (markdown.contains("```")) {
            throw invalid("Markdown 결과에 코드 블록이 포함되었습니다.");
        }
        Matcher internalField = INTERNAL_SCORE_FIELD_PATTERN.matcher(markdown);
        if (internalField.find()) {
            throw invalid(
                    "사용자용 Report에 내부 점수 필드명 '"
                            + internalField.group()
                            + "'이 포함되었습니다. 분위기, 생활 인프라, 청결도, "
                            + "넓은 집 가능성 또는 접근성 중 대응하는 한글 표시명을 사용해야 합니다."
            );
        }
    }

    private void validateHeadingOrder(String markdown, List<String> headings) {
        validateHeadingOrder(markdown, headings, -1);
    }

    private void validateHeadingOrder(
            String markdown,
            List<String> headings,
            int startingIndex
    ) {
        int previousIndex = startingIndex;
        for (String heading : headings) {
            int index = indexOfExactLine(markdown, heading, previousIndex + 1);
            if (index < 0 || countExactLines(markdown, heading) != 1) {
                throw invalid("필수 Markdown 제목이 누락되었거나 중복되었습니다: " + heading);
            }
            previousIndex = index;
        }
    }

    private void validateListItemCount(
            String markdown,
            String sectionHeading,
            String nextHeading,
            int minimum,
            int maximum
    ) {
        int sectionStart = indexOfExactLine(markdown, sectionHeading, 0);
        int sectionEnd = indexOfExactLine(markdown, nextHeading, sectionStart + 1);
        if (sectionStart < 0 || sectionEnd < 0) {
            throw invalid("확인 체크리스트 구간을 찾을 수 없습니다.");
        }
        long itemCount = markdown.substring(sectionStart, sectionEnd)
                .lines()
                .map(String::stripLeading)
                .filter(line -> line.startsWith("- ") || line.startsWith("* "))
                .count();
        if (itemCount < minimum || itemCount > maximum) {
            throw invalid("확인 체크리스트 항목 수가 허용 범위를 벗어났습니다.");
        }
    }

    private void validateRemoteWorkChecklistSection(
            String markdown,
            String sectionHeading,
            String nextHeading
    ) {
        int sectionStart = indexOfExactLine(markdown, sectionHeading, 0);
        int sectionEnd = indexOfExactLine(markdown, nextHeading, sectionStart + 1);
        if (sectionStart < 0 || sectionEnd < 0) {
            throw invalid("재택근무 생활 전제를 검증할 체크리스트 구간을 찾을 수 없습니다.");
        }
        validateRemoteWorkChecklist(markdown.substring(sectionStart, sectionEnd));
    }

    /**
     * 확인 체크리스트가 전면 재택근무 사용자의 생활 패턴과 표현 선호를 지키는지 검사한다.
     */
    private void validateRemoteWorkChecklist(String checklist) {
        if (DAILY_COMMUTE_PATTERN.matcher(checklist).find()) {
            throw invalid(
                    "재택근무 사용자에게 통근, 출근 또는 퇴근을 기본 확인 항목으로 "
                            + "제안할 수 없습니다. 도쿄 주요 지역 이동을 기준으로 작성해야 합니다."
            );
        }
        if (FATIGUE_RECORDING_PATTERN.matcher(checklist).find()) {
            throw invalid(
                    "체감 피로도를 기록하라고 요구하지 말고, "
                            + "도쿄 주요 지역까지 이동한 후 확인하고 후보 간 비교하도록 작성해야 합니다."
            );
        }
    }

    private int indexOfExactLine(String markdown, String heading, int fromIndex) {
        int lineStart = 0;
        while (lineStart <= markdown.length()) {
            int lineFeed = markdown.indexOf('\n', lineStart);
            int lineEnd = lineFeed < 0 ? markdown.length() : lineFeed;
            String line = markdown.substring(lineStart, lineEnd).stripTrailing();
            if (lineStart >= fromIndex && line.equals(heading)) {
                return lineStart;
            }
            if (lineFeed < 0) {
                break;
            }
            lineStart = lineFeed + 1;
        }
        return -1;
    }

    private int countExactLines(String markdown, String heading) {
        int count = 0;
        for (String line : markdown.lines().toList()) {
            if (line.stripTrailing().equals(heading)) {
                count++;
            }
        }
        return count;
    }

    private void appendScoreRows(StringBuilder markdown, ScoreAverages scores) {
        markdown.append("| 분위기 | ").append(formatScore(scores.atmosphere())).append(" |\n")
                .append("| 생활 인프라 | ").append(formatScore(scores.infra())).append(" |\n")
                .append("| 청결도 | ").append(formatScore(scores.clean())).append(" |\n")
                .append("| 넓은 집 가능성 | ").append(formatScore(scores.size())).append(" |\n")
                .append("| 접근성 | ").append(formatScore(scores.access())).append(" |\n");
    }

    private void appendScoreCells(StringBuilder markdown, ScoreAverages scores) {
        markdown.append(" | ").append(formatScore(scores.atmosphere()))
                .append(" | ").append(formatScore(scores.infra()))
                .append(" | ").append(formatScore(scores.clean()))
                .append(" | ").append(formatScore(scores.size()))
                .append(" | ").append(formatScore(scores.access()));
    }

    private void appendTopFive(
            StringBuilder markdown,
            String title,
            List<TopArea> topAreas
    ) {
        markdown.append("\n### ").append(title).append("\n\n");
        if (topAreas.isEmpty()) {
            markdown.append("- 데이터 없음\n");
            return;
        }
        for (int index = 0; index < topAreas.size(); index++) {
            TopArea area = topAreas.get(index);
            markdown.append(index + 1).append(". ")
                    .append(area.areaName())
                    .append(" (").append(formatScore(area.score())).append(")\n");
        }
    }

    private void appendAnalysis(StringBuilder markdown, String title, String content) {
        markdown.append("\n### ").append(title).append("\n\n")
                .append(content.strip()).append("\n");
    }

    private String formatScore(Double score) {
        return score == null ? "-" : String.format(Locale.ROOT, "%.1f", score);
    }

    private String escapeTableCell(String value) {
        return value.replace("|", "\\|")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    private <T> T parse(String output, Class<T> outputType) {
        try {
            return objectMapper.readValue(output, outputType);
        } catch (JacksonException exception) {
            throw invalid("Structured Output JSON을 읽을 수 없습니다.");
        }
    }

    private <T> T cast(Object value, Class<T> expectedType) {
        if (!expectedType.isInstance(value)) {
            throw invalid("Prompt 입력 구조가 Report Type과 일치하지 않습니다.");
        }
        return expectedType.cast(value);
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
    }

    private void requireLength(String value, int minimum, int maximum, String fieldName) {
        requireText(value, fieldName + "이(가) 비어 있습니다.");
        int length = value.strip().length();
        if (length < minimum || length > maximum) {
            throw invalid(fieldName + " 분량이 허용 범위를 벗어났습니다.");
        }
    }

    private InvalidReportOutputException invalid(String message) {
        return new InvalidReportOutputException(message);
    }
}
