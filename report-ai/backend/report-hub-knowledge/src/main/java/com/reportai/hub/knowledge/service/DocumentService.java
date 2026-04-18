package com.reportai.hub.knowledge.service;

import com.reportai.hub.knowledge.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

    /** 上传并解析：返回文档 ID；chunk 会异步 / 同步写入 knowledge_chunk。 */
    KnowledgeDocument upload(Long kbId, MultipartFile file, Long operatorId);

    /** 从 URL 抓取网页正文后入库。 */
    KnowledgeDocument importFromUrl(Long kbId, String url, String filename, Long operatorId);

    /** 软删文档 + 清 chunk。 */
    void deleteCascade(Long docId);
}
