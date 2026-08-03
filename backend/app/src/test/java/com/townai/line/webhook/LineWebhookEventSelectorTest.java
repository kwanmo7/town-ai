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
    void selectsFollowTextAndSupportedPostbackInRequestOrder() {
        LineWebhookEventSelector selector = selector(ALLOWED_USER_ID);
        LineWebhookRequest request = new LineWebhookRequest(
                "Ubot",
                List.of(
                        followEvent(
                                "01H810YECXQQZ37VAXPF6H9E6S",
                                ALLOWED_USER_ID
                        ),
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

        assertEquals(3, result.size());
        assertEquals(
                LineWebhookEventType.FOLLOW,
                result.getFirst().eventType()
        );
        assertNull(result.getFirst().messageText());
        assertNull(result.getFirst().postbackData());
        assertEquals(
                LineWebhookEventType.TEXT_MESSAGE,
                result.get(1).eventType()
        );
        assertEquals(
                "센터미나미 분위기 9점",
                result.get(1).messageText()
        );
        assertNull(result.get(1).postbackData());
        assertEquals(
                Instant.ofEpochMilli(TIMESTAMP),
                result.get(1).occurredAt()
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
        List<LineWebhookEventPayload> result = selector.select(
                new LineWebhookRequest(
                        "Ubot",
                        List.of(
                                unauthorized,
                                group,
                                image,
                                standby,
                                unsupportedPostback
                        )
                )
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void selectsSupportedMenuPostback() {
        LineWebhookEventSelector selector = selector(ALLOWED_USER_ID);
        LineWebhookRequest request = new LineWebhookRequest(
                "Ubot",
                List.of(postbackEvent(
                        "01H810YECXQQZ37VAXPF6H9E6M",
                        ALLOWED_USER_ID,
                        "action=menu&target=visit-register"
                ))
        );

        List<LineWebhookEventPayload> result = selector.select(request);

        assertEquals(1, result.size());
        assertEquals(
                "action=menu&target=visit-register",
                result.getFirst().postbackData()
        );
    }

    @Test
    void selectsSupportedDraftEditPostback() {
        LineWebhookEventSelector selector = selector(ALLOWED_USER_ID);
        LineWebhookRequest request = new LineWebhookRequest(
                "Ubot",
                List.of(postbackEvent(
                        "01H810YECXQQZ37VAXPF6H9E6R",
                        ALLOWED_USER_ID,
                        "action=edit&draftId=12"
                ))
        );

        List<LineWebhookEventPayload> result = selector.select(request);

        assertEquals(1, result.size());
        assertEquals(
                "action=edit&draftId=12",
                result.getFirst().postbackData()
        );
    }

    @Test
    void selectsSupportedReportPostbacks() {
        LineWebhookEventSelector selector = selector(ALLOWED_USER_ID);
        LineWebhookRequest request = new LineWebhookRequest(
                "Ubot",
                List.of(
                        postbackEvent(
                                "01H810YECXQQZ37VAXPF6H9E6N",
                                ALLOWED_USER_ID,
                                "action=report-type&reportType=COMPARE"
                        ),
                        postbackEvent(
                                "01H810YECXQQZ37VAXPF6H9E6P",
                                ALLOWED_USER_ID,
                                "action=report-generate&reportType=COMPARE&areaIds=1,2"
                        )
                )
        );

        List<LineWebhookEventPayload> result = selector.select(request);

        assertEquals(2, result.size());
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

    private LineWebhookRequest.Event followEvent(
            String webhookEventId,
            String userId
    ) {
        return new LineWebhookRequest.Event(
                "follow",
                TIMESTAMP,
                new LineWebhookRequest.Source("user", userId),
                webhookEventId,
                "active",
                null,
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
