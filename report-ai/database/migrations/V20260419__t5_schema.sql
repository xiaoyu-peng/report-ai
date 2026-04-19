-- V20260419__t5_schema.sql
-- T5 模块新增表 + 字段
-- 适用 MySQL 8.0 + utf8mb4

-- knowledge_chunk 增加段落定位
ALTER TABLE `knowledge_chunk`
  ADD COLUMN `paragraph_index` INT DEFAULT NULL COMMENT 'PDF/Word 源段落序号 (0-based)，用于引用溯源到段落';

-- report_template 增加可复用大纲 JSON
ALTER TABLE `report_template`
  ADD COLUMN `outline_json` JSON DEFAULT NULL COMMENT '可复用大纲：[{title,prompt,targetWords}...]';

-- ① 引用溯源表（核心）
CREATE TABLE IF NOT EXISTS `report_citation` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `report_id`       BIGINT       NOT NULL,
  `version_id`      BIGINT       DEFAULT NULL,
  `section_index`   INT          NOT NULL DEFAULT 0,
  `paragraph_index` INT          NOT NULL DEFAULT 0,
  `citation_marker` INT          NOT NULL COMMENT '正文 [n] 编号',
  `chunk_id`        BIGINT       NOT NULL,
  `doc_id`          BIGINT       NOT NULL,
  `doc_title`       VARCHAR(255) DEFAULT NULL,
  `page_start`      INT          DEFAULT NULL,
  `page_end`        INT          DEFAULT NULL,
  `snippet`         TEXT         DEFAULT NULL,
  `accepted`        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '0=用户排除',
  `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_citation_report` (`report_id`, `version_id`),
  KEY `idx_citation_marker` (`report_id`, `citation_marker`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告引用溯源';

-- ② 章节流式生成状态表
CREATE TABLE IF NOT EXISTS `report_section` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `report_id`      BIGINT      NOT NULL,
  `section_index`  INT         NOT NULL,
  `title`          VARCHAR(255) DEFAULT NULL,
  `prompt`         TEXT        DEFAULT NULL,
  `status`         VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/generating/done/failed',
  `content`        LONGTEXT    DEFAULT NULL,
  `word_count`     INT         NOT NULL DEFAULT 0,
  `citation_count` INT         NOT NULL DEFAULT 0,
  `started_at`     DATETIME    DEFAULT NULL,
  `finished_at`    DATETIME    DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_section_idx` (`report_id`, `section_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节级生成状态';

-- ③ 用户排除的引用块
CREATE TABLE IF NOT EXISTS `report_excluded_chunk` (
  `report_id` BIGINT NOT NULL,
  `chunk_id`  BIGINT NOT NULL,
  PRIMARY KEY (`report_id`, `chunk_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户排除的引用块';

-- ④ 报告质量检查结果
CREATE TABLE IF NOT EXISTS `report_quality` (
  `report_id`        BIGINT       NOT NULL,
  `coverage_rate`    DECIMAL(5,2) DEFAULT NULL COMMENT '0~100',
  `citations_total`  INT          DEFAULT 0,
  `paragraphs_total` INT          DEFAULT 0,
  `paragraphs_cited` INT          DEFAULT 0,
  `kb_distribution`  JSON         DEFAULT NULL COMMENT '{"行业报告":12,"政策法规":4}',
  `suspicious_facts` JSON         DEFAULT NULL COMMENT '[{text,reason,severity}]',
  `checked_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告质量体检';

-- ⑤ report_version 增加 before_content 字段（diff 视图所需）
ALTER TABLE `report_version`
  ADD COLUMN `before_content` LONGTEXT DEFAULT NULL COMMENT '改写前原文，用于 diff 三色对比';
