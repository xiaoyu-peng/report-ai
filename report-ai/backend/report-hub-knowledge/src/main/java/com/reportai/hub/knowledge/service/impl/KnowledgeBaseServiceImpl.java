package com.reportai.hub.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reportai.hub.knowledge.entity.KnowledgeBase;
import com.reportai.hub.knowledge.entity.KnowledgeChunk;
import com.reportai.hub.knowledge.entity.KnowledgeDocument;
import com.reportai.hub.knowledge.mapper.KnowledgeBaseMapper;
import com.reportai.hub.knowledge.mapper.KnowledgeChunkMapper;
import com.reportai.hub.knowledge.mapper.KnowledgeDocumentMapper;
import com.reportai.hub.knowledge.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl
        extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
        implements KnowledgeBaseService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;

    @Override
    public KnowledgeBase create(String name, String description, String category, Long operatorId) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(name);
        kb.setDescription(description);
        kb.setCategory(category != null && !category.isBlank() ? category : "other");
        kb.setStatus("active");
        kb.setDocCount(0);
        kb.setChunkCount(0);
        kb.setCreatedBy(operatorId);
        save(kb);
        return kb;
    }

    @Override
    public Page<KnowledgeBase> listByPage(long current, long size, String keyword, String category) {
        return page(new Page<>(current, size),
                new LambdaQueryWrapper<KnowledgeBase>()
                        .like(keyword != null && !keyword.isBlank(),
                                KnowledgeBase::getName, keyword)
                        .eq(category != null && !category.isBlank(),
                                KnowledgeBase::getCategory, category)
                        .orderByDesc(KnowledgeBase::getCreatedAt));
    }

    @Override
    public void refreshCounters(Long kbId) {
        Long docs = documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKbId, kbId));
        Long chunks = chunkMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KnowledgeChunk>()
                        .eq("kb_id", kbId));
        KnowledgeBase kb = getById(kbId);
        if (kb == null) return;
        kb.setDocCount(docs == null ? 0 : docs.intValue());
        kb.setChunkCount(chunks == null ? 0 : chunks.intValue());
        updateById(kb);
    }
}
