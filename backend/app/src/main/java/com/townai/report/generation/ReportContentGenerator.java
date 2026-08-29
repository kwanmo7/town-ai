package com.townai.report.generation;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.report.generation.ReportDataAssembler.AllAreaInput;
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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 원본 출력을 유형별 계약으로 검증하고 저장 가능한 Markdown으로 변환한다.
 *
 * <p>SUMMARY·COMPARE·ALL은 Structured Output의 AI 문장과 Backend 소유 구조를
 * Markdown으로 조립한다. AREA는 AI Markdown의 제목 순서와 사용자 전제를 검증한다.
 * 첫 출력이 형식 또는 분량 계약을 위반하면 같은 Prompt 입력으로 한 번만
 * 교정 요청하며, 두 번째 실패는 외부 AI 오류로 변환한다.</p>
 */
@Component
public class ReportContentGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportContentGenerator.class);

    private final ReportAiClient reportAiClient;
    private final ObjectMapper objectMapper;
    private final ReportMarkdownValidator markdownValidator;

    /**
     * Report 출력 생성기와 유형별 Validator를 구성한다.
     *
     * @param reportAiClient AI 모델 호출 Port
     * @param objectMapper Structured Output을 내부 Record로 변환할 ObjectMapper
     * @param markdownValidator AI Markdown 구조와 사용자 표현 Validator
     */
    public ReportContentGenerator(
            ReportAiClient reportAiClient,
            ObjectMapper objectMapper,
            ReportMarkdownValidator markdownValidator
    ) {
        this.reportAiClient = reportAiClient;
        this.objectMapper = objectMapper;
        this.markdownValidator = markdownValidator;
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
            case AREA -> markdownValidator.validateArea(
                    cast(data.promptInput(), AreaInput.class),
                    aiResult.output()
            );
            case ALL -> renderAll(
                    cast(data.promptInput(), AllInput.class),
                    parse(aiResult.output(), AllAiOutput.class)
            );
        };
        markdownValidator.validateCommon(markdown);
        return new GeneratedReportContent(
                aiResult.model(),
                data.reportType().promptVersion(),
                markdown.strip() + System.lineSeparator()
        );
    }

    /**
     * Backend 집계 표와 최대 500자의 AI Comment를 SUMMARY Markdown으로 조립한다.
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
     * 검증된 ALL Structured Output을 고정된 Markdown 제목 구조로 조립한다.
     */
    private String renderAll(AllInput input, AllAiOutput output) {
        validateAllOutput(input, output);

        StringBuilder markdown = new StringBuilder();
        markdown.append("# 전체 지역 분석 리포트\n\n")
                .append("## 전체 경향\n\n")
                .append(output.overallTrends().strip())
                .append("\n\n## 지역별 분석\n");

        for (AllAiOutput.AreaAnalysis analysis : output.areaAnalyses()) {
            markdown.append("\n### ").append(analysis.areaName()).append("\n\n")
                    .append("#### 평가 요약\n\n")
                    .append(analysis.summary().strip()).append("\n\n")
                    .append("#### 주요 장점\n\n")
                    .append(analysis.strengths().strip()).append("\n\n")
                    .append("#### 주요 단점\n\n")
                    .append(analysis.weaknesses().strip()).append("\n\n")
                    .append("#### 고려사항\n\n")
                    .append(analysis.considerations().strip()).append("\n");
        }

        markdown.append("\n## 항목별 주요 후보\n");
        appendCriteriaAnalysis(markdown, output.criteriaCandidates(), false);

        markdown.append("\n## 우선순위별 후보\n");
        appendCriteriaAnalysis(markdown, output.priorityCandidates(), true);

        markdown.append("\n## 객관적으로 추가 확인할 사항\n\n");
        for (AllAiOutput.VerificationItem item : output.verificationChecklist()) {
            markdown.append("- **").append(item.category().strip()).append("**: ")
                    .append(item.content().strip()).append("\n");
        }

        markdown.append("\n## 종합 평가\n\n")
                .append(output.overall().strip());
        return markdownValidator.validateAll(input, markdown.toString());
    }

    /**
     * ALL 대상 Area의 ID·이름·순서와 모든 상세 필드 및 체크리스트를 검증한다.
     */
    private void validateAllOutput(AllInput input, AllAiOutput output) {
        if (output == null
                || output.areaAnalyses() == null
                || output.criteriaCandidates() == null
                || output.priorityCandidates() == null
                || output.verificationChecklist() == null) {
            throw invalid("ALL 필수 필드가 누락되었습니다.");
        }
        requireText(output.overallTrends(), "ALL 전체 경향이 비어 있습니다.");
        requireText(output.overall(), "ALL 종합 평가가 비어 있습니다.");

        if (output.areaAnalyses().size() != input.areas().size()) {
            throw invalid("ALL 지역별 분석 개수가 입력 지역 수와 다릅니다.");
        }
        for (int index = 0; index < input.areas().size(); index++) {
            AllAreaInput expected = input.areas().get(index);
            AllAiOutput.AreaAnalysis actual = output.areaAnalyses().get(index);
            if (actual == null
                    || !expected.id().equals(actual.areaId())
                    || !expected.name().equals(actual.areaName())) {
                throw invalid("ALL 지역 ID, 이름 또는 순서가 입력과 다릅니다.");
            }
            requireText(actual.summary(), expected.name() + " 평가 요약이 비어 있습니다.");
            requireText(actual.strengths(), expected.name() + " 주요 장점이 비어 있습니다.");
            requireText(actual.weaknesses(), expected.name() + " 주요 단점이 비어 있습니다.");
            requireText(actual.considerations(), expected.name() + " 고려사항이 비어 있습니다.");
        }

        validateCriteriaAnalysis(output.criteriaCandidates(), "항목별 주요 후보");
        validateCriteriaAnalysis(output.priorityCandidates(), "우선순위별 후보");

        int checklistSize = output.verificationChecklist().size();
        if (checklistSize < 5 || checklistSize > 10) {
            throw invalid("ALL 확인 체크리스트는 5개 이상 10개 이하여야 합니다.");
        }
        for (AllAiOutput.VerificationItem item : output.verificationChecklist()) {
            if (item == null) {
                throw invalid("ALL 확인 체크리스트에 빈 항목이 있습니다.");
            }
            requireText(item.category(), "ALL 확인 분류가 비어 있습니다.");
            requireText(item.content(), "ALL 확인 내용이 비어 있습니다.");
            markdownValidator.validateChecklistText(
                    item.category() + ": " + item.content()
            );
        }
    }

    private void validateCriteriaAnalysis(
            AllAiOutput.CriteriaAnalysis analysis,
            String sectionName
    ) {
        requireText(analysis.atmosphere(), sectionName + " 분위기가 비어 있습니다.");
        requireText(analysis.infra(), sectionName + " 생활 인프라가 비어 있습니다.");
        requireText(analysis.clean(), sectionName + " 청결도가 비어 있습니다.");
        requireText(analysis.size(), sectionName + " 넓은 집 가능성이 비어 있습니다.");
        requireText(analysis.access(), sectionName + " 접근성이 비어 있습니다.");
    }

    private void appendCriteriaAnalysis(
            StringBuilder markdown,
            AllAiOutput.CriteriaAnalysis analysis,
            boolean priorityHeading
    ) {
        appendAnalysis(
                markdown,
                priorityHeading ? "분위기 우선" : "분위기",
                analysis.atmosphere()
        );
        appendAnalysis(
                markdown,
                priorityHeading ? "생활 인프라 우선" : "생활 인프라",
                analysis.infra()
        );
        appendAnalysis(
                markdown,
                priorityHeading ? "청결도 우선" : "청결도",
                analysis.clean()
        );
        appendAnalysis(
                markdown,
                priorityHeading ? "넓은 집 가능성 우선" : "넓은 집 가능성",
                analysis.size()
        );
        appendAnalysis(
                markdown,
                priorityHeading ? "접근성 우선" : "접근성",
                analysis.access()
        );
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
            markdownValidator.validateChecklistText(
                    item.category() + ": " + item.content()
            );
        }
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
