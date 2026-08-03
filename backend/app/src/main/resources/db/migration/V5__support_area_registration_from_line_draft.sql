-- LINE에서 첫 Visit을 입력할 때 미등록 Area를 사용자 확인 후 함께 생성할 수 있도록
-- 신규 Area 후보의 위치 Snapshot과 등록 필요 여부를 Draft에 보존한다.
ALTER TABLE `line_visit_draft`
    ADD COLUMN `area_registration_required` BOOLEAN NOT NULL DEFAULT FALSE
        AFTER `area_id`,
    ADD COLUMN `area_name` VARCHAR(25) NULL
        AFTER `area_registration_required`,
    ADD COLUMN `area_prefecture` VARCHAR(20) NULL
        AFTER `area_name`,
    ADD COLUMN `area_city` VARCHAR(20) NULL
        AFTER `area_prefecture`,
    ADD COLUMN `area_station` VARCHAR(50) NULL
        AFTER `area_city`;
