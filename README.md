# ReportAI — 智能报告写作平台

> AI 极客挑战赛 第二站 · T5 赛题答卷

以"知识库驱动"与"以稿写稿"双引擎，打造企业/政府内部决策场景下的智能报告平台。支持参考报告风格学习、RAG 检索引用、SSE 流式生成、四种改写模式、版本管理与 Word/PDF 导出。

## 技术栈

- 后端：Java 17 · Spring Boot 3.2.5 · MyBatis Plus · MySQL 8（FULLTEXT RAG）· Redis · Apache Tika
- 前端：Vue 3 · Vite · Element Plus · Pinia
- 模型：Anthropic Claude（SSE 流式）
- 外部能力：晴天自研 MCP（信息检索 + 舆情分析，共 40+ 个工具）
- 部署：Docker Compose 一键启动

## 目录结构

```
.
├── CLAUDE.md                    # Claude Code 协作指引（中英双语）
├── README.md                    # 本文件
├── docs/
│   ├── 竞赛题目/                # 赛题 PDF + 原型图
│   ├── 以稿写稿-报告样例/       # 5 份样例报告 + 说明
│   ├── 晴天自研的MCP.txt       # MCP 端点
│   ├── plans/                   # 权威实施计划（2927 行，8 Task）
│   ├── requirements.md          # 需求分析
│   ├── report-style-guide.md    # 5 类报告风格总结
│   └── mcp-integration.md       # MCP 接入方案
└── report-ai/                   # 实际代码（Task 1 创建后生成）
    ├── backend/                 # Spring Boot 多模块
    ├── frontend/                # Vue 3
    ├── database/
    └── docker-compose.yml
```

## 快速开始

```bash
cd report-ai
export ANTHROPIC_API_KEY=sk-ant-...
docker compose up -d --build
# 打开 http://localhost:3001  admin / admin123
```

## 开发进度

- [x] 赛题分析 + 样例研究 + MCP 接入方案
- [ ] Task 1 · Docker + 数据库 + 项目骨架
- [ ] Task 2 · 后端 common 模块
- [ ] Task 3 · 后端 system 模块
- [ ] Task 4 · 后端 knowledge 模块（RAG）
- [ ] Task 5 · 后端 report 模块（生成 + 改写）
- [ ] Task 6 · 后端 api 主模块
- [ ] Task 7 · 前端 Vue 3
- [ ] Task 8 · 前端 Dockerfile + 联调

## 评分对照

| 维度 | 权重 | 对应交付 |
| --- | --- | --- |
| 知识检索准确性 | 25% | knowledge 模块 + 引用溯源视图 |
| 报告生成质量 | 25% | report 模块 + 风格学习提示词 |
| 改写质量 | 25% | 四种改写模式 + diff 展示 |
| 对比展示 / 版本管理 | 15% | 版本树 + 修订痕迹视图 |
| 系统完整性与体验 | 10% | Docker 一键部署 + 管理后台 |