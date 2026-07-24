package com.townai.line.webhook;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.line.dto.LineWebhookRequest;
import com.townai.line.model.LineWebhookEventPayload;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * LINE Webhook Controller가 사용할 수신 전처리 경계이다.
 *
 * <p>보안상 서명 검증을 가장 먼저 수행하고, 성공한 Body만 DTO로 역직렬화한다.
 * 이후 허용 사용자와 지원 이벤트 선별 결과만 다음 단계의 영속화 Service에 전달한다.</p>
 */
@Component
public class LineWebhookRequestProcessor {

    private final LineWebhookSignatureVerifier signatureVerifier;
    private final LineWebhookEventSelector eventSelector;
    private final ObjectMapper objectMapper;

    /**
     * Webhook 수신 전처리기를 생성한다.
     *
     * @param signatureVerifier 원문 Body 서명 검증기
     * @param eventSelector 허용 사용자와 지원 이벤트 Selector
     * @param objectMapper 검증된 JSON Body를 읽을 ObjectMapper
     */
    public LineWebhookRequestProcessor(
            LineWebhookSignatureVerifier signatureVerifier,
            LineWebhookEventSelector eventSelector,
            ObjectMapper objectMapper
    ) {
        this.signatureVerifier = signatureVerifier;
        this.eventSelector = eventSelector;
        this.objectMapper = objectMapper;
    }

    /**
     * 원문 요청을 검증하고 이번 요청에서 저장할 이벤트를 선별한다.
     *
     * @param rawBody 어떤 변환도 거치지 않은 Webhook Request Body
     * @param signature {@code X-Line-Signature} Header
     * @return 요청 순서를 보존한 지원 이벤트 목록
     * @throws ApiException 서명이 잘못됐거나 검증된 Body가 JSON 요청 형식이 아닌 경우
     */
    public List<LineWebhookEventPayload> verifyAndSelect(
            byte[] rawBody,
            String signature
    ) {
        signatureVerifier.verify(rawBody, signature);
        try {
            LineWebhookRequest request = objectMapper.readValue(
                    rawBody,
                    LineWebhookRequest.class
            );
            return eventSelector.select(request);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST);
        }
    }
}
