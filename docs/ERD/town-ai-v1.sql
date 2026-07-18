CREATE TABLE `report` (
	`id`	BIGINT	NOT NULL	COMMENT 'id',
	`report_type`	VARCHAR(10)	NULL	COMMENT '전체 / 지역별 / 비교',
	`model`	VARCHAR(50)	NULL	COMMENT 'AI 모델',
	`prompt_version`	VARCHAR(30)	NULL	COMMENT 'AI프롬프트 버젼',
	`storage_path`	VARCHAR(255)	NULL	COMMENT 'Cloud Storage 경로',
	`created_at`	TIMESTAMP	NULL	COMMENT '생성일',
	`updated_at`	TIMESTAMP	NULL	COMMENT '수정일'
);

CREATE TABLE `visit` (
	`id`	BIGINT	NOT NULL,
	`area_id`	BIGINT	NOT NULL	COMMENT 'id',
	`visit_date`	DATE	NOT NULL	COMMENT '방문일',
	`atmosphere_score`	TINYINT	NOT NULL	COMMENT '분위기',
	`infra_score`	TINYINT	NOT NULL	COMMENT '생활 인프라',
	`clean_score`	TINYINT	NOT NULL	COMMENT '청결',
	`size_score`	TINYINT	NOT NULL	COMMENT '넓은집 가능성',
	`access_score`	TINYINT	NOT NULL	COMMENT '접근성',
	`memo`	TEXT	NULL	COMMENT '메모',
	`created_at`	TIMESTAMP	NULL	COMMENT '생성일',
	`updated_at`	TIMESTAMP	NULL	COMMENT '수정일'
);

CREATE TABLE `area` (
	`id`	BIGINT	NOT NULL	COMMENT 'id',
	`name`	VARCHAR(25)	NOT NULL	COMMENT '동네 이름',
	`prefecture`	VARCHAR(20)	NOT NULL	COMMENT '도 및 현 이름',
	`city`	VARCHAR(20)	NOT NULL	COMMENT '마을 및 도시 이름',
	`station`	VARCHAR(50)	NULL	COMMENT '가장 가까운 역 이름',
	`created_at`	TIMESTAMP	NULL	COMMENT '생성일',
	`updated_at`	TIMESTAMP	NULL	COMMENT '수정일',
	`deleted_at`	TIMESTAMP	NULL	COMMENT '논리 삭제일'
);

CREATE TABLE `report_area` (
	`report_id`	BIGINT	NOT NULL	COMMENT 'Report ID',
	`area_id`	BIGINT	NOT NULL	COMMENT '분석 대상 Area ID',
	`display_order`	INT	NOT NULL	COMMENT 'Report 내 지역 표시 순서'
);

ALTER TABLE `report` ADD CONSTRAINT `PK_REPORT` PRIMARY KEY (
	`id`
);

ALTER TABLE `visit` ADD CONSTRAINT `PK_VISIT` PRIMARY KEY (
	`id`
);

ALTER TABLE `area` ADD CONSTRAINT `PK_AREA` PRIMARY KEY (
	`id`
);

ALTER TABLE `report_area` ADD CONSTRAINT `PK_REPORT_AREA` PRIMARY KEY (
	`report_id`,
	`area_id`
);

ALTER TABLE `area` ADD CONSTRAINT `UK_AREA_LOCATION` UNIQUE (
	`prefecture`,
	`city`,
	`name`
);

ALTER TABLE `report_area` ADD CONSTRAINT `UK_REPORT_AREA_DISPLAY_ORDER` UNIQUE (
	`report_id`,
	`display_order`
);

ALTER TABLE `visit` ADD CONSTRAINT `FK_VISIT_AREA` FOREIGN KEY (
	`area_id`
) REFERENCES `area` (
	`id`
);

ALTER TABLE `report_area` ADD CONSTRAINT `FK_REPORT_AREA_REPORT` FOREIGN KEY (
	`report_id`
) REFERENCES `report` (
	`id`
);

ALTER TABLE `report_area` ADD CONSTRAINT `FK_REPORT_AREA_AREA` FOREIGN KEY (
	`area_id`
) REFERENCES `area` (
	`id`
);
