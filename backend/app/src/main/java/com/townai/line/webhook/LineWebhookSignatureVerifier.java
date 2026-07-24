package com.townai.line.webhook;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.line.config.LineProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * LINE Webhook 원문 Body의 {@code X-Line-Signature}를 검증한다.
 *
 * <p>JSON 역직렬화, 문자열 정규화 또는 문자 인코딩 변환 전에 수신한 Byte 배열을
 * 그대로 HMAC-SHA256 입력으로 사용한다. 비교는 계산 결과와 Base64 Header를
 * Byte 배열로 변환한 뒤 Constant-time 비교 방식으로 수행한다.</p>
 */
@Component
public class LineWebhookSignatureVerifier {

    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final String channelSecret;

    /**
     * LINE Channel Secret을 사용하는 검증기를 생성한다.
     *
     * @param properties LINE 인증 설정
     */
    public LineWebhookSignatureVerifier(LineProperties properties) {
        this.channelSecret = properties.channelSecret();
    }

    /**
     * 수신 Header가 원문 Body와 일치하는지 검증한다.
     *
     * @param rawBody 어떤 변환도 거치지 않은 Webhook Request Body
     * @param signature {@code X-Line-Signature} Header 원문
     * @throws ApiException Secret 또는 Signature가 없거나 서명이 일치하지 않는 경우
     */
    public void verify(byte[] rawBody, String signature) {
        if (!isValid(rawBody, signature)) {
            throw new ApiException(ErrorCode.INVALID_LINE_SIGNATURE);
        }
    }

    private boolean isValid(byte[] rawBody, String signature) {
        if (channelSecret == null
                || channelSecret.isBlank()
                || signature == null
                || signature.isBlank()) {
            return false;
        }

        byte[] receivedSignature;
        try {
            receivedSignature = Base64.getDecoder().decode(signature);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(
                    channelSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA_256
            ));
            byte[] expectedSignature = mac.doFinal(
                    rawBody == null ? new byte[0] : rawBody
            );
            return MessageDigest.isEqual(
                    expectedSignature,
                    receivedSignature
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "HMAC-SHA256 is unavailable.",
                    exception
            );
        }
    }
}
