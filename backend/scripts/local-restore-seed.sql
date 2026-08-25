-- Local 화면 확인용 Seed Area만 복구한다.
-- Visit Row는 Area Soft Delete 시 삭제되지 않으므로 별도로 변경하지 않는다.
START TRANSACTION;

UPDATE `area`
SET `deleted_at` = NULL
WHERE `deleted_at` IS NOT NULL
  AND (
      (`prefecture` = '가나가와현' AND `city` = '요코하마시 츠즈키구' AND `name` = '센터미나미')
      OR (`prefecture` = '가나가와현' AND `city` = '가와사키시 나카하라구' AND `name` = '무사시코스기')
      OR (`prefecture` = '도쿄도' AND `city` = '무사시노시' AND `name` = '키치조지')
  );

SELECT ROW_COUNT() AS `restored_area_count`;

COMMIT;

SELECT
    `id`,
    `name`,
    `prefecture`,
    `city`,
    `deleted_at`
FROM `area`
WHERE (
    (`prefecture` = '가나가와현' AND `city` = '요코하마시 츠즈키구' AND `name` = '센터미나미')
    OR (`prefecture` = '가나가와현' AND `city` = '가와사키시 나카하라구' AND `name` = '무사시코스기')
    OR (`prefecture` = '도쿄도' AND `city` = '무사시노시' AND `name` = '키치조지')
)
ORDER BY `id`;
