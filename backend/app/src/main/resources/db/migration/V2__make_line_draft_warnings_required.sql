-- 기존 V1 데이터의 NULL을 빈 JSON 배열로 정규화한 뒤,
-- Entity가 전제로 하는 non-null warnings 제약을 DB에도 적용한다.
UPDATE `line_visit_draft`
SET `warnings` = JSON_ARRAY()
WHERE `warnings` IS NULL;

ALTER TABLE `line_visit_draft`
    MODIFY COLUMN `warnings` JSON NOT NULL
        COMMENT '누락 또는 모호한 값 경고';
