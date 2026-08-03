-- 친구 추가 또는 차단 해제 시 메인 메뉴를 전송할 수 있도록
-- LINE Webhook 이벤트 유형과 Payload 제약에 FOLLOW를 추가한다.
ALTER TABLE `line_webhook_event`
    DROP CHECK `CHK_LINE_WEBHOOK_EVENT_TYPE`,
    DROP CHECK `CHK_LINE_WEBHOOK_EVENT_PAYLOAD`;

ALTER TABLE `line_webhook_event`
    ADD CONSTRAINT `CHK_LINE_WEBHOOK_EVENT_TYPE`
        CHECK (`event_type` IN ('FOLLOW', 'TEXT_MESSAGE', 'POSTBACK')),
    ADD CONSTRAINT `CHK_LINE_WEBHOOK_EVENT_PAYLOAD`
        CHECK (
            (
                `event_type` = 'FOLLOW'
                AND `message_text` IS NULL
                AND `postback_data` IS NULL
            )
            OR
            (
                `event_type` = 'TEXT_MESSAGE'
                AND `message_text` IS NOT NULL
                AND `postback_data` IS NULL
            )
            OR
            (
                `event_type` = 'POSTBACK'
                AND `message_text` IS NULL
                AND `postback_data` IS NOT NULL
            )
        );
