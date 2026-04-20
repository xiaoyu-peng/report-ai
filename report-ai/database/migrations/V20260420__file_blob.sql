-- V20260420 —— 知识文档原文件 blob 列，支持"查看"弹窗直接渲染 PDF / DOCX
-- 限制：上传侧由后端强制 10MB 以内；列类型 LONGBLOB 可容 4GB，实际不用那么多
ALTER TABLE `knowledge_document`
  ADD COLUMN `file_blob` LONGBLOB DEFAULT NULL
  COMMENT '原始文件二进制（<=10MB），为空表示仅有解析后的正文';
