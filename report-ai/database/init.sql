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
  `description` varchar(200) DEFAULT NULL,
  `permissions` text DEFAULT NULL,
  `status` varchar(20) DEFAULT 'active',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 部门表
CREATE TABLE IF NOT EXISTS `sys_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `sort_order` int DEFAULT 0,
  `status` varchar(20) DEFAULT 'active',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 操作日志表
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(50) DEFAULT NULL,
  `operation` varchar(100) DEFAULT NULL,
  `method` varchar(200) DEFAULT NULL,
  `params` text DEFAULT NULL,
  `ip` varchar(50) DEFAULT NULL,
  `status` int DEFAULT NULL,
  `error_msg` text DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
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

SET FOREIGN_KEY_CHECKS = 1;

-- 初始数据
INSERT INTO `sys_role` (`id`, `name`, `code`, `description`, `permissions`, `status`) VALUES
(1, '超级管理员', 'SUPER_ADMIN', '系统超级管理员', '["*"]', 'active'),
(2, '管理员', 'ADMIN', '系统管理员', '["*"]', 'active'),
(3, '普通用户', 'USER', '普通用户', '["read"]', 'active');

INSERT INTO `sys_department` (`id`, `name`, `parent_id`, `sort_order`) VALUES
(1, '总部', NULL, 1),
(2, '技术部', 1, 2),
(3, '产品部', 1, 3);

-- 密码：admin123（BCrypt）
INSERT INTO `sys_user` (`id`, `username`, `email`, `password_hash`, `tenant_id`, `dept_id`, `role_id`, `status`) VALUES
(1, 'admin', 'admin@reportai.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 1, 1, 1, 'active');

INSERT INTO `report_template` (`id`, `name`, `description`, `is_builtin`) VALUES
(1, '政策影响分析报告', '解读政策变化对行业/企业的影响，含事件概述、热点梳理、影响分析、建议', 1),
(2, '行业分析报告', '深度行业研究报告模板，含趋势研判、竞争格局、典型案例、发展建议', 1),
(3, '传播分析报告', '舆情传播与品牌效果分析，含声量渠道、热文节点、观点分布、传播评估', 1),
(4, '科技情报专题报告', '技术发展跟踪、专利分析、竞品研究模板', 1),
(5, '专题日报周报', '定期追踪特定主题动态的轻量速递模板', 1);
