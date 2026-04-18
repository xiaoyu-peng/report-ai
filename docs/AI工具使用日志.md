# AI 工具使用日志

> 赛题硬性要求（`docs/竞赛题目/AI闯关赛第二站.pdf` 第 1 页"总体要求"第 3 条：必须提交 AI 工具使用日志，无日志不得参加答辩）。
> 规则：每一个 commit / 每一次重要决策都追加一行。列：日期时间、负责角色、使用的 AI 工具、解决的问题、产出 / commit 号。

## 时间线

| 时间 | 角色 | AI 工具 | 解决的问题 | 产出 / commit |
| --- | --- | --- | --- | --- |
| 2026-04-18 10:41 | Leo | Claude Code (Opus 4.7) | 分析赛题 + 目录整理 + 初始化 CLAUDE.md | `40782bf` |
| 2026-04-18 11:00 | Vera | Claude Code + curl | 探测晴天 MCP（search + sass）可达性，拉 `tools/list`（3 + 41 工具） | `docs/mcp-integration.md`、`docs/mcp-schemas/*.json` |
| 2026-04-18 11:05 | Vera | pdftoppm + Claude 多模态读图 | 从赛题 PDF T5 页抽需求与评分标准 | `docs/requirements.md` |
| 2026-04-18 11:08 | Vera | pdftoppm + Claude 多模态读图 | 识别 5 类样例报告的结构与风格 | `docs/report-style-guide.md` |
| 2026-04-18 11:10 | Leo | Git + GitHub API | 初始化仓库、加 .gitignore、推 docs baseline | `40782bf` |
| 2026-04-18 11:14 | Arch | Claude Code | 生成 Cherry Studio 配置片段（UI + config.json + curl 对照） | `f5f9891` |
| 2026-04-18 11:18 | Arch | Claude Code + jq | 按 7 类分组整理 sass-mcp 41 个工具 + 必填参数 | `b087c77` |
| 2026-04-18 11:23 | Arch | Claude Code + Python | 持久化 tools/list 快照到 `docs/mcp-schemas/` | `dd7d0db` |
| 2026-04-18 11:25 | Arch | WebSearch + WebFetch | 调研 2026 可用 MCP 生态（filesystem/github/mysql/Docling/Qdrant） | `feaf0eb` - `docs/additional-mcps.md` |
| 2026-04-18 11:30 | Bolt | Claude Code + Docker | Task 1: docker-compose.yml + init.sql + Dockerfile；起 MySQL 验证 9 张表 + FULLTEXT 生效 | `c0e62c8` |
| 2026-04-18 11:36 | Bolt | Claude Code + sed + mvn | Task 2: 移植第一站 common 模块 27 类，批量改包名 `skillmcp.hub → reportai.hub`，mvn compile 通过 | `b3283ed` |
| 2026-04-18 11:40 | Leo | Claude Code | 引入 7 角色虚拟 AI 团队并映射到产物清单 | `408f52f` |
| 2026-04-18 11:50 | Lens | Claude Code | 写精简 PRD：5 用户故事 + 30 个 v1 接口契约 + 验收清单 | 本 commit |
| 2026-04-18 11:52 | Leo | Claude Code | 启动 AI 工具使用日志（本文件） | 本 commit |
| 2026-04-18 11:50 | Bolt | Claude Code + sed + mvn | Task 3: 移植 22 个 system 文件（user/role/department/log），遇到 LogController 引用 SkillMCP-only 类，改写为仅保留 operation 查询；pom.xml 临时 exclude 4 个 Ark/Doubao 文件待 Task 5 处理 | `867a7fb` |
| 2026-04-18 11:56 | Leo | git | 发现 `.doubao`（含 api-key）被 `git add` 进 index，撤回 + 加 gitignore + push 固化 | `ee2a6e9` |
| 2026-04-18 12:04 | Bolt | Claude Code + Tika 2.9 + MySQL FULLTEXT | Task 4 knowledge 模块（21 类，BUILD SUCCESS）：KB/Doc/Chunk 三层实体 + Tika 解析 + 段落+句子两级分块（500 char + 50 overlap）+ Jsoup URL 抓取 + FULLTEXT BOOLEAN MODE 检索（CJK 单字分词） | `9a01bf3` |
| 2026-04-18 12:16 | Bolt | Claude Code + Anthropic Messages API + JDK HttpClient SSE | Task 5 report 模块（27 类, BUILD SUCCESS）：Report/Template/Version 3 表 + LlmClient 抽象 + ClaudeLlmClient (JDK HttpClient 裸 SSE 解析) + Prompts 中心化 (风格分析/生成/4 模式改写) + ReportGeneration/Rewrite/Version/Template 4 个 Service + 4 个 Controller（含 SseEmitter 的 GET /generate 和 POST /rewrite） | 待 commit |

## 使用的 AI 工具汇总

- **Claude Code CLI（Opus 4.7, 1M context）**：主力 IDE，90% 的编码 / 文档 / 决策在此完成；
- **Claude 多模态**：直接读取 PDF 转 PNG 后的图像，抽赛题要求和样例结构；
- **Cherry Studio（MacOS）**：挂 4 个 MCP（qingtian-search / qingtian-sass / context7 / figma）做轮次调试；
- **context7 MCP**：拉 Vue 3 / Spring Boot / MyBatis Plus 实时文档；
- **晴天自研 MCP**：`search-mcp`（3 个工具）+ `sass-mcp`（41 个工具），用于"传播分析报告"类的真实数据接入；
- **pdftoppm / pdftotext**：PDF → 图像 / 文本提取；
- **jq + Python**：解析 MCP tools/list JSON；
- **Docker Compose + MySQL 8**：起数据库验证 init.sql；
- **Maven 3.9 + JDK 17**：后端构建；
- **Git + GitHub**：全程版本控制 + 远端推送（https://github.com/xiaoyu-peng/report-ai）。

## 典型对话片段（选 3 例存档，答辩用）

1. **"赛题 PDF 拿不到文本怎么办"**
   - 问题：`pdftotext` 因 PDF 字段问题失败；
   - 方案：改用 `pdftoppm -r 250 -png` 转图像，让 Claude 多模态直接 OCR + 理解；
   - 结果：一次性拿到 T5 完整需求 + 评分细则。

2. **"7 类 MCP 工具怎么快速整理"**
   - 问题：sass-mcp 返回 41 个工具，单独看都有冗长描述；
   - 方案：Python 脚本 + `required` 字段排重，按"共用参数 § 3.0"抽出来，个别工具只列额外必填；
   - 结果：261 行表就涵盖 41 个工具，代码生成器可直接 `jq` 消费。

3. **"第一站代码怎么快速复用"**
   - 问题：第一站 `com.skillmcp.hub` 包名和 SkillMCP 领域词要迁到 `com.reportai.hub`；
   - 方案：`sed 's|com\.skillmcp\.hub|com.reportai.hub|g'` 批量改，用 grep 白名单只拿通用的 27 个类，跳过 Skill/Mcp/ApiKey 等业务类；
   - 结果：4 分钟完成移植，`mvn compile` 首次就过。
