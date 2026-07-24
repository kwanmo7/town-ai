package com.townai.line.messaging;

import com.townai.line.model.LineWebhookEventWorkItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 최종 처리에 실패한 LINE 이벤트의 사용자 안내 메시지를 전송한다.
 *
 * <p>오류 코드나 내부 예외는 사용자 메시지에 포함하지 않는다. 동일 이벤트의
 * 중복 호출은 {@link LineMessagePurpose#FAILURE_NOTICE} 전용 결정적 Retry Key로
 * LINE Messaging API에서 중복 수락되지 않게 한다.</p>
 */
@Component
public class LineFailureNoticeSender {

    private static final String FAILURE_MESSAGE =
            "방문 기록을 처리하지 못했습니다. "
                    + "잠시 후 내용을 다시 보내주세요.";

    private final LinePushClient pushClient;
    private final LineRetryKeyFactory retryKeyFactory;

    /**
     * 최종 실패 안내 Sender를 생성한다.
     *
     * @param pushClient LINE Push Message API Port
     * @param retryKeyFactory 이벤트별 결정적 Retry Key Factory
     */
    public LineFailureNoticeSender(
            LinePushClient pushClient,
            LineRetryKeyFactory retryKeyFactory
    ) {
        this.pushClient = pushClient;
        this.retryKeyFactory = retryKeyFactory;
    }

    /**
     * 민감한 입력이나 오류 원인을 포함하지 않은 재입력 안내를 전송한다.
     *
     * @param workItem 최종 실패한 이벤트의 안전한 처리 Snapshot
     * @throws LineMessagingException LINE이 요청을 수락하지 않은 경우
     */
    public void send(LineWebhookEventWorkItem workItem) {
        UUID retryKey = retryKeyFactory.create(
                workItem.webhookEventId(),
                LineMessagePurpose.FAILURE_NOTICE
        );
        LinePushRequest request = new LinePushRequest(
                workItem.lineUserId(),
                List.of(LinePushRequest.TextMessage.of(FAILURE_MESSAGE))
        );
        pushClient.push(request, retryKey);
    }
}
