package com.townai.line.dispatcher;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * LINE Webhook Event ID로부터 Cloud Tasks에서 사용할 결정적 Task ID를 만든다.
 *
 * <p>외부 ID에 Task 이름에서 허용하지 않는 문자가 포함돼도 안전하도록 원문을
 * SHA-256 Hex로 변환한다. 같은 Webhook Event ID는 항상 같은 Task ID가 되므로
 * Cloud Tasks의 Task 이름 기반 중복 방지를 사용할 수 있다.</p>
 */
@Component
public class LineTaskIdFactory {

    /**
     * Webhook Event ID를 Cloud Tasks Task ID로 변환한다.
     *
     * @param webhookEventId LINE Webhook Event ID
     * @return {@code line-} Prefix와 SHA-256 Hex로 구성된 Task ID
     */
    public String create(String webhookEventId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    webhookEventId.getBytes(StandardCharsets.UTF_8)
            );
            return "line-" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }
    }
}
