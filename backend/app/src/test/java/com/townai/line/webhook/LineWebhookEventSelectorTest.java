package com.townai.line.webhook;

import com.townai.line.config.LineProperties;
import com.townai.line.dto.LineWebhookRequest;
import com.townai.line.model.LineWebhookEventPayload;
import com.townai.line.model.LineWebhookEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineWebhookEventSelectorTest {

    private static final String ALLOWED_USER_ID =
            "U8189cf6745fc0d808977bdb0b9f22995";
    private static final long TIMESTAMP = 1_692_251_666_727L;

    @Test
    void selectsAllowedActiveTextAndSupportedPostbackInRequestOrder() {
        LineWebhookEventSelector selector = selector(ALLOWED_USER_ID);
        LineWebhookRequest request = new LineWebhookRequest(
                "Ubot",
                List.of(
                        textEvent(
                                "01H810YECXQQZ37VAXPF6H9E6T",
                                ALLOWED_USER_ID,
                                "센터미나미 분위기 9점"
                        ),
                        postbackEvent(
                                "01H810YECXQQZ37VAXPF6H9E6U",
                                ALLOWED_USER_ID,
                                "action=confirm&draftId=12"
                        )
                )
        );

        List<LineWebhookEventPayload> result =
                selector.select(request);

        assertEquals(2, result.size());
        assertEquals(
                LineWebhookEventType.TEXT_MESSAGE,
                result.getFirst().eventType()
        );
        assertEquals(
                "센터미나미 분위기 9점",
                result.getFirst().messageText()
        );
        assertNull(result.getFirst().postbackData());
        assertEquals(
                Instant.ofEpochMilli(TIMESTAMP),
                result.getFirst().occurredAt()
        );
        assertEquals(
                LineWebhookEventType.POSTBACK,
                result.getLast().eventType()
        );
        assertEquals(
                "action=confirm&draftId=12",
                result.getLast().postbackData()
        );
        assertNull(result.getLast().messageText());
    }

    @Test
    void ignoresUnauthorizedNonUserInactiveAndUnsupportedEvents() {
        LineWebhookEventSelector selector = selector(ALLOWED_USER_ID);
        LineWebhookRequest.Event unauthorized = textEvent(
                "01H810YECXQQZ37VAXPF6H9E6A",
                "Uother",
                "방문 기록"
        );
        LineWebhookRequest.Event group = new LineWebhookRequest.Event(
                "message",
                TIMESTAMP,
                new LineWebhookRequest.Source("group", ALLOWED_USER_ID),
                "01H810YECXQQZ37VAXPF6H9E6B",
                "active",
                new LineWebhookRequest.Message("text", "방문 기록"),
                null
        );
        LineWebhookRequest.Event image = new LineWebhookRequest.Event(
                "message",
                TIMESTAMP,
                new LineWebhookRequest.Source("user", ALLOWED_USER_ID),
                "01H810YECXQQZ37VAXPF6H9E6C",
                "active",
                new LineWebhookRequest.Message("image", null),
                null
        );
        LineWebhookRequest.Event standby = new LineWebhookRequest.Event(
                "message",
                TIMESTAMP,
                new LineWebhookRequest.Source("user", ALLOWED_USER_ID),
                "01H810YECXQQZ37VAXPF6H9E6D",
                "standby",
                new LineWebhookRequest.Message("text", "방문 기록"),
                null
        );
        LineWebhookRequest.Event unsupportedPostback = postbackEvent(
                "01H810YECXQQZ37VAXPF6H9E6E",
                ALLOWED_USER_ID,
                "action=delete&draftId=12"
        );
        LineWebhookRequest.Event follow = new LineWebhookRequest.Event(
                "follow",
                TIMESTAMP,
                new LineWebhookRequest.Source("user", ALLOWED_USER_ID),
                "01H810YECXQQZ37VAXPF6H9E6F",
                "active",
                null,
                null
        );

        List<LineWebhookEventPayload> result = selector.select(
                new LineWebhookRequest(
                        "Ubot",
                        List.of(
                                unauthorized,
                                group,
                                image,
                                standby,
                                unsupportedPostback,
                                follow
                        )
                )
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void selectsNoEventsWhenAllowedUserIsNotConfigured() {
        LineWebhookEventSelector selector = selector("");
        LineWebhookRequest request = new LineWebhookRequest(
                "Ubot",
                List.of(textEvent(
                        "01H810YECXQQZ37VAXPF6H9E6T",
                        ALLOWED_USER_ID,
                        "방문 기록"
                ))
        );

        assertTrue(selector.select(request).isEmpty());
    }

    private LineWebhookEventSelector selector(String allowedUserId) {
        return new LineWebhookEventSelector(new LineProperties(
                "secret",
                allowedUserId
        ));
    }

    private LineWebhookRequest.Event textEvent(
            String webhookEventId,
            String userId,
            String text
    ) {
        return new LineWebhookRequest.Event(
                "message",
                TIMESTAMP,
                new LineWebhookRequest.Source("user", userId),
                webhookEventId,
                "active",
                new LineWebhookRequest.Message("text", text),
                null
        );
    }

    private LineWebhookRequest.Event postbackEvent(
            String webhookEventId,
            String userId,
            String data
    ) {
        return new LineWebhookRequest.Event(
                "postback",
                TIMESTAMP,
                new LineWebhookRequest.Source("user", userId),
                webhookEventId,
                "active",
                null,
                new LineWebhookRequest.Postback(data)
        );
    }
}
