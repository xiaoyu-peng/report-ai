-- V20260420 —— 修复 init.sql 初次导入时 client 字符集错配导致的 5 条内置模板中文丢失（?????）
-- 注意：源数据已是 `??????`（而非 mojibake），CONVERT 无法还原 → 只能重写。
-- 直接覆盖式 UPDATE，不关心已有 ?????? 值；需在 utf8mb4 连接下执行。
SET NAMES utf8mb4;

UPDATE `report_template`
SET `name`               = '政策影响分析报告',
    `description`        = '解读政策变化对行业/企业的影响，含事件概述、热点梳理、影响分析、建议',
    `style_description`  = '正式、严谨、数据驱动，面向管理层决策参考',
    `structure_json`     = '{"sections":["事件概述","政策背景","热点梳理","影响分析","应对建议"],"tone":"formal","citation_style":"inline"}'
WHERE `id` = 1;

UPDATE `report_template`
SET `name`               = '行业分析报告',
    `description`        = '深度行业研究报告模板，含趋势研判、竞争格局、典型案例、发展建议',
    `style_description`  = '专业、深度、结构化，面向战略规划',
    `structure_json`     = '{"sections":["行业概况","趋势研判","竞争格局","典型案例","发展建议"],"tone":"professional","citation_style":"footnote"}'
WHERE `id` = 2;

UPDATE `report_template`
SET `name`               = '传播分析报告',
    `description`        = '舆情传播与品牌效果分析，含声量渠道、热文节点、观点分布、传播评估',
    `style_description`  = '客观、数据可视化、面向公关传播',
    `structure_json`     = '{"sections":["事件回顾","声量与渠道分析","关键传播节点","情感与观点分布","传播效果评估"],"tone":"objective","citation_style":"inline"}'
WHERE `id` = 3;

UPDATE `report_template`
SET `name`               = '科技情报专题报告',
    `description`        = '技术发展跟踪、专利分析、竞品研究模板',
    `style_description`  = '前沿、精准、技术导向，面向研发决策',
    `structure_json`     = '{"sections":["技术进展","专利分析","竞品动态","趋势预测"],"tone":"technical","citation_style":"inline"}'
WHERE `id` = 4;

UPDATE `report_template`
SET `name`               = '专题日报周报',
    `description`        = '定期追踪特定主题动态的轻量速递模板',
    `style_description`  = '简洁、高效、要点化，面向日常信息追踪',
    `structure_json`     = '{"sections":["今日要闻","重点数据","趋势提示"],"tone":"concise","citation_style":"inline"}'
WHERE `id` = 5;

-- 防丢失：强制整表按 utf8mb4_unicode_ci 存储（理论上 init.sql 已经定义，这里是 double guard）
ALTER TABLE `report_template`
  MODIFY `name`              VARCHAR(100)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  MODIFY `description`       VARCHAR(500)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  MODIFY `style_description` TEXT          CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  MODIFY `structure_json`    TEXT          CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
