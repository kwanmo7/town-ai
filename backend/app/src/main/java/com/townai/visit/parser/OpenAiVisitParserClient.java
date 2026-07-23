package com.townai.visit.parser;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.common.openai.OpenAiResponsesClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Visit Parser Prompt와 strict JSON Schema로 OpenAI Responses API를 호출하는 Adapter이다.
 *
 * <p>Prompt와 Schema는 {@code prompts/visit-parser/v1}의 버전 고정 Resource를
 * 사용한다. 교정 요청이면 기존 System Prompt 뒤에 검증 실패 사유를 추가하되,
 * 입력 데이터와 전체 JSON 반환 규칙은 그대로 유지한다.</p>
 */
@Component
public class OpenAiVisitParserClient implements VisitParserAiClient {

    private static final String PROMPT_BASE_PATH = "prompts/visit-parser/v1";

    private final OpenAiResponsesClient responsesClient;
    private final ObjectMapper objectMapper;

    /**
     * Visit Parser AI Adapter를 생성한다.
     *
     * @param responsesClient 공통 Responses API HTTP Client
     * @param objectMapper strict JSON Schema Resource를 읽을 ObjectMapper
     */
    public OpenAiVisitParserClient(
            OpenAiResponsesClient responsesClient,
            ObjectMapper objectMapper
    ) {
        this.responsesClient = responsesClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String parse(
            VisitParserInput input,
            String correctionInstruction
    ) {
        try {
            return responsesClient.generateStructured(
                    loadInstructions(correctionInstruction),
                    input,
                    "visit_parser_v1",
                    loadSchema()
            ).output();
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.OPENAI_API_ERROR);
        }
    }

    private String loadInstructions(String correctionInstruction) throws IOException {
        String instructions = readResource(PROMPT_BASE_PATH + "/system.md");
        if (correctionInstruction == null || correctionInstruction.isBlank()) {
            return instructions;
        }
        return instructions
                + "\n\n# 출력 교정 요청\n"
                + correctionInstruction
                + "\n원래 입력과 위의 모든 규칙을 그대로 지키며 전체 JSON을 다시 반환하십시오.";
    }

    private JsonNode loadSchema() throws IOException {
        try (var inputStream = new ClassPathResource(
                PROMPT_BASE_PATH + "/output-schema.json"
        ).getInputStream()) {
            return objectMapper.readTree(inputStream);
        }
    }

    private String readResource(String path) throws IOException {
        try (var inputStream = new ClassPathResource(path).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
