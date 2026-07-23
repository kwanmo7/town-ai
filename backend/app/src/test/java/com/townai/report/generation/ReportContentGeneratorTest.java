package com.townai.report.generation;

import com.townai.report.entity.ReportType;
import com.townai.report.generation.ReportDataAssembler.CompareAreaInput;
import com.townai.report.generation.ReportDataAssembler.CompareInput;
import com.townai.report.generation.ReportDataAssembler.ScoreAverages;
import com.townai.report.generation.ReportDataAssembler.SummaryInput;
import com.townai.report.generation.ReportDataAssembler.TopFive;
import com.townai.report.generation.ai.AiReportResult;
import com.townai.report.generation.ai.ReportAiClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportContentGeneratorTest {

    @Test
    void buildsSummaryMarkdownFromBackendStatisticsAndAiComment() {
        FakeAiClient aiClient = new FakeAiClient("""
                {"comment":"청결도 강점과 접근성의 상대적인 약점을 함께 확인할 필요가 있습니다."}
                """);
        ReportContentGenerator generator =
                new ReportContentGenerator(aiClient, new ObjectMapper());
        SummaryInput input = new SummaryInput(
                2,
                3,
                new ScoreAverages(8.0, 7.0, 9.0, 6.0, 5.0),
                new TopFive(List.of(), List.of(), List.of(), List.of(), List.of())
        );

        GeneratedReportContent result = generator.generate(
                new ReportGenerationData(ReportType.SUMMARY, List.of(), input)
        );

        assertEquals("summary-v1", result.promptVersion());
        assertTrue(result.markdown().contains("# 전체 통계 요약"));
        assertTrue(result.markdown().contains("- 전체 방문 수: 3"));
        assertTrue(result.markdown().contains("| 청결도 | 9.0 |"));
        assertTrue(result.markdown().contains("## AI 평가"));
        assertEquals(1, aiClient.correctionInstructions.size());
    }

    @Test
    void retriesOnceWhenCompareAreaOrderDoesNotMatchInput() {
        FakeAiClient aiClient = new FakeAiClient(
                compareJson(2L, "B", 1L, "A"),
                compareJson(1L, "A", 2L, "B")
        );
        ReportContentGenerator generator =
                new ReportContentGenerator(aiClient, new ObjectMapper());
        CompareInput input = new CompareInput(List.of(
                compareArea(1, 1L, "A"),
                compareArea(2, 2L, "B")
        ));

        GeneratedReportContent result = generator.generate(
                new ReportGenerationData(ReportType.COMPARE, List.of(), input)
        );

        assertEquals(2, aiClient.correctionInstructions.size());
        assertTrue(aiClient.correctionInstructions.get(1)
                .contains("지역 ID, 이름 또는 순서"));
        assertTrue(result.markdown().indexOf("### A") < result.markdown().indexOf("### B"));
    }

    @Test
    void retriesOnceWhenUserFacingTextContainsInternalScoreFieldName() {
        FakeAiClient aiClient = new FakeAiClient(
                """
                {"comment":"infra 점수가 상대적으로 높습니다."}
                """,
                """
                {"comment":"생활 인프라 점수가 상대적으로 높습니다."}
                """
        );
        ReportContentGenerator generator =
                new ReportContentGenerator(aiClient, new ObjectMapper());
        SummaryInput input = new SummaryInput(
                1,
                1,
                new ScoreAverages(8.0, 9.0, 7.0, 6.0, 5.0),
                new TopFive(List.of(), List.of(), List.of(), List.of(), List.of())
        );

        GeneratedReportContent result = generator.generate(
                new ReportGenerationData(ReportType.SUMMARY, List.of(), input)
        );

        assertEquals(2, aiClient.correctionInstructions.size());
        assertTrue(aiClient.correctionInstructions.get(1).contains("내부 점수 필드명"));
        assertTrue(result.markdown().contains("생활 인프라 점수"));
    }

    @Test
    void retriesOnceWhenChecklistAssumesDailyCommute() {
        String validOutput = compareJson(1L, "A", 2L, "B");
        FakeAiClient aiClient = new FakeAiClient(
                validOutput.replace(
                        "도쿄 주요 지역까지 실제로 이동한 후 환승 편의를 확인",
                        "출퇴근 시간에 이동한 뒤 환승 피로도를 기록"
                ),
                validOutput
        );
        ReportContentGenerator generator =
                new ReportContentGenerator(aiClient, new ObjectMapper());
        CompareInput input = new CompareInput(List.of(
                compareArea(1, 1L, "A"),
                compareArea(2, 2L, "B")
        ));

        GeneratedReportContent result = generator.generate(
                new ReportGenerationData(ReportType.COMPARE, List.of(), input)
        );

        assertEquals(2, aiClient.correctionInstructions.size());
        assertTrue(aiClient.correctionInstructions.get(1).contains("재택근무 사용자"));
        assertTrue(result.markdown().contains("도쿄 주요 지역까지 실제로 이동한 후"));
    }

    private CompareAreaInput compareArea(int order, Long id, String name) {
        return new CompareAreaInput(
                order,
                id,
                name,
                1,
                new ScoreAverages(8.0, 7.0, 6.0, 5.0, 4.0),
                List.of("메모")
        );
    }

    private String compareJson(Long firstId, String firstName, Long secondId, String secondName) {
        return """
                {
                  "criteria": {
                    "atmosphere": "분위기 비교",
                    "infra": "생활 인프라 비교",
                    "clean": "청결도 비교",
                    "size": "넓은 집 가능성 비교",
                    "access": "접근성 비교"
                  },
                  "areaAssessments": [
                    {"areaId": %d, "areaName": "%s", "content": "첫 지역 평가"},
                    {"areaId": %d, "areaName": "%s", "content": "둘째 지역 평가"}
                  ],
                  "verificationChecklist": [
                    {"category": "도쿄 주요 지역 이동", "content": "도쿄 주요 지역까지 실제로 이동한 후 환승 편의를 확인"},
                    {"category": "주거비", "content": "동일 조건 매물 비교"},
                    {"category": "환경", "content": "야간에 다시 방문"}
                  ],
                  "overall": "우선순위에 따라 선택이 달라질 수 있습니다."
                }
                """.formatted(firstId, firstName, secondId, secondName);
    }

    private static class FakeAiClient implements ReportAiClient {

        private final Queue<String> outputs;
        private final List<String> correctionInstructions = new ArrayList<>();

        private FakeAiClient(String... outputs) {
            this.outputs = new ArrayDeque<>(List.of(outputs));
        }

        @Override
        public AiReportResult generate(
                ReportType reportType,
                Object promptInput,
                String correctionInstruction
        ) {
            correctionInstructions.add(correctionInstruction);
            return new AiReportResult("test-model", outputs.remove());
        }
    }
}
