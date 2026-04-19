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

    /**
     * 更新文档元信息 / 正文。filename 非空时 rename；content 非 null 时替换正文并重新分块。
     * 赛题模块 1.3 明确要求"知识库内容增删改查"的"改"能力。
     */
    KnowledgeDocument update(Long docId, String filename, String content, Long operatorId);

    /**
     * 重新分块（schema 升级带新字段如 paragraph_index 后用）。
     * 删旧 chunk → 用最新 chunker 重切 → 全新写入；不重新抓取文件。
     */
    KnowledgeDocument reembed(Long docId);
}
