-- category column already added via ALTER TABLE earlier, skip if exists

INSERT IGNORE INTO knowledge_base (id, name, description, category, status, doc_count, chunk_count, created_by) VALUES
(1, 'AI行业知识库', '人工智能行业政策、报告与资讯', 'industry', 'active', 2, 4, 1),
(2, '政策法规库', '国家政策法规文件', 'policy', 'active', 1, 2, 1),
(3, '舆情监测库', '互联网舆情数据', 'media', 'active', 1, 4, 1);

INSERT IGNORE INTO knowledge_document (id, kb_id, filename, file_type, file_size, status, chunk_count, created_by) VALUES
(1, 1, 'AI大模型行业报告2025.pdf', 'pdf', 1024000, 'completed', 2, 1),
(2, 1, '人工智能发展趋势白皮书.pdf', 'pdf', 2048000, 'completed', 2, 1),
(3, 2, '新一代人工智能发展规划.pdf', 'pdf', 512000, 'completed', 2, 1),
(4, 3, '2025年Q1舆情监测报告.pdf', 'pdf', 1536000, 'completed', 4, 1);

INSERT IGNORE INTO knowledge_chunk (id, doc_id, kb_id, content, chunk_index) VALUES
(1, 1, 1, '2025年AI大模型产业规模达到1200亿元，同比增长35%。', 0),
(2, 1, 1, '国内AI大模型企业已超过200家，形成以百度、阿里、华为为代表的头部阵营。', 1),
(3, 2, 1, '人工智能技术正在从感知智能向认知智能演进，大模型成为技术突破的核心驱动力。', 0),
(4, 2, 1, 'AI Agent成为2025年最热门的技术方向，多智能体协作能力显著提升。', 1),
(5, 3, 2, '国务院印发《新一代人工智能发展规划》，提出到2030年人工智能总体达到世界领先水平。', 0),
(6, 3, 2, '规划明确六大重点任务：构建开放协同的科技创新体系、培育高端高效的智能经济。', 1),
(7, 4, 3, '2025年第一季度，全网AI相关舆情信息总量达到2.3亿条，环比增长18%。', 0),
(8, 4, 3, 'AI行业热点事件TOP5：1）ChatGPT-5发布；2）大模型价格战；3）AI版权争议。', 1),
(9, 4, 3, '社交媒体平台AI话题讨论热度持续攀升，微博相关话题阅读量超过500亿次。', 2),
(10, 4, 3, '主流媒体对AI报道呈现理性化趋势，深度分析类报道占比提升至38%。', 3);
