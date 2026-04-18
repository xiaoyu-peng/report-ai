# ReportAI VibeCoding SaaS 化产品冲刺计划

> **制定人：** Leo（大总管）· 日期：2026-04-18
> **基线状态：** 后端 5 模块全通 + 前端 Vue 3 全量实现 + Docker 一键起 + SSE 流式生成 + 4 模式改写已对齐 + 引用溯源面板已实现 + Word/PDF 导出已实现
> **目标：** 在竞赛评分 5 维度上全面拿分，打造"企业私域知识库 × MCP 实时数据 × 多模式可视化改写"三合一 SaaS 产品

---

## 一、现状盘点（已完成 vs 待完成）

### ✅ 已完成

| 模块 | 能力 | 代码证据 |
|---|---|---|
| 基础设施 | Docker Compose 4 件套（MySQL/Redis/Backend/Frontend） | `docker-compose.yml` |
| 认证体系 | JWT 登录/登出/用户管理/角色/部门/操作日志 | `report-hub-system/` |
| 知识库 | KB CRUD + 文档上传（Tika 解析）+ URL 抓取 + FULLTEXT RAG + 分块 | `report-hub-knowledge/` |
| 报告生成 | 5 类模板 + SSE 流式 + Claude/豆包双 provider | `report-hub-report/` |
| 改写 | 4 模式（DATA_UPDATE/ANGLE_SHIFT/EXPAND/STYLE_SHIFT）+ 续写 | `RewriteServiceImpl` |
| 版本管理 | 版本列表 + LCS diff + 回滚 | `VersionController` |
| 前端 | Vue 3 + Element Plus + SSE 流式接收 + 引用溯源面板 + 编辑/预览切换 | `workspace/index.vue` |
| 导出 | Word（后端 POI）+ PDF（前端 html2pdf） | `ReportExportController` |
| AI 团队 | 7 角色分工 + 5 份分析文档 + 竞品调研 | `docs/analysis/` |

### ⚠️ 待完成（按评分权重排序）

| # | 缺口 | 影响评分 | 优先级 |
|---|---|---|---|
| G1 | **晴天 MCP 实际接入后端**——当前零实现，只有文档 | 差异化核心 + 传播分析报告数据源 | **P0** |
| G2 | **版本 diff 前端可视化**——后端 diff API 已有，前端无红绿双栏 | 改写 25% + 版本 15% | **P0** |
| G3 | **流式打字光标 + Markdown 渐进渲染**——当前 textarea 纯文本 | AI 感 + 演示第一印象 | **P0** |
| G4 | **Demo 数据铺垫**——10 篇报告 + 3 篇改写演示 | 赛题硬性交付 | **P0** |
| G5 | **报告详情页 + 版本树 UI**——当前报告库只有列表 | 版本 15% | **P1** |
| G6 | **知识库搜索 UI**——RAG 检索结果前端展示 | 检索 25% | **P1** |
| G7 | **段落级改写按钮**——hover 浮出改写/扩写/精简 | 改写 25% 细腻度 | **P1** |
| G8 | **AI 生成进度可视化**——5 步管道进度条 | AI 感 + 演示气质 | **P1** |
| G9 | **必交材料闭环**——演示视频/部署文档/PPT | 赛题硬性要求 | **P0** |

---

## 二、竞品差异化亮点（答辩弹药）

基于 Vera 竞品调研 + 互联网最新信息，ReportAI 的 **5 大差异化壁垒**：

| # | 亮点 | 竞品对比 | 评分映射 |
|---|---|---|---|
| 1 | **领域知识 × MCP 实时数据双引擎** | 豆包/秘塔只接公网，Notion 无中文舆情 MCP | 检索 25% + 生成 25% |
| 2 | **"以稿写稿"四模式可视化差异** | 秘塔单篇改写，Jasper 只管口气，无 diff 视图 | 改写 25% + 版本 15% |
| 3 | **5 种中文专业报告模板体系** | 竞品无政策/行业/传播/科技/日报体系 | 生成 25% |
| 4 | **引用原文级溯源（chunk 级角标）** | 豆包只给网页链接，无法定位原文段落 | 检索 25% |
| 5 | **MCP 协议原生集成** | 国内首个将舆情 MCP 与报告生成深度整合的产品 | 系统完整性 10% |

---

## 三、任务分解（多 Agent 并行）

### Phase 1 · P0 必做（Day 1，预估 16h）

#### Task-A · Bolt · 晴天 MCP 后端接入 ⏱️ 6h

**目标：** 后端实际调用晴天 search-mcp + sass-mcp，为传播分析/政策影响类报告注入实时数据

**实现路径：**
1. 新建 `report-hub-knowledge/src/main/java/com/reportai/hub/knowledge/mcp/` 包
2. `QingTianMcpClient.java`——封装 JSON-RPC 2.0 + streamableHttp + appkey Header
3. `SearchMcpService.java`——封装 `article-search` / `article-detail` / `media-account-search`
4. `SassMcpService.java`——封装 41 个舆情指标工具（按需调用，先实现 `overview` / `hot-article` / `emotional-distribution` / `datasourceSound` / `stage-envolution` / `generate-event-topic-info` 6 个核心工具）
5. `McpKnowledgeController.java`——新增 API：
   - `POST /api/v1/mcp/search-articles` → 调 `article-search`，结果可选入库
   - `POST /api/v1/mcp/analysis/overview` → 调 `overview`，返回渠道+情感+关键词
   - `POST /api/v1/mcp/analysis/{tool}` → 通用调用入口
6. `ReportGenerationServiceImpl` 改造：生成传播分析类报告时，并行调 MCP 拿实时数据注入 Prompt
7. `application.yml` 新增 `qingtian.mcp.*` 配置

**验收：** curl 调 `/mcp/search-articles` 返回真实舆情文章；生成传播分析报告时 Prompt 含 MCP 数据

#### Task-B · Pixel · 版本 Diff 前端可视化 + 报告详情页 ⏱️ 5h

**目标：** 改写前后差异一目了然（红绿双栏 diff），版本树可切换

**实现路径：**
1. `npm install diff2html` 或自实现简单 diff 渲染
2. 新建 `views/reports/detail.vue`——报告详情页：
   - 左侧：Markdown 预览区（复用 `renderReportMarkdown`）
   - 右侧：版本时间轴（竖排节点，点击切换正文）
   - 底部：Diff 视图入口（选 v1 vs v2 → 双栏对比）
3. Diff 视图组件：
   - 左栏（旧版）红色删除行 / 右栏（新版）绿色新增行 / 黄色修改行
   - 对接 `GET /v1/reports/versions/diff?from=X&to=Y`
4. 路由新增 `/reports/:id` → `reports/detail.vue`
5. `reports/index.vue` 的"查看"按钮改为跳转详情页

**验收：** 改写后打开报告详情 → 选两个版本 → 看到红绿 diff

#### Task-C · Pixel · 流式打字光标 + Markdown 渐进渲染 ⏱️ 3h

**目标：** SSE 流式生成时，用户看到"AI 打字机"效果，Markdown 实时渲染

**实现路径：**
1. `workspace/index.vue` 的编辑模式改造：
   - `el-input textarea` → `<div class="streaming-editor">` + v-html 渐进插入
   - SSE 每到新 token：追加到 buffer → markdown-it 解析 → 追加到 DOM
   - 尾部插入 `<span class="cursor blink">▍</span>` 闪烁光标
   - 完成后撤掉光标，自动切到预览态
2. CSS 动画：`@keyframes blink { 50% { opacity: 0 } }`
3. 保留 textarea 编辑模式作为"手动编辑"入口（预览态点"编辑"切回）

**验收：** 点"开始生成"→ 看到 ▍ 闪烁 + 标题/粗体实时渲染

#### Task-D · Bolt + Lens · Demo 数据铺垫 ⏱️ 2h

**目标：** 登录后首页 stats 非零，4 页都有东西看

**实现路径：**
1. `init.sql` 补 seed 数据：
   - 3 个知识库（政策影响/行业分析/传播分析）
   - 每库 2-3 份文档（用样例报告内容填充 chunks）
   - 5 个内置模板的 `style_description` + `structure_json` 补齐
   - 10 篇示范报告（5 类 × 2 篇，status=completed）
   - 3 篇改写版本记录
2. 前端 Dashboard 展示最近报告列表

**验收：** `docker compose up -d --build` 后登录 → stats 全非零 → 报告库有 10 篇

### Phase 2 · P1 加分（Day 2，预估 12h）

#### Task-E · Pixel · 段落级改写按钮 ⏱️ 4h

**目标：** Markdown 预览时 hover 段落浮出"改写/扩写/精简"按钮

**实现路径：**
1. `renderReportMarkdown` 改造：给每个 `<h2>/<h3>` 段落包 `<div class="section-block" data-section-id="N">`
2. CSS hover 浮出按钮组
3. 点击按钮 → 调 `POST /v1/reports/{id}/regenerate-section`（后端需新增）
4. SSE 流式替换该段落内容

#### Task-F · Pixel · AI 生成进度可视化 ⏱️ 3h

**目标：** 生成过程显示 5 步管道进度条

**实现路径：**
1. 后端 SSE 新增 `event: progress` 事件：`{step: "检索知识库", stepIndex: 1, totalSteps: 5}`
2. 前端 workspace 顶部显示进度条：`检索知识库 → 分析风格 → 撰写大纲 → 逐段生成 → 完稿`
3. 当前步骤高亮 + 动画

#### Task-G · Bolt · 知识库搜索 UI + MCP 前端入口 ⏱️ 3h

**目标：** 前端可搜索知识库 + workspace 支持"引入晴天 MCP 数据"

**实现路径：**
1. `knowledge/detail.vue` 新增搜索框 + 结果列表（含 chunk 内容 + 相似度）
2. `workspace/index.vue` 新增"引入外部数据"按钮 → 弹窗输入关键词 → 调 MCP search → 结果可选入库
3. 生成时自动合并 MCP 数据到 RAG context

#### Task-H · Bolt · Redis RAG 缓存 + Docker 安全加固 ⏱️ 2h

**目标：** 让 Redis 真正用起来 + 修 Arch 扣分点

**实现路径：**
1. `RagSearchServiceImpl` 加 `@Cacheable(key="'rag:' + #kbId + ':' + T(java.security.MessageDigest).getInstance('MD5').digest(#query.bytes)")`，TTL 300s
2. `docker-compose.yml` backend 环境变量 `SPRING_DATASOURCE_USERNAME` 改 `reportai`

### Phase 3 · 收尾（Day 3，预估 8h）

#### Task-I · Leo · 必交材料闭环 ⏱️ 4h

1. 演示视频脚本 + 3 分钟录屏（Golden Path 全程）
2. 部署文档 README.md 更新（1 分钟 docker compose 拉起）
3. AI 工具使用日志追加最后几行
4. PPT（5-8 页：痛点/差异化/演示/架构/AI 工具链/评分映射）

#### Task-J · Apex · 赛题自检 + 彩排 ⏱️ 4h

1. 按 Apex 25 条测试用例逐条验证
2. 按 6 维度打分卡评估 10 篇 Demo 报告质量
3. 整体彩排 3 遍
4. 最终压缩包打包

---

## 四、多 Agent 调度方案

```
Leo（总管，主会话）
  ├─→ Bolt（开发官）── Task-A / Task-D / Task-G / Task-H
  ├─→ Pixel（设计官）── Task-B / Task-C / Task-E / Task-F
  ├─→ Lens（产品官）── Task-D 内容 + 答辩话术
  ├─→ Arch（架构官）── MCP 接入架构审查 + Redis 缓存方案
  └─→ Apex（质检官）── Task-J 全链路验证
```

**并行策略：**
- Day 1：Bolt 做 Task-A（MCP 接入）的同时，Pixel 做 Task-B（diff 视图）+ Task-C（打字光标）
- Day 2：Bolt 做 Task-G（搜索 UI）的同时，Pixel 做 Task-E（段落改写）+ Task-F（进度条）
- Day 3：Leo + Apex 收尾

---

## 五、晴天 MCP 接入详细方案

### 5.1 后端架构

```
report-hub-knowledge/
└── src/main/java/com/reportai/hub/knowledge/mcp/
    ├── QingTianMcpClient.java        # JSON-RPC 2.0 + streamableHttp + appkey
    ├── SearchMcpService.java         # article-search / detail / account
    ├── SassMcpService.java           # 6+ 核心舆情指标
    └── dto/                          # 入参出参 POJO
```

### 5.2 报告生成时的 MCP 编排

| 报告类型 | MCP 工具调用链 | 注入 Prompt 位置 |
|---|---|---|
| 传播分析 | `overview` → `stage-envolution` → `hot-article` → `emotional-distribution` → `datasourceSound` → `generate-event-topic-info` | 事件回顾/声量渠道/关键节点/情感/媒体/概述 |
| 政策影响 | `article-search`(政策关键词) → `content-classification` → `hot-theme` | 热点梳理/影响分析/关注点 |
| 行业分析 | `article-search`(行业关键词) → `hot-words` → `hot-org` | 行业趋势/竞争格局/热门机构 |
| 科技情报 | `article-search`(技术关键词) → `hot-person` → `influence-indexation` | 技术进展/代表人物/影响力 |
| 专题日报 | `article-search`(主题关键词, 最近7天) → `hot-article`(Top5) | 今日要闻/重点/数据快讯 |

### 5.3 前端入口

1. **Workspace "引入外部数据"按钮**：弹窗 → 输入关键词 → 调 MCP → 结果列表 → 勾选入库
2. **传播分析报告自动注入**：选"传播分析"模板时，自动触发 MCP 数据拉取，生成前显示"正在获取舆情数据..."
3. **知识库详情页"订阅主题"**：设置关键词+时间窗 → 定时调 MCP 拉取 → 自动入库

---

## 六、答辩演示脚本（Golden Path 5 分钟）

| 时间 | 动作 | 亮点 |
|---|---|---|
| 0:00-0:30 | 登录 → Dashboard 展示 stats | "已有 10 篇报告、3 个知识库" |
| 0:30-1:30 | 知识库 → 上传 PDF → 搜索 | "Tika 解析 + FULLTEXT 中文检索 + chunk 级溯源" |
| 1:30-2:30 | Workspace → 选知识库+模板 → 开始生成 | "SSE 流式 + 打字光标 + RAG 引用面板实时高亮" |
| 2:30-3:30 | 改写 → 数据更新 + 视角调整 | "四模式有区分度 + 修订痕迹红绿 diff" |
| 3:30-4:15 | 传播分析报告 → MCP 实时数据注入 | "晴天 MCP 44 工具实时拉取舆情 → 报告含真实数据" |
| 4:15-5:00 | 版本对比 → 导出 Word | "版本树 + diff + 一键导出" |

---

## 七、风险与兜底

| 风险 | 兜底方案 |
|---|---|
| LLM API 挂了 | 预生成 10 篇报告 + 屏幕录制备份；豆包 endpoint 国内可达 |
| MCP 超时 | MCP 是增强而非必需，超时回退纯本地 RAG |
| SSE 被切断 | nginx 已配 `proxy_buffering off` + `proxy_read_timeout 600s` |
| Docker 起不来 | 提供本地开发启动方案（mvn + npm run dev） |

---

## 八、执行顺序

1. **立刻启动 Task-A（MCP 接入）+ Task-B（diff 视图）+ Task-C（打字光标）三路并行**
2. Task-A 完成后接 Task-D（Demo 数据）
3. Day 2 启动 Task-E/F/G/H
4. Day 3 收尾 Task-I/J
