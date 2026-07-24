package com.townai.line.webhook;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.line.config.LineProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LineWebhookSignatureVerifierTest {

    private static final String OFFICIAL_CHANNEL_SECRET =
            "8c570fa6dd201bb328f1c1eac23a96d8";
    private static final String OFFICIAL_BODY =
            "{\"destination\":\"U8e742f61d673b39c7fff3cecb7536ef0\",\"events\":[]}";
    private static final String OFFICIAL_SIGNATURE =
            "GhRKmvmHys4Pi8DxkF4+EayaH0OqtJtaZxgTD9fMDLs=";

    @Test
    void acceptsOfficialLineSignatureExample() {
        LineWebhookSignatureVerifier verifier =
                verifier(OFFICIAL_CHANNEL_SECRET);

        assertDoesNotThrow(() -> verifier.verify(
                OFFICIAL_BODY.getBytes(StandardCharsets.UTF_8),
                OFFICIAL_SIGNATURE
        ));
    }

    @Test
    void rejectsBodyChangedBeforeVerification() {
        LineWebhookSignatureVerifier verifier =
                verifier(OFFICIAL_CHANNEL_SECRET);
        byte[] formattedBody = """
                {
                  "destination": "U8e742f61d673b39c7fff3cecb7536ef0",
                  "events": []
                }
                """.getBytes(StandardCharsets.UTF_8);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> verifier.verify(formattedBody, OFFICIAL_SIGNATURE)
        );

        assertEquals(
                ErrorCode.INVALID_LINE_SIGNATURE,
                exception.errorCode()
        );
    }

    @Test
    void rejectsMissingMalformedOrMisconfiguredSignature() {
        LineWebhookSignatureVerifier verifier =
                verifier(OFFICIAL_CHANNEL_SECRET);
        byte[] body = OFFICIAL_BODY.getBytes(StandardCharsets.UTF_8);

        assertInvalid(verifier, body, null);
        assertInvalid(verifier, body, "not-base64");
        assertInvalid(verifier(""), body, OFFICIAL_SIGNATURE);
    }

    private void assertInvalid(
            LineWebhookSignatureVerifier verifier,
            byte[] body,
            String signature
    ) {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> verifier.verify(body, signature)
        );
        assertEquals(
                ErrorCode.INVALID_LINE_SIGNATURE,
                exception.errorCode()
        );
    }

    private LineWebhookSignatureVerifier verifier(String channelSecret) {
        return new LineWebhookSignatureVerifier(new LineProperties(
                channelSecret,
                "Uallowed"
        ));
    }
}
