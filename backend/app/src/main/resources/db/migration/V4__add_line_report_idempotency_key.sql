-- 같은 LINE Webhook Event가 Cloud Tasks에서 재처리돼도
-- AI Report와 Storage 객체가 중복 생성되지 않도록 원본 Event ID를 보존한다.
ALTER TABLE `report`
    ADD COLUMN `source_webhook_event_id` VARCHAR(64) NULL
        COMMENT 'LINE에서 생성을 요청한 webhookEventId' AFTER `storage_path`,
    ADD CONSTRAINT `UK_REPORT_SOURCE_WEBHOOK_EVENT`
        UNIQUE (`source_webhook_event_id`);
