package com.townai.common.openai;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * OpenAI Responses API의 공통 HTTP Client이다.
 *
 * <p>요청에는 저장 비활성화({@code store=false})를 적용하고, Structured Output과
 * 일반 Text Output의 차이는 Text Format 설정으로만 구분한다. HTTP 오류, 거절 응답,
 * 불완전한 응답과 파싱 실패는 모두 외부 API 장애를 의미하는
 * {@link ErrorCode#OPENAI_API_ERROR}로 변환한다.</p>
 */
@Component
public class OpenAiResponsesClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    /**
     * 설정된 Endpoint, 인증정보와 Timeout으로 공통 Responses API Client를 만든다.
     *
     * @param restClientBuilder Spring RestClient Builder
     * @param objectMapper 요청·응답 JSON을 처리할 ObjectMapper
     * @param baseUrl OpenAI 호환 API의 Base URL
     * @param apiKey Bearer 인증에 사용할 API Key
     * @param model Report와 Parser 요청에 사용할 기본 모델
     * @param connectTimeout TCP 연결 제한 시간
     * @param readTimeout 모델 응답 본문 수신 제한 시간
     */
    public OpenAiResponsesClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${town-ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${town-ai.openai.api-key:}") String apiKey,
            @Value("${town-ai.openai.report-model:gpt-5.4-mini}") String model,
            @Value("${town-ai.openai.connect-timeout:5s}") Duration connectTimeout,
            @Value("${town-ai.openai.read-timeout:120s}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * strict JSON Schema를 적용해 구조화된 응답을 생성한다.
     *
     * @param instructions 모델의 역할과 출력 규칙을 정의한 System 지시사항
     * @param input JSON 문자열로 직렬화해 모델에 전달할 Backend 입력
     * @param schemaName Responses API에 전달할 Schema 식별자
     * @param schema 출력 객체를 제한하는 JSON Schema
     * @return 실제 모델명과 Schema를 만족하는 원본 JSON Text
     * @throws ApiException API Key가 없거나 외부 API 호출 및 응답 검증에 실패한 경우
     */
    public OpenAiResponse generateStructured(
            String instructions,
            Object input,
            String schemaName,
            JsonNode schema
    ) {
        ObjectNode format = objectMapper.createObjectNode();
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.set("schema", schema);
        format.put("strict", true);
        return generate(instructions, input, format);
    }

    /**
     * 별도의 JSON Schema 없이 일반 Text 응답을 생성한다.
     *
     * @param instructions 모델의 역할과 출력 규칙을 정의한 System 지시사항
     * @param input JSON 문자열로 직렬화해 모델에 전달할 Backend 입력
     * @return 실제 모델명과 비어 있지 않은 Text 출력
     * @throws ApiException API Key가 없거나 외부 API 호출 및 응답 검증에 실패한 경우
     */
    public OpenAiResponse generateText(String instructions, Object input) {
        ObjectNode format = objectMapper.createObjectNode();
        format.put("type", "text");
        return generate(instructions, input, format);
    }

    private OpenAiResponse generate(
            String instructions,
            Object input,
            ObjectNode format
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(ErrorCode.OPENAI_API_ERROR);
        }

        try {
            JsonNode response = restClient.post()
                    .uri("/responses")
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(createRequest(instructions, input, format))
                    .retrieve()
                    .body(JsonNode.class);
            return parseResponse(response);
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            OpenAiErrorDetails details = readErrorDetails(
                    exception.getResponseBodyAsString()
            );
            log.error(
                    "OpenAI Responses API returned an error. "
                            + "status={}, type={}, code={}, param={}, message={}",
                    exception.getStatusCode(),
                    details.type(),
                    details.code(),
                    details.param(),
                    details.message()
            );
            throw new ApiException(ErrorCode.OPENAI_API_ERROR);
        } catch (RestClientException | JacksonException exception) {
            log.error("OpenAI Responses API request failed.", exception);
            throw new ApiException(ErrorCode.OPENAI_API_ERROR);
        }
    }

    private ObjectNode createRequest(
            String instructions,
            Object input,
            ObjectNode format
    ) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        request.put("store", false);
        request.put("instructions", instructions);
        request.put("input", objectMapper.writeValueAsString(input));

        ObjectNode text = objectMapper.createObjectNode();
        text.set("format", format);
        request.set("text", text);
        return request;
    }

    private OpenAiResponse parseResponse(JsonNode response) {
        if (response == null || !"completed".equals(response.path("status").asString(""))) {
            throw new ApiException(ErrorCode.OPENAI_API_ERROR);
        }

        for (JsonNode outputItem : response.path("output")) {
            if (!"message".equals(outputItem.path("type").asString(""))) {
                continue;
            }
            for (JsonNode content : outputItem.path("content")) {
                if ("refusal".equals(content.path("type").asString(""))) {
                    throw new ApiException(ErrorCode.OPENAI_API_ERROR);
                }
                if ("output_text".equals(content.path("type").asString(""))) {
                    String output = content.path("text").asString("");
                    if (!output.isBlank()) {
                        String responseModel = response.path("model").asString(model);
                        return new OpenAiResponse(responseModel, output);
                    }
                }
            }
        }
        throw new ApiException(ErrorCode.OPENAI_API_ERROR);
    }

    private OpenAiErrorDetails readErrorDetails(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return OpenAiErrorDetails.empty();
        }
        try {
            JsonNode error = objectMapper.readTree(responseBody).path("error");
            return new OpenAiErrorDetails(
                    safeLogValue(error.path("type").asString("")),
                    safeLogValue(error.path("code").asString("")),
                    safeLogValue(error.path("param").asString("")),
                    safeLogValue(error.path("message").asString(""))
            );
        } catch (JacksonException exception) {
            return OpenAiErrorDetails.empty();
        }
    }

    private String safeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String normalized = value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .strip();
        int maximumLength = 500;
        return normalized.length() <= maximumLength
                ? normalized
                : normalized.substring(0, maximumLength) + "...";
    }

    private record OpenAiErrorDetails(
            String type,
            String code,
            String param,
            String message
    ) {

        private static OpenAiErrorDetails empty() {
            return new OpenAiErrorDetails("-", "-", "-", "-");
        }
    }
}
