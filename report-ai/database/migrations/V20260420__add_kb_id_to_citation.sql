-- V20260420__add_kb_id_to_citation.sql
-- 为 report_citation 表添加 kb_id 字段，用于跳转到原文

ALTER TABLE `report_citation`
  ADD COLUMN `kb_id` BIGINT DEFAULT NULL COMMENT '所属知识库 ID，用于跳转到原文' AFTER `doc_id`;

-- 为现有数据补充 kb_id（从 knowledge_chunk 表关联）
UPDATE `report_citation` rc
  JOIN `knowledge_chunk` kc ON rc.chunk_id = kc.id
  SET rc.kb_id = kc.kb_id
  WHERE rc.kb_id IS NULL;
