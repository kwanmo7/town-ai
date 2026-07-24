package com.townai.line.webhook;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.line.config.LineProperties;
import com.townai.line.model.LineWebhookEventPayload;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LineWebhookRequestProcessorTest {

    private static final String CHANNEL_SECRET = "test-channel-secret";
    private static final String ALLOWED_USER_ID =
            "U8189cf6745fc0d808977bdb0b9f22995";

    @Test
    void verifiesBeforeParsingAndSelectsSupportedEvent() {
        LineWebhookRequestProcessor processor = processor();
        byte[] body = """
                {
                  "destination": "Ubot",
                  "events": [
                    {
                      "type": "message",
                      "message": {
                        "type": "text",
                        "id": "468789577898262530",
                        "text": "센터미나미 분위기 9점"
                      },
                      "webhookEventId": "01H810YECXQQZ37VAXPF6H9E6T",
                      "deliveryContext": {"isRedelivery": false},
                      "timestamp": 1692251666727,
                      "source": {
                        "type": "user",
                        "userId": "U8189cf6745fc0d808977bdb0b9f22995"
                      },
                      "replyToken": "not-stored",
                      "mode": "active"
                    },
                    {
                      "type": "message",
                      "message": {"type": "text", "text": "다른 사용자"},
                      "webhookEventId": "01H810YECXQQZ37VAXPF6H9E6U",
                      "timestamp": 1692251666727,
                      "source": {"type": "user", "userId": "Uother"},
                      "mode": "active"
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);

        List<LineWebhookEventPayload> result = processor.verifyAndSelect(
                body,
                sign(body)
        );

        assertEquals(1, result.size());
        assertEquals(
                "01H810YECXQQZ37VAXPF6H9E6T",
                result.getFirst().webhookEventId()
        );
        assertEquals(
                "센터미나미 분위기 9점",
                result.getFirst().messageText()
        );
    }

    @Test
    void rejectsInvalidSignatureBeforeAttemptingJsonParsing() {
        LineWebhookRequestProcessor processor = processor();
        byte[] malformedBody = "{".getBytes(StandardCharsets.UTF_8);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> processor.verifyAndSelect(
                        malformedBody,
                        "invalid-signature"
                )
        );

        assertEquals(
                ErrorCode.INVALID_LINE_SIGNATURE,
                exception.errorCode()
        );
    }

    @Test
    void rejectsMalformedJsonAfterSuccessfulSignatureVerification() {
        LineWebhookRequestProcessor processor = processor();
        byte[] malformedBody = "{".getBytes(StandardCharsets.UTF_8);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> processor.verifyAndSelect(
                        malformedBody,
                        sign(malformedBody)
                )
        );

        assertEquals(ErrorCode.MALFORMED_REQUEST, exception.errorCode());
    }

    private LineWebhookRequestProcessor processor() {
        LineProperties properties = new LineProperties(
                CHANNEL_SECRET,
                ALLOWED_USER_ID
        );
        ObjectMapper objectMapper = JsonMapper.builder()
                .findAndAddModules()
                .build();
        return new LineWebhookRequestProcessor(
                new LineWebhookSignatureVerifier(properties),
                new LineWebhookEventSelector(properties),
                objectMapper
        );
    }

    private String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    CHANNEL_SECRET.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return Base64.getEncoder().encodeToString(mac.doFinal(body));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
