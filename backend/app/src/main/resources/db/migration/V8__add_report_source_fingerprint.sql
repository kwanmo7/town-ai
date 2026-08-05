ALTER TABLE `report`
    ADD COLUMN `source_fingerprint` CHAR(64) NULL
        COMMENT 'Report Prompt 입력과 버전의 SHA-256 지문'
        AFTER `prompt_version`;

CREATE INDEX `IX_REPORT_REUSE`
    ON `report` (
        `report_type`,
        `prompt_version`,
        `source_fingerprint`,
        `created_at`
    );
