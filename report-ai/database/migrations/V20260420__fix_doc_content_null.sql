-- V20260420 —— 修复 seed 文档 content=NULL（"查看"弹窗空白的根因）
-- 原因：init.sql 首次导入时 client 字符集错配，LONGTEXT 的中文 content 落入 NULL。
-- 对策：从 knowledge_chunk 聚合正文回填 document.content —— 反正这正是 RAG 正在使用的数据。
SET NAMES utf8mb4;
-- GROUP_CONCAT 默认 1024 字节，中文报告轻松超过；先放到 10 MB 再聚合
SET SESSION group_concat_max_len = 10485760;

UPDATE `knowledge_document` d
JOIN (
  SELECT doc_id,
         GROUP_CONCAT(content ORDER BY chunk_index SEPARATOR '\n\n') AS joined,
         SUM(CHAR_LENGTH(content)) + COUNT(*) * 2 AS joined_len
  FROM `knowledge_chunk`
  GROUP BY doc_id
) c ON c.doc_id = d.id
SET d.content   = c.joined,
    d.file_size = GREATEST(d.file_size, c.joined_len)
WHERE d.content IS NULL OR d.content = '';
