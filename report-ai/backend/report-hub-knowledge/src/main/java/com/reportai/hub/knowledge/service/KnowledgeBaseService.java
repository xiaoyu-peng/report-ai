package com.reportai.hub.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.reportai.hub.knowledge.entity.KnowledgeBase;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    KnowledgeBase create(String name, String description, Long operatorId);

    Page<KnowledgeBase> listByPage(long current, long size, String keyword);

    /** 文档或 chunk 数量变化后刷新统计字段。 */
    void refreshCounters(Long kbId);
}
