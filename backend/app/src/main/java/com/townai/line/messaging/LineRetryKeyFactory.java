package com.townai.line.messaging;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Webhook 이벤트와 메시지 용도로부터 RFC 4122 UUIDv5 Retry Key를 생성한다.
 */
@Component
public class LineRetryKeyFactory {

    private static final UUID NAMESPACE_URL = UUID.fromString(
            "6ba7b811-9dad-11d1-80b4-00c04fd430c8"
    );

    /**
     * LINE Retry Key Factory를 생성한다.
     */
    public LineRetryKeyFactory() {
    }

    /**
     * LINE Push API 최초 요청부터 사용할 결정적 Retry Key를 만든다.
     *
     * @param webhookEventId 메시지를 발생시킨 Webhook Event ID
     * @param purpose 한 이벤트 안의 메시지 용도
     * @return 같은 입력에 항상 동일한 UUIDv5
     */
    public UUID create(
            String webhookEventId,
            LineMessagePurpose purpose
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(uuidBytes(NAMESPACE_URL));
            digest.update(
                    ("town-ai:" + webhookEventId + ":" + purpose.name())
                            .getBytes(StandardCharsets.UTF_8)
            );
            byte[] hash = digest.digest();
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
            ByteBuffer buffer = ByteBuffer.wrap(hash);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-1 is unavailable.",
                    exception
            );
        }
    }

    private byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
