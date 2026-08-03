package com.townai.line.webhook;

import com.townai.line.config.LineProperties;
import com.townai.line.dto.LineWebhookRequest;
import com.townai.line.model.LineWebhookEventPayload;
import com.townai.line.model.LineWebhookEventType;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 검증된 Webhook 요청에서 Town AI V1이 처리할 이벤트만 선별한다.
 *
 * <p>허용된 개인 사용자의 Active 1:1 대화에서 발생한 Text Message와
 * Follow Event와 지원하는 메뉴·Draft Postback만 내부 Payload로 변환한다. 다른 사용자, Group·Room,
 * Standby Channel, Text가 아닌 Message와 지원하지 않는 이벤트는 정보 노출 없이
 * 무시한다.</p>
 */
@Component
public class LineWebhookEventSelector {

    private static final int MAX_WEBHOOK_EVENT_ID_LENGTH = 64;
    private static final int MAX_LINE_USER_ID_LENGTH = 64;
    private static final int MAX_POSTBACK_DATA_LENGTH = 255;
    private static final Pattern SUPPORTED_POSTBACK = Pattern.compile(
            "^(?:action=(?:confirm|edit|cancel)&draftId=[1-9][0-9]*"
                    + "|action=menu&target=(?:main|visit-register|report)"
                    + "|action=report-type&reportType=(?:AREA|COMPARE|SUMMARY|ALL)"
                    + "|action=compare-toggle&areaId=[1-9][0-9]*"
                    + "(?:&selectedAreaIds=[1-9][0-9]*(?:,[1-9][0-9]*)*)?"
                    + "|action=report-generate&reportType=AREA&areaId=[1-9][0-9]*"
                    + "|action=report-generate&reportType=COMPARE&areaIds="
                    + "[1-9][0-9]*(?:,[1-9][0-9]*){1,4})$"
    );

    private final String allowedUserId;

    /**
     * 개인 사용자 제한을 적용하는 Selector를 생성한다.
     *
     * @param properties 허용된 LINE User ID를 포함한 설정
     */
    public LineWebhookEventSelector(LineProperties properties) {
        this.allowedUserId = properties.allowedUserId();
    }

    /**
     * 한 Webhook 요청에 포함된 지원 이벤트를 요청 순서대로 반환한다.
     *
     * @param request 서명 검증 후 역직렬화한 Webhook 요청
     * @return 영속화 가능한 최소 이벤트 Payload 목록
     */
    public List<LineWebhookEventPayload> select(
            LineWebhookRequest request
    ) {
        if (request == null
                || allowedUserId == null
                || allowedUserId.isBlank()) {
            return List.of();
        }
        return request.events().stream()
                .map(this::select)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<LineWebhookEventPayload> select(
            LineWebhookRequest.Event event
    ) {
        if (!hasSupportedCommonFields(event)) {
            return Optional.empty();
        }

        return switch (event.type()) {
            case "follow" -> selectFollow(event);
            case "message" -> selectTextMessage(event);
            case "postback" -> selectPostback(event);
            default -> Optional.empty();
        };
    }

    private Optional<LineWebhookEventPayload> selectFollow(
            LineWebhookRequest.Event event
    ) {
        return Optional.of(new LineWebhookEventPayload(
                event.webhookEventId(),
                event.source().userId(),
                LineWebhookEventType.FOLLOW,
                null,
                null,
                Instant.ofEpochMilli(event.timestamp())
        ));
    }

    private boolean hasSupportedCommonFields(
            LineWebhookRequest.Event event
    ) {
        if (event == null
                || event.type() == null
                || !"active".equals(event.mode())
                || !hasLength(
                        event.webhookEventId(),
                        MAX_WEBHOOK_EVENT_ID_LENGTH
                )
                || event.timestamp() == null
                || event.timestamp() < 0
                || event.source() == null
                || !"user".equals(event.source().type())
                || !hasLength(
                        event.source().userId(),
                        MAX_LINE_USER_ID_LENGTH
                )
                || !allowedUserId.equals(event.source().userId())) {
            return false;
        }
        try {
            Instant.ofEpochMilli(event.timestamp());
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }

    private Optional<LineWebhookEventPayload> selectTextMessage(
            LineWebhookRequest.Event event
    ) {
        LineWebhookRequest.Message message = event.message();
        if (message == null
                || !"text".equals(message.type())
                || message.text() == null) {
            return Optional.empty();
        }
        return Optional.of(new LineWebhookEventPayload(
                event.webhookEventId(),
                event.source().userId(),
                LineWebhookEventType.TEXT_MESSAGE,
                message.text(),
                null,
                Instant.ofEpochMilli(event.timestamp())
        ));
    }

    private Optional<LineWebhookEventPayload> selectPostback(
            LineWebhookRequest.Event event
    ) {
        LineWebhookRequest.Postback postback = event.postback();
        if (postback == null
                || !hasLength(
                        postback.data(),
                        MAX_POSTBACK_DATA_LENGTH
                )
                || !SUPPORTED_POSTBACK.matcher(postback.data()).matches()) {
            return Optional.empty();
        }
        return Optional.of(new LineWebhookEventPayload(
                event.webhookEventId(),
                event.source().userId(),
                LineWebhookEventType.POSTBACK,
                null,
                postback.data(),
                Instant.ofEpochMilli(event.timestamp())
        ));
    }

    private boolean hasLength(String value, int maximumLength) {
        return value != null
                && !value.isBlank()
                && value.length() <= maximumLength;
    }
}
