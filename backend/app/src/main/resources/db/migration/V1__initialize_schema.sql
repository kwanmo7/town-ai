-- =========================================================
-- AREA
-- =========================================================

CREATE TABLE `area` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'id',
    `name` VARCHAR(25) NOT NULL COMMENT '동네 이름',
    `prefecture` VARCHAR(20) NOT NULL COMMENT '도 및 현 이름',
    `city` VARCHAR(20) NOT NULL COMMENT '마을 및 도시 이름',
    `station` VARCHAR(50) NULL COMMENT '가장 가까운 역 이름',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    `deleted_at` TIMESTAMP NULL DEFAULT NULL COMMENT '논리 삭제일',

    CONSTRAINT `PK_AREA`
        PRIMARY KEY (`id`),

    CONSTRAINT `UK_AREA_LOCATION`
        UNIQUE (`prefecture`, `city`, `name`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- =========================================================
-- REPORT
-- =========================================================

CREATE TABLE `report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'id',
    `report_type` VARCHAR(10) NOT NULL
        COMMENT 'SUMMARY / ALL / AREA / COMPARE',
    `model` VARCHAR(50) NOT NULL COMMENT 'AI 모델',
    `prompt_version` VARCHAR(30) NOT NULL COMMENT 'AI 프롬프트 버전',
    `storage_path` VARCHAR(255) NULL
        COMMENT 'Report ID 선점 Transaction 안에서만 임시 NULL 허용',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT `PK_REPORT`
        PRIMARY KEY (`id`),

    CONSTRAINT `CHK_REPORT_TYPE`
        CHECK (`report_type` IN ('SUMMARY', 'ALL', 'AREA', 'COMPARE'))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- =========================================================
-- VISIT
-- =========================================================

CREATE TABLE `visit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'id',
    `area_id` BIGINT NOT NULL COMMENT 'Area ID',
    `visit_date` DATE NOT NULL COMMENT '방문일',
    `atmosphere_score` TINYINT NOT NULL COMMENT '분위기',
    `infra_score` TINYINT NOT NULL COMMENT '생활 인프라',
    `clean_score` TINYINT NOT NULL COMMENT '청결',
    `size_score` TINYINT NOT NULL COMMENT '넓은 집 가능성',
    `access_score` TINYINT NOT NULL COMMENT '접근성',
    `memo` TEXT NULL COMMENT '메모',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

    CONSTRAINT `PK_VISIT`
        PRIMARY KEY (`id`),

    CONSTRAINT `CHK_VISIT_ATMOSPHERE_SCORE`
        CHECK (`atmosphere_score` BETWEEN 0 AND 10),

    CONSTRAINT `CHK_VISIT_INFRA_SCORE`
        CHECK (`infra_score` BETWEEN 0 AND 10),

    CONSTRAINT `CHK_VISIT_CLEAN_SCORE`
        CHECK (`clean_score` BETWEEN 0 AND 10),

    CONSTRAINT `CHK_VISIT_SIZE_SCORE`
        CHECK (`size_score` BETWEEN 0 AND 10),

    CONSTRAINT `CHK_VISIT_ACCESS_SCORE`
        CHECK (`access_score` BETWEEN 0 AND 10),

    CONSTRAINT `FK_VISIT_AREA`
        FOREIGN KEY (`area_id`)
        REFERENCES `area` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- =========================================================
-- LINE WEBHOOK EVENT
-- =========================================================

CREATE TABLE `line_webhook_event` (
    `webhook_event_id` VARCHAR(64) NOT NULL COMMENT 'LINE webhookEventId',
    `line_user_id` VARCHAR(64) NOT NULL COMMENT 'LINE User ID',
    `event_type` VARCHAR(20) NOT NULL COMMENT 'TEXT_MESSAGE / POSTBACK',
    `message_text` TEXT NULL COMMENT '텍스트 메시지 입력',
    `postback_data` VARCHAR(255) NULL COMMENT '확인 또는 취소 Postback Data',
    `status` VARCHAR(20) NOT NULL
        COMMENT 'RECEIVED / PROCESSING / COMPLETED / FAILED',
    `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '비동기 처리 시도 횟수',
    `last_error_code` VARCHAR(50) NULL COMMENT '마지막 처리 오류 코드',
    `occurred_at` TIMESTAMP NOT NULL COMMENT 'LINE 이벤트 발생 시각 UTC',
    `processing_started_at` TIMESTAMP NULL
        COMMENT '현재 처리 시도 시작 시각 UTC',
    `processed_at` TIMESTAMP NULL COMMENT '처리 완료 시각 UTC',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일 UTC',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일 UTC',

    CONSTRAINT `PK_LINE_WEBHOOK_EVENT`
        PRIMARY KEY (`webhook_event_id`),

    CONSTRAINT `CHK_LINE_WEBHOOK_EVENT_TYPE`
        CHECK (`event_type` IN ('TEXT_MESSAGE', 'POSTBACK')),

    CONSTRAINT `CHK_LINE_WEBHOOK_EVENT_PAYLOAD`
        CHECK (
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
        ),

    CONSTRAINT `CHK_LINE_WEBHOOK_EVENT_STATUS`
        CHECK (`status` IN ('RECEIVED', 'PROCESSING', 'COMPLETED', 'FAILED')),

    CONSTRAINT `CHK_LINE_WEBHOOK_EVENT_ATTEMPT_COUNT`
        CHECK (`attempt_count` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- =========================================================
-- REPORT AREA
-- =========================================================

CREATE TABLE `report_area` (
    `report_id` BIGINT NOT NULL COMMENT 'Report ID',
    `area_id` BIGINT NOT NULL COMMENT '분석 대상 Area ID',
    `display_order` INT NOT NULL COMMENT 'Report 내 지역 표시 순서',

    CONSTRAINT `PK_REPORT_AREA`
        PRIMARY KEY (`report_id`, `area_id`),

    CONSTRAINT `UK_REPORT_AREA_DISPLAY_ORDER`
        UNIQUE (`report_id`, `display_order`),

    CONSTRAINT `FK_REPORT_AREA_REPORT`
        FOREIGN KEY (`report_id`)
        REFERENCES `report` (`id`),

    CONSTRAINT `FK_REPORT_AREA_AREA`
        FOREIGN KEY (`area_id`)
        REFERENCES `area` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- =========================================================
-- LINE VISIT DRAFT
-- =========================================================

CREATE TABLE `line_visit_draft` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'id',
    `source_webhook_event_id` VARCHAR(64) NOT NULL
        COMMENT 'Draft를 생성한 LINE webhookEventId',
    `line_user_id` VARCHAR(64) NOT NULL COMMENT 'Draft 소유 LINE User ID',
    `area_id` BIGINT NULL COMMENT '파싱된 Area ID',
    `visit_date` DATE NULL COMMENT '파싱된 방문일',
    `atmosphere_score` TINYINT NULL COMMENT '분위기',
    `infra_score` TINYINT NULL COMMENT '생활 인프라',
    `clean_score` TINYINT NULL COMMENT '청결',
    `size_score` TINYINT NULL COMMENT '넓은 집 가능성',
    `access_score` TINYINT NULL COMMENT '접근성',
    `memo` TEXT NULL COMMENT '메모',
    `warnings` JSON NULL COMMENT '누락 또는 모호한 값 경고',
    `status` VARCHAR(30) NOT NULL
        COMMENT 'NEEDS_INPUT / AWAITING_CONFIRMATION / CONFIRMED / CANCELLED / EXPIRED',
    `expires_at` TIMESTAMP NOT NULL COMMENT '확인 만료 시각 UTC',
    `confirmed_visit_id` BIGINT NULL COMMENT '확정 후 생성된 Visit ID',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일 UTC',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일 UTC',

    CONSTRAINT `PK_LINE_VISIT_DRAFT`
        PRIMARY KEY (`id`),

    CONSTRAINT `UK_LINE_VISIT_DRAFT_SOURCE_EVENT`
        UNIQUE (`source_webhook_event_id`),

    CONSTRAINT `UK_LINE_VISIT_DRAFT_CONFIRMED_VISIT`
        UNIQUE (`confirmed_visit_id`),

    CONSTRAINT `CHK_LINE_VISIT_DRAFT_STATUS`
        CHECK (
            `status` IN (
                'NEEDS_INPUT',
                'AWAITING_CONFIRMATION',
                'CONFIRMED',
                'CANCELLED',
                'EXPIRED'
            )
        ),

    CONSTRAINT `CHK_LINE_VISIT_DRAFT_CONFIRMED_VISIT`
        CHECK (
            (
                `status` = 'CONFIRMED'
                AND `confirmed_visit_id` IS NOT NULL
            )
            OR
            (
                `status` <> 'CONFIRMED'
                AND `confirmed_visit_id` IS NULL
            )
        ),

    CONSTRAINT `CHK_LINE_DRAFT_ATMOSPHERE_SCORE`
        CHECK (
            `atmosphere_score` IS NULL
            OR `atmosphere_score` BETWEEN 0 AND 10
        ),

    CONSTRAINT `CHK_LINE_DRAFT_INFRA_SCORE`
        CHECK (
            `infra_score` IS NULL
            OR `infra_score` BETWEEN 0 AND 10
        ),

    CONSTRAINT `CHK_LINE_DRAFT_CLEAN_SCORE`
        CHECK (
            `clean_score` IS NULL
            OR `clean_score` BETWEEN 0 AND 10
        ),

    CONSTRAINT `CHK_LINE_DRAFT_SIZE_SCORE`
        CHECK (
            `size_score` IS NULL
            OR `size_score` BETWEEN 0 AND 10
        ),

    CONSTRAINT `CHK_LINE_DRAFT_ACCESS_SCORE`
        CHECK (
            `access_score` IS NULL
            OR `access_score` BETWEEN 0 AND 10
        ),

    CONSTRAINT `FK_LINE_DRAFT_SOURCE_EVENT`
        FOREIGN KEY (`source_webhook_event_id`)
        REFERENCES `line_webhook_event` (`webhook_event_id`),

    CONSTRAINT `FK_LINE_DRAFT_AREA`
        FOREIGN KEY (`area_id`)
        REFERENCES `area` (`id`),

    CONSTRAINT `FK_LINE_DRAFT_CONFIRMED_VISIT`
        FOREIGN KEY (`confirmed_visit_id`)
        REFERENCES `visit` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
