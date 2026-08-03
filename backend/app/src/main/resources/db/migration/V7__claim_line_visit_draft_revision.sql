-- 수정 Text Message 하나가 원본 Draft를 AI 호출 전에 점유하게 해 취소·중복 입력과
-- 비동기 Task 경합에서도 수정 결과가 일반 신규 Draft로 저장되지 않도록 한다.
ALTER TABLE `line_webhook_event`
    ADD COLUMN `revision_source_draft_id` BIGINT NULL
        AFTER `last_error_code`;

ALTER TABLE `line_visit_draft`
    DROP CHECK `CHK_LINE_VISIT_DRAFT_STATUS`;

ALTER TABLE `line_visit_draft`
    ADD COLUMN `revision_webhook_event_id` VARCHAR(64) NULL
        AFTER `expires_at`,
    ADD CONSTRAINT `UK_LINE_VISIT_DRAFT_REVISION_EVENT`
        UNIQUE (`revision_webhook_event_id`),
    ADD CONSTRAINT `FK_LINE_DRAFT_REVISION_EVENT`
        FOREIGN KEY (`revision_webhook_event_id`)
        REFERENCES `line_webhook_event` (`webhook_event_id`),
    ADD CONSTRAINT `CHK_LINE_VISIT_DRAFT_STATUS`
        CHECK (
            `status` IN (
                'NEEDS_INPUT',
                'AWAITING_CONFIRMATION',
                'AWAITING_REVISION',
                'REVISION_PROCESSING',
                'SUPERSEDED',
                'CONFIRMED',
                'CANCELLED',
                'EXPIRED'
            )
        ),
    ADD CONSTRAINT `CHK_LINE_DRAFT_REVISION_EVENT`
        CHECK (
            (
                `status` = 'REVISION_PROCESSING'
                AND `revision_webhook_event_id` IS NOT NULL
            )
            OR
            (
                `status` <> 'REVISION_PROCESSING'
                AND `revision_webhook_event_id` IS NULL
            )
        );
