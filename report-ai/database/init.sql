SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 系统用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `email` varchar(100) DEFAULT NULL,
  `password_hash` varchar(255) NOT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `dept_id` bigint DEFAULT NULL,
  `role_id` bigint DEFAULT NULL,
  `status` varchar(20) DEFAULT 'active',
  `mfa_enabled` tinyint(1) DEFAULT 0,
  `avatar` varchar(500) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `last_login_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `code` varchar(50) NOT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `permissions` text DEFAULT NULL,
  `is_system` tinyint(1) DEFAULT 0,
  `description` varchar(200) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 部门表
CREATE TABLE IF NOT EXISTS `sys_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `manager_id` bigint DEFAULT NULL,
  `path` varchar(500) DEFAULT NULL,
  `level` int DEFAULT 0,
  `description` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 操作日志表
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(50) DEFAULT NULL,
  `dept_id` bigint DEFAULT NULL,
  `action` varchar(100) DEFAULT NULL,
  `action_name` varchar(200) DEFAULT NULL,
  `resource_type` varchar(50) DEFAULT NULL,
  `resource_id` varchar(100) DEFAULT NULL,
  `resource_name` varchar(200) DEFAULT NULL,
  `details` text DEFAULT NULL,
  `before_data` text DEFAULT NULL,
  `after_data` text DEFAULT NULL,
  `ip_address` varchar(50) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `session_id` varchar(100) DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL,
  `error_message` text DEFAULT NULL,
  `duration` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识库表
CREATE TABLE IF NOT EXISTS `knowledge_base` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `status` varchar(20) DEFAULT 'active',
  `doc_count` int DEFAULT 0,
  `chunk_count` int DEFAULT 0,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识文档表
CREATE TABLE IF NOT EXISTS `knowledge_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kb_id` bigint NOT NULL,
  `filename` varchar(255) NOT NULL,
  `file_type` varchar(50) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `content` longtext DEFAULT NULL,
  `status` varchar(20) DEFAULT 'processing',
  `chunk_count` int DEFAULT 0,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_kb_id` (`kb_id`),
  FULLTEXT KEY `ft_content` (`content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识分块表
CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `doc_id` bigint NOT NULL,
  `kb_id` bigint NOT NULL,
  `content` text NOT NULL,
  `chunk_index` int DEFAULT 0,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_id` (`kb_id`),
  KEY `idx_doc_id` (`doc_id`),
  FULLTEXT KEY `ft_content` (`content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 报告模板表
CREATE TABLE IF NOT EXISTS `report_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `content` longtext DEFAULT NULL,
  `style_description` text DEFAULT NULL,
  `structure_json` text DEFAULT NULL,
  `is_builtin` tinyint DEFAULT 0,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 报告表
CREATE TABLE IF NOT EXISTS `report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL,
  `content` longtext DEFAULT NULL,
  `status` varchar(20) DEFAULT 'draft',
  `kb_id` bigint DEFAULT NULL,
  `template_id` bigint DEFAULT NULL,
  `topic` varchar(500) DEFAULT NULL,
  `key_points` text DEFAULT NULL,
  `word_count` int DEFAULT 0,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_kb_id` (`kb_id`),
  KEY `idx_template_id` (`template_id`),
  FULLTEXT KEY `ft_content` (`content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 报告版本表（版本管理 + 修订痕迹评分 15%）
CREATE TABLE IF NOT EXISTS `report_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_id` bigint NOT NULL,
  `version_num` int NOT NULL,
  `title` varchar(200) DEFAULT NULL,
  `content` longtext DEFAULT NULL,
  `change_summary` varchar(500) DEFAULT NULL,
  -- initial / regenerate / rewrite_data_update / rewrite_angle_shift /
  -- rewrite_expand / rewrite_style_shift / manual_edit
  `source_mode` varchar(30) DEFAULT 'initial',
  `word_count` int DEFAULT 0,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_ver` (`report_id`, `version_num`),
  KEY `idx_report_id` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;

-- 初始数据
INSERT INTO `sys_role` (`id`, `name`, `code`, `tenant_id`, `permissions`, `is_system`, `description`) VALUES
(1, '超级管理员', 'SUPER_ADMIN', 1, '["*"]', 1, '系统超级管理员'),
(2, '管理员', 'ADMIN', 1, '["*"]', 1, '系统管理员'),
(3, '普通用户', 'USER', 1, '["read"]', 0, '普通用户');

INSERT INTO `sys_department` (`id`, `name`, `tenant_id`, `parent_id`, `path`, `level`, `description`) VALUES
(1, '总部', 1, NULL, '/1', 0, '集团总部'),
(2, '技术部', 1, 1, '/1/2', 1, '研发团队'),
(3, '产品部', 1, 1, '/1/3', 1, '产品团队');

-- 密码：admin123（BCrypt）
INSERT INTO `sys_user` (`id`, `username`, `email`, `password_hash`, `tenant_id`, `dept_id`, `role_id`, `status`) VALUES
(1, 'admin', 'admin@reportai.com', '$2a$10$gOw16OIp1UglPMs6fLtGVuMU7cvS3lfb0.3teAi.ZDhXxHSS7BgOm', 1, 1, 1, 'active');

INSERT INTO `report_template` (`id`, `name`, `description`, `is_builtin`, `style_description`, `structure_json`) VALUES
(1, '政策影响分析报告', '解读政策变化对行业/企业的影响，含事件概述、热点梳理、影响分析、建议', 1, '正式、严谨、数据驱动，面向管理层决策参考', '{"sections":["事件概述","政策背景","热点梳理","影响分析","应对建议"],"tone":"formal","citation_style":"inline"}'),
(2, '行业分析报告', '深度行业研究报告模板，含趋势研判、竞争格局、典型案例、发展建议', 1, '专业、深度、结构化，面向战略规划', '{"sections":["行业概况","趋势研判","竞争格局","典型案例","发展建议"],"tone":"professional","citation_style":"footnote"}'),
(3, '传播分析报告', '舆情传播与品牌效果分析，含声量渠道、热文节点、观点分布、传播评估', 1, '客观、数据可视化、面向公关传播', '{"sections":["事件回顾","声量与渠道分析","关键传播节点","情感与观点分布","传播效果评估"],"tone":"objective","citation_style":"inline"}'),
(4, '科技情报专题报告', '技术发展跟踪、专利分析、竞品研究模板', 1, '前沿、精准、技术导向，面向研发决策', '{"sections":["技术进展","专利分析","竞品动态","趋势预测"],"tone":"technical","citation_style":"inline"}'),
(5, '专题日报周报', '定期追踪特定主题动态的轻量速递模板', 1, '简洁、高效、要点化，面向日常信息追踪', '{"sections":["今日要闻","重点数据","趋势提示"],"tone":"concise","citation_style":"inline"}');

-- Demo 知识库
INSERT INTO `knowledge_base` (`id`, `name`, `description`, `status`, `doc_count`, `chunk_count`, `created_by`) VALUES
(1, '政策影响知识库', '政策解读、法规条文、行业影响分析相关文档', 'active', 3, 12, 1),
(2, '行业分析知识库', '行业报告、市场研究、竞争格局分析文档', 'active', 2, 8, 1),
(3, '传播分析知识库', '舆情监测、传播分析、品牌效果评估文档', 'active', 2, 10, 1);

-- Demo 知识文档
INSERT INTO `knowledge_document` (`id`, `kb_id`, `filename`, `file_type`, `file_size`, `content`, `status`, `chunk_count`, `created_by`) VALUES
(1, 1, '2025年数字经济政策解读.pdf', 'pdf', 45000, '2025年数字经济政策解读\n\n一、政策背景\n近年来，数字经济已成为推动经济高质量发展的重要引擎。2025年政府工作报告明确提出加快数字经济发展，推动数字技术与实体经济深度融合。\n\n二、核心要点\n1. 数据要素市场化配置改革\n2. 人工智能产业创新发展\n3. 数字基础设施建设提速\n4. 数字安全治理体系完善\n\n三、行业影响\n数字经济政策的持续推进将对制造业、金融业、医疗健康等行业产生深远影响，预计到2026年数字经济占GDP比重将超过45%。', 'completed', 3, 1),
(2, 1, '数据安全法实施条例要点.docx', 'docx', 32000, '数据安全法实施条例要点\n\n第一章 总则\n数据安全是国家安全的重要组成部分。本条例旨在细化数据安全法的实施要求，明确数据处理者的安全义务。\n\n第二章 数据分类分级\n国家建立数据分类分级保护制度，根据数据在经济社会发展中的重要程度以及一旦遭到篡改、破坏、泄露或者非法获取、非法利用，对国家安全、公共利益或者个人、组织合法权益造成的危害程度，对数据实行分类分级保护。\n\n第三章 数据安全风险评估\n数据处理者应当定期开展数据安全风险评估，评估报告应当包括处理的重要数据的种类、数量，数据安全风险及应对措施等内容。', 'completed', 2, 1),
(3, 2, '2025年AI行业深度研究报告.pdf', 'pdf', 88000, '2025年AI行业深度研究报告\n\n一、行业概况\n2025年全球人工智能市场规模预计突破5000亿美元，中国市场规模超过1500亿人民币。大模型技术持续突破，多模态AI成为新趋势。\n\n二、竞争格局\n国内主要厂商包括百度、阿里、字节跳动、腾讯等，在通用大模型和垂直领域模型方面各有侧重。开源生态蓬勃发展，Llama、Qwen等开源模型持续迭代。\n\n三、关键趋势\n1. 多模态融合：文本、图像、视频、语音统一理解与生成\n2. Agent化：从对话助手到自主执行复杂任务的智能体\n3. 端侧部署：大模型向手机、PC等终端设备下沉\n4. 行业深耕：金融、医疗、法律等垂直领域应用加速', 'completed', 4, 1),
(4, 3, '某品牌舆情传播分析报告.pdf', 'pdf', 56000, '某品牌舆情传播分析报告\n\n一、事件概述\n2025年3月，某知名消费品牌因产品质量问题引发网络热议，相关话题在微博、抖音等平台迅速传播。\n\n二、传播特征\n1. 传播速度：事件曝光后2小时内话题阅读量突破1亿\n2. 传播路径：微博首发→抖音短视频扩散→微信公众号深度解读→主流媒体报道\n3. 情感分布：负面情绪占比62%，中性28%，正面10%\n4. 关键意见领袖：3位百万粉丝博主参与传播\n\n三、应对建议\n1. 及时发布官方声明，回应核心关切\n2. 启动产品召回机制，展现责任担当\n3. 加强与媒体和KOL的沟通协调', 'completed', 3, 1);

-- Demo 知识分块
INSERT INTO `knowledge_chunk` (`id`, `doc_id`, `kb_id`, `content`, `chunk_index`) VALUES
(1, 1, 1, '2025年政府工作报告明确提出加快数字经济发展，推动数字技术与实体经济深度融合。数字经济已成为推动经济高质量发展的重要引擎。', 0),
(2, 1, 1, '数字经济政策的核心要点包括：数据要素市场化配置改革、人工智能产业创新发展、数字基础设施建设提速、数字安全治理体系完善。', 1),
(3, 1, 1, '预计到2026年数字经济占GDP比重将超过45%。数字经济政策的持续推进将对制造业、金融业、医疗健康等行业产生深远影响。', 2),
(4, 2, 1, '国家建立数据分类分级保护制度，根据数据在经济社会发展中的重要程度，对数据实行分类分级保护。数据处理者应当定期开展数据安全风险评估。', 0),
(5, 2, 1, '数据安全是国家安全的重要组成部分。本条例旨在细化数据安全法的实施要求，明确数据处理者的安全义务。', 1),
(6, 3, 2, '2025年全球人工智能市场规模预计突破5000亿美元，中国市场规模超过1500亿人民币。大模型技术持续突破，多模态AI成为新趋势。', 0),
(7, 3, 2, '国内主要厂商包括百度、阿里、字节跳动、腾讯等，在通用大模型和垂直领域模型方面各有侧重。开源生态蓬勃发展。', 1),
(8, 3, 2, '关键趋势：多模态融合、Agent化、端侧部署、行业深耕。金融、医疗、法律等垂直领域应用加速。', 2),
(9, 4, 3, '事件曝光后2小时内话题阅读量突破1亿。传播路径：微博首发→抖音短视频扩散→微信公众号深度解读→主流媒体报道。', 0),
(10, 4, 3, '情感分布：负面情绪占比62%，中性28%，正面10%。关键意见领袖：3位百万粉丝博主参与传播。', 1);

-- Demo 报告
INSERT INTO `report` (`id`, `title`, `content`, `status`, `kb_id`, `template_id`, `topic`, `key_points`, `word_count`, `created_by`) VALUES
(1, '2025年数字经济政策影响分析报告', '# 2025年数字经济政策影响分析报告\n\n## 一、事件概述\n\n2025年，国务院及各部委密集出台数字经济相关政策文件，从数据要素市场化、人工智能产业发展、数字基础设施建设等多个维度推动数字经济高质量发展。这些政策对传统行业数字化转型、新兴技术产业发展产生了深远影响。\n\n## 二、政策背景\n\n近年来，数字经济已成为推动经济高质量发展的重要引擎。2025年政府工作报告明确提出加快数字经济发展，推动数字技术与实体经济深度融合[1]。预计到2026年数字经济占GDP比重将超过45%[3]。\n\n## 三、热点梳理\n\n### 3.1 数据要素市场化配置改革\n数据要素作为新型生产要素，其市场化配置改革是数字经济的核心议题。国家数据局成立后，数据产权制度、数据流通交易规则、数据收益分配机制等基础制度逐步完善[4]。\n\n### 3.2 人工智能产业创新发展\n大模型技术持续突破，多模态AI成为新趋势[6]。国内主要厂商在通用大模型和垂直领域模型方面各有侧重，开源生态蓬勃发展[7]。\n\n## 四、影响分析\n\n### 4.1 对制造业的影响\n数字孪生、工业互联网等技术加速落地，制造业数字化转型进入深水区。预计到2025年底，规模以上制造业企业数字化普及率将达到75%。\n\n### 4.2 对金融业的影响\n金融科技监管沙盒扩大试点，数字人民币应用场景持续拓展，智能风控和智能投顾成为标配。\n\n## 五、应对建议\n\n1. 企业应加快数据资产化进程，建立数据治理体系\n2. 积极布局AI应用场景，提升智能化运营能力\n3. 关注数据安全合规要求，完善安全防护体系[5]\n4. 加强数字人才培养，构建数字化组织能力', 'ready', 1, 1, '2025年数字经济政策影响分析', '["数据要素市场化","人工智能产业发展","数字基础设施建设","数字安全治理"]', 856, 1),
(2, '2025年AI行业深度分析报告', '# 2025年AI行业深度分析报告\n\n## 一、行业概况\n\n2025年全球人工智能市场规模预计突破5000亿美元，中国市场规模超过1500亿人民币[6]。大模型技术持续突破，多模态AI成为新趋势。\n\n## 二、趋势研判\n\n### 2.1 多模态融合\n文本、图像、视频、语音的统一理解与生成能力成为大模型的核心竞争力。GPT-5、Gemini Ultra等模型已实现跨模态推理。\n\n### 2.2 Agent化\n从对话助手到自主执行复杂任务的智能体，AI Agent成为2025年最热门的技术方向。AutoGPT、Devin等产品展示了Agent的巨大潜力。\n\n### 2.3 端侧部署\n大模型向手机、PC等终端设备下沉，Apple Intelligence、高通AI引擎等推动端侧AI普及[8]。\n\n## 三、竞争格局\n\n国内主要厂商包括百度、阿里、字节跳动、腾讯等，在通用大模型和垂直领域模型方面各有侧重[7]。开源生态蓬勃发展，Llama、Qwen等开源模型持续迭代。\n\n## 四、典型案例\n\n### 4.1 金融领域\n智能风控系统将贷款审批效率提升300%，不良贷款率下降1.2个百分点。\n\n### 4.2 医疗领域\nAI辅助诊断系统在三甲医院试点，影像诊断准确率达到95%以上。\n\n## 五、发展建议\n\n1. 加大基础研究投入，突破核心技术瓶颈\n2. 构建开放生态，降低AI应用门槛\n3. 完善AI治理框架，确保技术安全可控\n4. 培养复合型AI人才，支撑产业持续发展', 'ready', 2, 2, '2025年AI行业深度分析', '["大模型技术突破","AI Agent发展","端侧AI部署","行业应用加速"]', 780, 1),
(3, '某品牌舆情传播分析报告', '# 某品牌舆情传播分析报告\n\n## 一、事件回顾\n\n2025年3月，某知名消费品牌因产品质量问题引发网络热议，相关话题在微博、抖音等平台迅速传播[9]。事件曝光后2小时内话题阅读量突破1亿。\n\n## 二、声量与渠道分析\n\n### 2.1 传播路径\n微博首发→抖音短视频扩散→微信公众号深度解读→主流媒体报道[9]。社交媒体成为主要传播渠道，占比超过80%。\n\n### 2.2 声量走势\n事件首日声量峰值达到日均的15倍，第3天开始回落，第7天趋于平稳。\n\n## 三、关键传播节点\n\n1. 初始爆料：微博用户@消费者之声发布产品质量问题\n2. KOL转发：3位百万粉丝博主参与传播[10]\n3. 媒体介入：多家主流媒体跟进报道\n4. 官方回应：品牌方于第2天发布声明\n\n## 四、情感与观点分布\n\n情感分布：负面情绪占比62%，中性28%，正面10%[10]。主要负面观点集中在产品质量和售后服务方面。\n\n## 五、传播效果评估\n\n1. 品牌美誉度下降约15个百分点\n2. 相关产品销量短期下滑30%\n3. 品牌方需加强质量管控和危机应对能力', 'ready', 3, 3, '某品牌舆情传播分析', '["传播路径分析","情感分布","关键传播节点","品牌影响评估"]', 620, 1),
(4, '科技情报：MCP协议发展专题报告', '# MCP协议发展专题报告\n\n## 一、技术进展\n\nMCP（Model Context Protocol）是Anthropic于2024年底发布的开放协议，旨在标准化LLM与外部工具/数据源的交互方式。2025年进入快速普及期。\n\n### 1.1 协议演进\nMCP协议从最初的stdio传输方式，扩展到SSE（Server-Sent Events）和StreamableHTTP两种新的传输方式，支持更灵活的部署场景。\n\n### 1.2 生态建设\nClaude Desktop原生支持MCP，2025年推出MCP Hub（工具市场）。Spring AI 1.0正式支持MCP Client/Server。\n\n## 二、专利分析\n\n截至2025年底，与AI工具调用协议相关的专利申请超过200件，其中中国占比35%，美国占比40%。\n\n## 三、竞品动态\n\n### 3.1 OpenAI Tool Use\nOpenAI在ChatGPT中引入类似MCP的Tool Use框架，但未完全兼容MCP协议。\n\n### 3.2 国内实践\n晴天舆情通过MCP协议开放44个舆情分析工具，是国内首个将舆情分析能力通过MCP开放的产品。\n\n## 四、趋势预测\n\n1. MCP将成为AI应用连接外部工具的事实标准\n2. 更多企业级SaaS产品将通过MCP开放能力\n3. MCP安全认证和权限管理将成为重点\n4. 多Agent协作场景下MCP将发挥更大价值', 'ready', NULL, 4, 'MCP协议发展专题', '["MCP协议演进","生态建设","竞品动态","趋势预测"]', 540, 1),
(5, '专题日报：AI行业动态速递', '# AI行业动态速递\n\n## 今日要闻\n\n1. **百度发布文心大模型4.5**：推理能力大幅提升，API定价减半\n2. **字节跳动豆包深入研究模式上线**：一键生成3000-8000字结构化研究报告\n3. **阿里云通义千问开源Qwen3**：支持120+语言，性能超越同级别模型\n\n## 重点数据\n\n- 全球AI市场规模突破5000亿美元\n- 中国AI专利申请量全球第一\n- AI应用渗透率在金融行业达到78%\n\n## 趋势提示\n\n1. 多模态AI成为大模型标配能力\n2. AI Agent从概念走向产品化\n3. 端侧AI部署加速，手机AI芯片出货量增长200%', 'ready', NULL, 5, 'AI行业动态', '["大模型发布","市场数据","趋势提示"]', 320, 1);

-- Demo 报告版本
INSERT INTO `report_version` (`id`, `report_id`, `version_num`, `title`, `content`, `source_mode`, `word_count`, `change_summary`, `created_by`) VALUES
(1, 1, 1, '2025年数字经济政策影响分析报告', '# 2025年数字经济政策影响分析报告\n\n## 一、事件概述\n\n2025年，国务院及各部委密集出台数字经济相关政策文件，从数据要素市场化、人工智能产业发展、数字基础设施建设等多个维度推动数字经济高质量发展。\n\n## 二、政策背景\n\n数字经济已成为推动经济高质量发展的重要引擎。2025年政府工作报告明确提出加快数字经济发展。\n\n## 三、热点梳理\n\n数据要素市场化配置改革、人工智能产业创新发展、数字基础设施建设提速、数字安全治理体系完善。\n\n## 四、影响分析\n\n对制造业、金融业、医疗健康等行业产生深远影响。\n\n## 五、应对建议\n\n1. 加快数据资产化进程\n2. 布局AI应用场景\n3. 关注数据安全合规', 'initial', 380, '首次 AI 生成', 1),
(2, 1, 2, '2025年数字经济政策影响分析报告', '# 2025年数字经济政策影响分析报告\n\n## 一、事件概述\n\n2025年，国务院及各部委密集出台数字经济相关政策文件，从数据要素市场化、人工智能产业发展、数字基础设施建设等多个维度推动数字经济高质量发展。这些政策对传统行业数字化转型、新兴技术产业发展产生了深远影响。\n\n## 二、政策背景\n\n近年来，数字经济已成为推动经济高质量发展的重要引擎。2025年政府工作报告明确提出加快数字经济发展，推动数字技术与实体经济深度融合[1]。预计到2026年数字经济占GDP比重将超过45%[3]。\n\n## 三、热点梳理\n\n### 3.1 数据要素市场化配置改革\n数据要素作为新型生产要素，其市场化配置改革是数字经济的核心议题。\n\n### 3.2 人工智能产业创新发展\n大模型技术持续突破，多模态AI成为新趋势[6]。\n\n## 四、影响分析\n\n### 4.1 对制造业的影响\n数字孪生、工业互联网等技术加速落地。\n\n### 4.2 对金融业的影响\n金融科技监管沙盒扩大试点。\n\n## 五、应对建议\n\n1. 企业应加快数据资产化进程\n2. 积极布局AI应用场景\n3. 关注数据安全合规要求', 'rewrite_expand', 560, '改写模式：内容扩展', 1),
(3, 2, 1, '2025年AI行业深度分析报告', '# 2025年AI行业深度分析报告\n\n## 一、行业概况\n\n2025年全球人工智能市场规模预计突破5000亿美元，中国市场规模超过1500亿人民币。\n\n## 二、趋势研判\n\n多模态融合、Agent化、端侧部署成为三大趋势。\n\n## 三、竞争格局\n\n国内主要厂商各有侧重，开源生态蓬勃发展。\n\n## 四、发展建议\n\n1. 加大基础研究投入\n2. 构建开放生态\n3. 完善AI治理框架', 'initial', 280, '首次 AI 生成', 1);
