package com.townai.line.messaging;

import com.townai.line.model.LineWebhookEventWorkItem;
import com.townai.line.model.LineWebhookEventType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineFailureNoticeSenderTest {

    private final LinePushClient pushClient = mock(LinePushClient.class);
    private final LineRetryKeyFactory retryKeyFactory =
            mock(LineRetryKeyFactory.class);
    private final LineFailureNoticeSender sender =
            new LineFailureNoticeSender(pushClient, retryKeyFactory);

    @Test
    void sendsSafeTextWithFailureNoticeRetryKey() {
        LineWebhookEventWorkItem workItem = new LineWebhookEventWorkItem(
                "event-1",
                "user-1",
                LineWebhookEventType.TEXT_MESSAGE,
                "민감한 방문 기록",
                null,
                Instant.parse("2026-07-25T10:00:00Z"),
                5
        );
        UUID retryKey = UUID.fromString(
                "0b2635bd-5e36-57a2-b342-79442921dad0"
        );
        when(retryKeyFactory.create(
                "event-1",
                LineMessagePurpose.FAILURE_NOTICE
        )).thenReturn(retryKey);

        sender.send(workItem);

        ArgumentCaptor<LinePushRequest> requestCaptor =
                ArgumentCaptor.forClass(LinePushRequest.class);
        verify(pushClient).push(requestCaptor.capture(), eq(retryKey));
        LinePushRequest request = requestCaptor.getValue();
        assertEquals("user-1", request.to());
        assertEquals(1, request.messages().size());
        LinePushRequest.TextMessage message = assertInstanceOf(
                LinePushRequest.TextMessage.class,
                request.messages().getFirst()
        );
        assertFalse(message.text().contains("민감한 방문 기록"));
        assertFalse(message.text().contains("OPENAI"));
    }
}
