package com.townai.report.generation;

import com.townai.common.openai.OpenAiResponsesClient;
import com.townai.common.openai.OpenAiProperties;
import com.townai.report.entity.ReportType;
import com.townai.report.generation.ReportDataAssembler.AllInput;
import com.townai.report.generation.ReportDataAssembler.AreaInput;
import com.townai.report.generation.ReportDataAssembler.CompareInput;
import com.townai.report.generation.ReportDataAssembler.SummaryInput;
import com.townai.report.generation.ai.OpenAiReportClient;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.parser.OpenAiVisitParserClient;
import com.townai.visit.parser.VisitParserInput;
import com.townai.visit.parser.validation.VisitDraftOutputValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 OpenAI API와 운영 Prompt를 사용해 Report Markdown과 Visit Parser 품질을 확인한다.
 *
 * <p>API 비용과 비결정적인 출력 때문에 일반 Test 및 CI에서는 제외하고,
 * {@code promptEval} Gradle Task로만 명시적으로 실행한다.</p>
 */
@Tag("prompt-eval")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenAiPromptEvaluationTest {

    private static final String FIXTURE_BASE_PATH = "prompt-eval/fixtures/";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-5.4-mini";

    private ObjectMapper objectMapper;
    private ReportContentGenerator contentGenerator;
    private OpenAiVisitParserClient visitParserClient;
    private Path outputDirectory;
    private String model;

    @BeforeAll
    void setUp() throws IOException {
        String apiKey = System.getenv("OPENAI_API_KEY");
        assertFalse(
                apiKey == null || apiKey.isBlank(),
                "promptEval 실행 전 OPENAI_API_KEY 환경변수를 설정해야 합니다."
        );

        model = environmentOrDefault("OPENAI_REPORT_MODEL", DEFAULT_MODEL);
        objectMapper = JsonMapper.builder()
                .findAndAddModules()
                .build();
        OpenAiResponsesClient responsesClient = new OpenAiResponsesClient(
                RestClient.builder(),
                objectMapper,
                new OpenAiProperties(
                        apiKey,
                        environmentOrDefault(
                                "OPENAI_BASE_URL",
                                DEFAULT_BASE_URL
                        ),
                        model,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(180)
                )
        );
        OpenAiReportClient aiClient = new OpenAiReportClient(
                responsesClient,
                objectMapper
        );
        visitParserClient = new OpenAiVisitParserClient(
                responsesClient,
                objectMapper
        );
        contentGenerator = new ReportContentGenerator(
                aiClient,
                objectMapper,
                new ReportMarkdownValidator()
        );

        outputDirectory = Path.of(
                System.getProperty(
                        "townai.prompt-eval.output-directory",
                        "build/prompt-eval"
                )
        );
        Files.createDirectories(outputDirectory);
        copyEvaluationSheet();
        writeRunInformation();
    }

    @Test
    @Order(1)
    @DisplayName("SUMMARY 실제 Prompt 평가")
    void generatesSummaryReport() throws IOException {
        evaluate(
                ReportType.SUMMARY,
                readFixture("summary.json", SummaryInput.class)
        );
    }

    @Test
    @Order(2)
    @DisplayName("AREA 실제 Prompt 평가")
    void generatesAreaReport() throws IOException {
        evaluate(
                ReportType.AREA,
                readFixture("area.json", AreaInput.class)
        );
    }

    @Test
    @Order(3)
    @DisplayName("COMPARE 실제 Prompt 평가")
    void generatesCompareReport() throws IOException {
        evaluate(
                ReportType.COMPARE,
                readFixture("compare.json", CompareInput.class)
        );
    }

    @Test
    @Order(4)
    @DisplayName("ALL 실제 Prompt 평가")
    void generatesAllReport() throws IOException {
        evaluate(
                ReportType.ALL,
                readFixture("all.json", AllInput.class)
        );
    }

    @Test
    @Order(5)
    @DisplayName("등록 Area가 없는 실제 Visit Parser 평가")
    void parsesNewAreaCandidateWithoutRegisteredAreas() throws IOException {
        VisitParserInput input = new VisitParserInput(
                LocalDate.of(2026, 8, 4),
                "센터미나미를 7월 25일에 방문했어. 분위기 8, 생활 인프라 8, "
                        + "청결도 8, 넓은 집 가능성 7, 접근성 7이야. "
                        + "역 주변이 넓은 광장으로 되어 있어서 개방감이 있고 "
                        + "쇼핑몰과 상가가 있어 동선이 좋은 것 같아.",
                List.of()
        );

        String output = visitParserClient.parse(input, null);
        VisitDraftResponse result = new VisitDraftOutputValidator(
                objectMapper
        ).validate(output, input);

        assertNull(result.area().id());
        assertEquals("센터미나미", result.area().name());
        assertTrue(result.area().hasRequiredLocation());
        assertEquals(LocalDate.of(2026, 7, 25), result.visitDate());
        assertEquals(8, result.atmosphereScore());
        assertEquals(8, result.infraScore());
        assertEquals(8, result.cleanScore());
        assertEquals(7, result.sizeScore());
        assertEquals(7, result.accessScore());
        assertFalse(result.warnings().isEmpty());

        Files.writeString(
                outputDirectory.resolve("visit-parser.json"),
                output,
                StandardCharsets.UTF_8
        );
    }

    @Test
    @Order(6)
    @DisplayName("기존 Visit Draft 부분 수정 실제 Prompt 평가")
    void revisesOnlyRequestedVisitDraftField() throws IOException {
        VisitParserInput.AreaInput area = new VisitParserInput.AreaInput(
                1L,
                "센터미나미",
                "가나가와현",
                "요코하마시",
                "센터미나미역"
        );
        VisitParserInput input = new VisitParserInput(
                LocalDate.of(2026, 8, 4),
                "접근성만 8로 수정",
                List.of(area),
                new VisitParserInput.ExistingDraftInput(
                        area,
                        LocalDate.of(2026, 7, 25),
                        8,
                        8,
                        8,
                        7,
                        7,
                        "역 주변이 정돈돼 있고 쇼핑하기 편했다."
                )
        );

        String output = visitParserClient.parse(input, null);
        VisitDraftResponse result = new VisitDraftOutputValidator(
                objectMapper
        ).validateRevision(output, input);

        assertEquals(1L, result.area().id());
        assertEquals(LocalDate.of(2026, 7, 25), result.visitDate());
        assertEquals(8, result.atmosphereScore());
        assertEquals(8, result.infraScore());
        assertEquals(8, result.cleanScore());
        assertEquals(7, result.sizeScore());
        assertEquals(8, result.accessScore());
        assertEquals(
                "역 주변이 정돈돼 있고 쇼핑하기 편했다.",
                result.memo()
        );

        Files.writeString(
                outputDirectory.resolve("visit-parser-revision.json"),
                output,
                StandardCharsets.UTF_8
        );
    }

    private void evaluate(ReportType reportType, Object promptInput) throws IOException {
        GeneratedReportContent result = contentGenerator.generate(
                new ReportGenerationData(reportType, List.of(), promptInput)
        );

        assertEquals(reportType.promptVersion(), result.promptVersion());
        assertFalse(result.model().isBlank());
        assertFalse(result.markdown().isBlank());

        Files.writeString(
                outputDirectory.resolve(reportType.pathName() + ".md"),
                result.markdown(),
                StandardCharsets.UTF_8
        );
    }

    private <T> T readFixture(String filename, Class<T> type) throws IOException {
        try (var inputStream = new ClassPathResource(
                FIXTURE_BASE_PATH + filename
        ).getInputStream()) {
            return objectMapper.readValue(inputStream, type);
        }
    }

    private void copyEvaluationSheet() throws IOException {
        try (var inputStream = new ClassPathResource(
                "prompt-eval/evaluation-sheet.md"
        ).getInputStream()) {
            Files.write(
                    outputDirectory.resolve("evaluation-sheet.md"),
                    inputStream.readAllBytes()
            );
        }
    }

    private void writeRunInformation() throws IOException {
        String content = """
                # Prompt 평가 실행 정보

                - 실행 시각(UTC): %s
                - 요청 모델: `%s`
                - Prompt Version: `summary-v1`, `area-v1`, `compare-v1`, `all-v1`
                - Fixture: `summary.json`, `area.json`, `compare.json`, `all.json`
                - 기본 API 호출 수: 4회
                - 출력 교정 재시도 발생 시 최대 API 호출 수: 8회

                API Key는 이 파일에 기록하지 않습니다.
                """.formatted(Instant.now(), model);
        Files.writeString(
                outputDirectory.resolve("run-info.md"),
                content,
                StandardCharsets.UTF_8
        );
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
