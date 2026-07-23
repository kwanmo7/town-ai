package com.townai.common.openai;

/**
 * OpenAI Responses API 호출에서 Backend가 사용하는 최소 결과이다.
 *
 * @param model 응답이 실제로 사용한 모델명
 * @param output 응답에서 추출한 비어 있지 않은 최종 Text
 */
public record OpenAiResponse(
        String model,
        String output
) {
}
