package com.townai.report.generation.ai;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.common.openai.OpenAiResponse;
import com.townai.common.openai.OpenAiResponsesClient;
import com.townai.report.entity.ReportType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Report 유형별 Prompt와 Schema를 읽어 OpenAI Responses API 호출을 구성하는 Adapter이다.
 *
 * <p>SUMMARY와 COMPARE는 Backend가 Markdown을 조립할 수 있도록 Structured Output을
 * 사용하고, 상세 분석인 AREA와 ALL은 Markdown Text를 직접 요청한다. Resource는
 * {@code prompts/{reportType}/v1}에서 읽으며 교정 요청에도 같은 버전을 유지한다.</p>
 */
@Component
public class OpenAiReportClient implements ReportAiClient {

    private final OpenAiResponsesClient responsesClient;
    private final ObjectMapper objectMapper;

    /**
     * Report AI Adapter를 생성한다.
     *
     * @param responsesClient 공통 Responses API HTTP Client
     * @param objectMapper JSON Schema Resource를 읽을 ObjectMapper
     */
    public OpenAiReportClient(
            OpenAiResponsesClient responsesClient,
            ObjectMapper objectMapper
    ) {
        this.responsesClient = responsesClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiReportResult generate(
            ReportType reportType,
            Object promptInput,
            String correctionInstruction
    ) {
        try {
            String instructions = loadInstructions(
                    reportType,
                    correctionInstruction
            );
            OpenAiResponse response;
            if (reportType == ReportType.SUMMARY || reportType == ReportType.COMPARE) {
                response = responsesClient.generateStructured(
                        instructions,
                        promptInput,
                        reportType.pathName() + "_v1",
                        loadSchema(reportType)
                );
            } else {
                response = responsesClient.generateText(
                        instructions,
                        promptInput
                );
            }
            return new AiReportResult(response.model(), response.output());
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.OPENAI_API_ERROR);
        }
    }

    private String loadInstructions(
            ReportType reportType,
            String correctionInstruction
    ) throws IOException {
        String resourcePath = promptBasePath(reportType) + "/system.md";
        String instructions;
        try (var inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            instructions = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (correctionInstruction == null || correctionInstruction.isBlank()) {
            return instructions;
        }
        return instructions
                + "\n\n# 출력 교정 요청\n"
                + correctionInstruction
                + "\n원래 입력 데이터와 위의 모든 출력 규칙을 그대로 지키며 전체 결과를 다시 반환하십시오.";
    }

    private JsonNode loadSchema(ReportType reportType) throws IOException {
        String resourcePath = promptBasePath(reportType) + "/output-schema.json";
        try (var inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            return objectMapper.readTree(inputStream);
        }
    }

    private String promptBasePath(ReportType reportType) {
        return "prompts/" + reportType.pathName() + "/v1";
    }
}
