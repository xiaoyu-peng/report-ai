-- V20260420 —— file_type 列加宽：docx 的 MIME 是 73 字符，原先 varchar(50) 截断导致上传 500
ALTER TABLE `knowledge_document`
  MODIFY `file_type` varchar(150) DEFAULT NULL COMMENT 'MIME type 或扩展名';
