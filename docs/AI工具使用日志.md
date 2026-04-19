# AI 工具使用日志

> 赛题硬性要求（`docs/竞赛题目/AI闯关赛第二站.pdf` 第 1 页"总体要求"第 3 条：必须提交 AI 工具使用日志，无日志不得参加答辩）。
> 规则：以「里程碑 / 重要决策」为粒度合并记录，便于答辩查阅。详细 commit 历史见 `git log`。

## 时间线

| 时间 | 角色 | AI 工具 | 解决的问题 | 产出 / commit |
| --- | --- | --- | --- | --- |
| 2026-04-18 10:41 | Leo | Claude Code (Opus 4.7) + Git | 赛题解读、目录梳理、初始化 CLAUDE.md 与 .gitignore、推 docs baseline | `40782bf` |
| 2026-04-18 11:05 | Vera | pdftoppm + Claude 多模态 + curl | 多模态读图抽取赛题需求与 5 份样例报告风格；同步探测晴天 MCP（search 3 + sass 41 工具）可达性 | `docs/requirements.md`、`report-style-guide.md`、`mcp-integration.md` |
| 2026-04-18 11:14–25 | Arch | Claude Code + jq + Python + WebSearch | MCP 文档化闭环：Cherry Studio 配置片段 + 41 工具按 7 类归并 + tools/list JSON 快照持久化 + 2026 生态调研 | `f5f9891`、`b087c77`、`dd7d0db`、`feaf0eb` |
| 2026-04-18 11:30 | Bolt | Claude Code + Docker | Task 1 基础设施：docker-compose + init.sql（9 表 + FULLTEXT，含 `innodb-ft-min-token-size=1` 解决中文检索）+ 后端 Dockerfile，MySQL 冷启动验证 | `c0e62c8` |
| 2026-04-18 11:36 | Bolt | Claude Code + sed + mvn | Task 2 common 移植：第一站 27 类 `skillmcp.hub → reportai.hub` 批量改包，`mvn compile` 首次即过 | `b3283ed` |
| 2026-04-18 11:40 | Leo + Lens | Claude Code | 引入 7 角色虚拟 AI 团队；编写精简 PRD（5 用户故事 + 30 个 v1 接口契约 + 验收清单）；启动本日志 | `408f52f` |
| 2026-04-18 11:50 | Bolt | Claude Code + sed + mvn | Task 3 system 移植：22 文件（user/role/department/log），LogController 去除 SkillMCP 依赖；pom 临时 exclude 4 个 Ark/Doubao 僵尸文件 | `867a7fb` |
| 2026-04-18 11:56 | Leo | git | `.doubao`（明文 api-key）误入 index，紧急 `git rm --cached` + gitignore 固化 | `ee2a6e9` |
| 2026-04-18 12:04 | Bolt | Claude Code + Apache Tika 2.9 + MySQL FULLTEXT | Task 4 knowledge 模块（21 类）：KB/Doc/Chunk 三层实体 + Tika 解析 + 段落+句子两级分块（500 char + 50 overlap）+ Jsoup URL 抓取 + BOOLEAN MODE 检索（CJK 单字分词） | `9a01bf3` |
| 2026-04-18 12:16 | Bolt | Claude Code + Anthropic Messages API + JDK HttpClient SSE | Task 5 report 模块（27 类）：Report/Template/Version 3 表 + `LlmClient` 抽象 + `ClaudeLlmClient` 裸 SSE 解析 + `Prompts` 中心化（风格分析/生成/4 模式改写）+ 4 个 Service + 4 个 Controller（含 SseEmitter `GET /generate` 与 `POST /rewrite`）| `7e60c09` |
| 2026-04-18 16:08 | Bolt | Claude Code + Maven 3.9 + JDK 17 | Task 6 api 模块：父 POM 注册 5 子模块 + Spring Boot 主入口（@MapperScan 6 个 mapper 包）+ AuthController 从第一站移植 + DashboardController stats + application.yml 全 env var 注入；首次打出 114 MB fat jar | `ed71523` |
| 2026-04-18 16:27 | Arch | Claude Code + Volcengine Ark SDK 2.0.0 | LLM 架构下沉：按"怎么调 LLM 属 common、调 LLM 干什么属业务"原则，把 `LlmClient/LlmProperties/ClaudeLlmClient` 从 report 下沉到 common/llm；新增 `DoubaoLlmClient`，两客户端用 `@ConditionalOnProperty(provider=claude/doubao)` 互斥装配；删除 4 个 stray 僵尸文件 + pom excludes；application.yml 默认切豆包 | `897ecf5` |
| 2026-04-18 16:45 | Leo + Pixel×4 | Claude Code 多 Agent 并行调度（superpowers:dispatching-parallel-agents）| Task 7+8 前端：Leo 拆解 4 个互斥独占任务（Scaffold / Knowledge / Workspace / Templates+Docker）、先串 A 搭骨架再并发 B/C/D；Agent A 从第一站复制 layouts/stores/utils/login/users/logs + 新写 router + 3001 端口 Vite 代理；Agent B 知识库 CRUD（list 卡片 + detail 文档管理）；Agent C 工作台 SSE 流式生成（fetch+ReadableStream+UTF-8 streaming decoder，多字节不拆 + `\n\n` 帧解析 + [DONE] 哨兵 + AbortController）+ Dashboard 4 stat 卡；Agent D 模板中心 + 报告库 + Dockerfile + nginx.conf（proxy_read_timeout 300s for SSE）。`npm run build` 3.15s 产出 21 个 chunk | `1b41a13` |
| 2026-04-18 17:00 | Bolt | Claude Code + Docker Compose | 端到端冒烟：docker compose up 起 mysql/redis/backend/frontend 四件套，backend Dockerfile 改 pre-built jar 复制（避开 alpine 镜像 arm64 拉不到），补 DOUBAO_* env 透传；首次上线发现 init.sql 与第一站移植的 User/Role/Department/OperationLog 实体字段不匹配（mfa_enabled/tenant_id/is_system/path/level/action/details 等），重写 3 张表 DDL + 修 seed SQL + 用宿主 JDK17 + spring-security-crypto 重新生成 admin123 的 BCrypt；down -v 后 recreate，`/login` + `/dashboard/stats` + `/knowledge/bases` + `/templates` + `/reports` 5 个端点 200（含 nginx 代理链路）| `b58add0` |
| 2026-04-18 17:30 | Leo + Vera/Lens/Pixel/Arch/Apex | Claude Code 多 Agent 并发 + superpowers:dispatching-parallel-agents | 七角色团队中的 5 个专项官并发做"赛题对标 + 竞品调研 + PRD 缺口 + UI/UX 评审 + 架构健康度 + QA/答辩风险"分析，各产出一份独立 MD；Leo 汇总出 `00-leo-roadmap.md`：P0 6 项 21h 工时（含 4 改写模式对齐赛题术语、SSE 不卡死、RAG 引用溯源可视化、Markdown 渐进渲染 + 打字光标、Demo 数据铺垫、必交材料闭环）、P1 8 项 30h、P2 7 项明确弃做；最痛发现：前端改写 4 模式是 POLISH/EXPAND/CONDENSE/CUSTOM，赛题硬性要求"数据更新/视角调整/内容扩展/风格转换"——不对齐直接丢 25% 改写分 | `1a65878` |
| 2026-04-18 17:45 | Bolt + Arch | Claude Code | P0-1 + P0-2 冲刺：(1) 前端 `api/report.ts` RewriteMode type + workspace 下拉框 `DATA_UPDATE/ANGLE_SHIFT/EXPAND/STYLE_SHIFT` 与赛题术语对齐，两个 mode 加 ElMessageBox prompt（数据更新指令 / 视角调整指令）；后端 enum/Prompts.java/RewriteServiceImpl 早已对齐无需改；(2) SSE 保命：ReportStreamController 注入 HttpServletResponse 打 `X-Accel-Buffering: no` + `Cache-Control: no-cache, no-transform` + `Connection: keep-alive`，SSE_TIMEOUT_MS 5min→10min；nginx.conf 为 `/api/v1/reports/*/generate\|rewrite` 单独 location 区块：`proxy_http_version 1.1` + `proxy_buffering off` + `proxy_request_buffering off` + `chunked_transfer_encoding on` + `proxy_read_timeout 600s`。mvn clean package + docker compose up -d --build，SSE 响应头端到端验证：backend 直出 + nginx 代理两条链路均正确携带 `X-Accel-Buffering: no` | 本 commit |
| 2026-04-18 22:00 | Leo + Bolt + Pixel | Claude Code (GLM-5.1) + 多Agent并行 | SaaS化冲刺 Phase 1-2：(1) Task-A 晴天MCP后端接入：QingTianMcpClient(JSON-RPC 2.0+streamableHttp+appkey) + SearchMcpService(3工具) + SassMcpService(8+工具) + McpController(/api/v1/mcp/*) + ReportGenerationServiceImpl自动注入MCP数据到传播/政策/行业类报告Prompt；(2) Task-B 版本Diff前端可视化：reports/detail.vue报告详情页+红绿双栏Diff视图+版本时间轴侧栏+router /reports/:id；(3) Task-C 流式打字光标：streaming-editor Markdown渐进渲染+闪烁光标▍+自动滚动；(4) Task-D Demo数据：3知识库+4文档+10chunk+5报告+3版本+模板style/structure补全；(5) Task-F AI生成进度条：后端SSE progress事件5步管道+前端进度条；(6) Task-G MCP前端入口+知识库搜索UI；(7) Task-H Redis RAG缓存(TTL 300s) | `9660f93` |
| 2026-04-19 01:00 | Leo + Bolt + Pixel | Claude Code (GLM-5.1) + 多Agent并行 | SaaS化产品第二轮冲刺：(1) MCP参数格式修正：article-search使用articleInfo嵌套结构(searchMode+searchTime+pageInfo)；sass-mcp使用searchKeywordType=1+mustKeyWord+realTime参数格式；article-detail改为uuid+publishTime必填参数；(2) 报告详情页导出功能：Word(后端POI)+PDF(前端html2pdf)双格式导出；(3) Docker安全加固：backend改用reportai用户连MySQL+MCP appkey环境变量+init.sql GRANT权限；(4) Dashboard动态统计：todayGenerated改为查询当日生成数；(5) 段落级改写按钮：markdown.ts sectionEditable模式+hover段落浮出改写/扩写/精简按钮+后端rewrite-section SSE端点+RewriteServiceImpl段落级改写(expand/condense/rewrite)+自动替换原段落+新版本记录 | `73da59b` |
| 2026-04-19 10:00 | Leo + Bolt + Pixel | Claude Code (GLM-5.1) + Tavily MCP + ECharts | 第三轮MCP生态扩展：(1) TavilyClient: search(高级搜索+AI摘要)+extract(URL内容提取)，application.yml+tavily.api-key配置+docker-compose TAVILY_API_KEY环境变量；(2) FetchClient: Jsoup抓取URL内容转Markdown(去广告/去导航/截断50K)；(3) McpController新增3个API: /web/search(Tavily搜索)+/web/extract(Tavily提取)+/fetch(URL抓取)；(4) 前端workspace: Web搜索弹窗(Tavily)+URL抓取弹窗+三个外部数据按钮(晴天舆情/Web搜索/URL抓取)；(5) EChartsChart.vue通用图表组件+ReportCharts.vue报告详情页舆情图表(渠道分布/情感分布/声量/词云)；(6) echarts npm依赖安装+报告详情页嵌入ReportCharts | `7c5da26` |
| 2026-04-19 19:40 | Leo + Pixel | Claude Code (Opus 4.7) | 赛题 3.4 质量保障 UI 闭环：(1) 审计发现 QualityController/QualityCheckService(覆盖度+引用准确性+事实性三维 LLM-as-judge)后端落地，workspace/index.vue 已接入；但 reports/detail.vue 作为报告主查看入口**完全没有质量检查入口**——用户看老报告时无法触发审查，3.4 分直接丢；(2) detail.vue 新增"质量检查" el-tab-pane：空态引导 + 开始检查按钮 + Loading 态(AI 正在审查…)；(3) 完成后渲染综合分卡(indigo 渐变，0-100)+AI 一句话总评+三维度卡片(覆盖度/引用准确性/事实性，0-1/0-100 双格式兼容)+问题详情(未覆盖要点 list、可疑引用 [n] 含 citedIndex badge、事实性疑点含 mark/fix/soften 建议标签)；(4) vue-tsc + vite build 全绿，后端 mvn compile 无回归 | `b0448e3` |

## 使用的 AI 工具汇总

- **Claude Code CLI（Opus 4.7 / Sonnet 4.6）**：主力 IDE，90% 的编码、文档、决策在此完成；
- **Claude 多模态**：直接读取 PDF 转 PNG 后的图像，抽赛题要求与样例结构；
- **Cherry Studio（macOS）**：挂载 4 个 MCP（qingtian-search / qingtian-sass / context7 / figma）做轮次调试；
- **context7 MCP**：实时拉取 Vue 3 / Spring Boot / MyBatis Plus 文档；
- **晴天自研 MCP**：`search-mcp`（3 工具）+ `sass-mcp`（41 工具），用于传播分析类报告的真实数据接入；
- **Tavily MCP**：Web 搜索（政策原文/行业报告/技术文档）+ URL 内容提取，免费 1000 次/月；
- **ECharts**：前端图表渲染（渠道分布/情感分布/声量/词云），npm 依赖集成；
- **大模型能力**：Claude（Anthropic Messages API，SSE 流式）+ 豆包（Volcengine Ark 2.0.0 Chat Completions），`@ConditionalOnProperty` 运行时切换；
- **外部工具链**：pdftoppm / pdftotext（PDF → 图像/文本）、jq + Python（MCP JSON 解析）、Apache Tika 2.9（文档解析）、Docker Compose + MySQL 8（环境）、Maven 3.9 + JDK 17（构建）、Git + GitHub（版本控制，仓库 https://github.com/xiaoyu-peng/report-ai）。

## 典型对话片段（答辩存档）

1. **"赛题 PDF 抽不到文本"**
   - 问题：`pdftotext` 因 PDF 字段编码异常失败；
   - 方案：改走 `pdftoppm -r 250 -png` 转图像 → Claude 多模态直接 OCR 并理解；
   - 结果：一次性拿到 T5 完整需求 + 评分细则。

2. **"41 个 MCP 工具怎么快速整理"**
   - 问题：sass-mcp `tools/list` 返回 41 个工具，每个都有冗长描述；
   - 方案：Python + `required` 字段排重，按"共用参数 § 3.0"上提，个别工具只列额外必填；
   - 结果：261 行表涵盖 41 工具，代码生成器可 `jq` 直接消费。

3. **"第一站代码如何快速复用"**
   - 问题：第一站包名 `com.skillmcp.hub` 与 SkillMCP 领域词要整体迁到 `com.reportai.hub`；
   - 方案：`sed 's|com\.skillmcp\.hub|com.reportai.hub|g'` 批量改 + grep 白名单只拿 27 个通用类（跳过 Skill/Mcp/ApiKey 等业务类）；
   - 结果：4 分钟完成移植，`mvn compile` 首次即过。

4. **"LLM 客户端应该放在哪个模块"**
   - 问题：Task 5 阶段把 `ClaudeLlmClient` 放进了 `report-hub-report/llm/`，Task 6.1 补豆包时需要决策；
   - 分析：LLM 客户端是"对外部 API 的适配层"，与 JwtUtil/MybatisPlus 同级，属基础设施；`Prompts` 才是业务语义；
   - 方案：整体下沉 `LlmClient/LlmProperties/ClaudeLlmClient/DoubaoLlmClient` 到 `common/llm/`，`Prompts` 和 Service 保留在 report；provider 切换走 `@ConditionalOnProperty`；
   - 结果：未来 knowledge 做文档摘要、system 做日志智能分类都可零成本复用，避免反向依赖 report。
