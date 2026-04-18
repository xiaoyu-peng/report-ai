# ReportAI PRD 对标 × 功能缺口分析

> 作者：Lens（首席产品官） · 日期：2026-04-18 · 里程碑：MVP 全栈烟测通过，距答辩 T-3 天
> 评估范围：赛题 PDF 5 大模块 + PRD v1 接口契约 + 当前 `report-ai/` 代码实现
> 评分权重锚点：检索 25% · 生成 25% · 改写 25% · 版本对比 15% · 完整性 10%

---

## 一、赛题需求 vs 已实现：逐项对照

| # | 赛题 / PRD 条目 | PRD 承诺 | 代码现状 | Gap 说明（证据） |
|---|---|---|---|---|
| 1 | 多格式文档上传（PDF/Word/TXT/MD） | `POST /api/v1/knowledge/bases/{id}/documents` | 全实现 | `DocumentController.upload` + `DocumentServiceImpl` Tika 解析链路通畅 |
| 2 | URL 抓取入库 | `POST /api/v1/knowledge/bases/{id}/url` | 全实现 | `DocumentServiceImpl.importFromUrl` 用 Jsoup+超时（L66-86） |
| 3 | 分块 + MySQL FULLTEXT | `innodb-ft-min-token-size=1` | 全实现 | `RagSearchServiceImpl.searchRaw` + `RagSearchController.search` |
| 4 | RAG 检索返回"片段+出处+相似度" | `GET /api/v1/knowledge/search` 含溯源 | 后端全，前端**未做** | 后端 `RagChunkHit` 含 filename/chunkIndex/score，但前端 `views/workspace`、`views/reports` 无引用角标/高亮渲染（Grep `citation\|引用\|溯源` 仅命中 2 个无关文件） |
| 5 | 报告流式生成（SSE） | `GET /api/v1/reports/{id}/generate` | 全实现 | `ReportStreamController.generate` + `ReportGenerationServiceImpl.streamGenerate` 正常吐 token |
| 6 | 引用溯源角标可点击跳转原文 | PRD §5 验收项③ | **未做** | 后端 Prompt 未要求 Claude 输出 `[1][2]` 角标，生成文本无锚点；前端无 hover 高亮组件 |
| 7 | 章节单独重生成 | `POST /api/v1/reports/{id}/regenerate-section` | **未做** | Grep 全模块 `regenerate-section` / `section` 无任何 Controller 命中 |
| 8 | 字数控制 | PRD §5 §6 未强约束 | **部分** | `Report.wordCount` 只统计不约束，Prompt 无"目标字数"注入 |
| 9 | 模板风格学习（PDF→AI 抽结构） | `POST /api/v1/templates` + `/analyze` | 全实现 | `TemplateController` + `StyleAnalysis` DTO，但 `analyze` 与 `create` 逻辑雷同（L47-52 注释承认），未做"不落库预览" |
| 10 | 4 模式改写 POLISH/EXPAND/CONDENSE/CUSTOM | `POST /api/v1/reports/{id}/rewrite` SSE | 后端全，前端**未做** | `RewriteServiceImpl.streamRewrite` 4 模式齐备写入 `source_mode`，但前端无模式选择面板 / diff 视图 |
| 11 | 修订痕迹视图（新增/删除/修改三色块） | PRD S3、赛题模块四必做 | **几乎未做** | `VersionController.diff` 后端返 `DiffResult`，但前端 Grep `diff\|Diff` 只在 2 个文件提及，无双栏 diff 渲染组件；赛题 25% 改写权重的关键展示缺失 |
| 12 | 版本列表 / 回滚 | `/versions`、`/restore` | 后端全，前端**未做** | 后端 `VersionController` 4 个端点齐全（list/get/diff/restore），前端无"版本树"UI |
| 13 | Word / PDF 导出 | `GET /api/v1/reports/{id}/export` | **未做** | Grep `report-hub-report` 对 `export\|docx\|Docx` 0 命中；PRD §5 S4 承诺的"终版导出"功能缺失 |
| 14 | 晴天 MCP 集成（search / sass streamableHttp） | `docs/晴天自研的MCP.txt` + `mcp-integration.md` | **零实现** | Grep `mcp\|Mcp\|晴天\|api-sc.wengegroup` 全后端只命中一个 `Constants.java` 字面量，无 HTTP client / 无调用点 |
| 15 | 多租户隔离 | PRD §7 明确本次不做，只留列 | 仅预留 | `tenant_id` 列存在于 `database/init.sql`，35 个文件引用但**无一处 WHERE 过滤**，属约定内不做 |
| 16 | 10+ 示例报告 / 3 改写演示物料 | 赛题交付要求 | **未准备** | `docs/以稿写稿-报告样例/` 只有 5 份参考 PDF，无团队自产成品报告 |
| 17 | AI 工具使用日志 | 赛题"无日志不得答辩" | 部分 | `docs/AI工具使用日志.md` 已起草但需持续滚更到答辩当天 |

---

## 二、v1.1 必做（P0，答辩演示前必须落地）

> 目标：把评分权重前 75% 的硬伤补齐，每项都是演示爆点。

| # | 功能 | 价值 | 估工 |
|---|---|---|---|
| P0-1 | **RAG 引用溯源可视化**：Prompt 里注入"用 `[n]` 角标引用"，前端 `workspace` / `reports` 解析角标渲染 hover 卡片（原文+文件名+chunk 号+相似度） | 检索 25% + 生成 25% 双项加分，评审一眼看出"有据可查" | 4h（Prompt 改 + 前端正则渲染组件） |
| P0-2 | **修订痕迹 diff 视图**：前端集成 `diff2html` 或 Element Plus 双栏，对接 `/versions/{from}/diff/{to}`，新增绿/删除红/修改黄三色 | 改写 25% + 版本 15% 关键展示，没有就扣 40 分 | 5h |
| P0-3 | **版本树 UI**：前端 `reports/detail` 加侧栏时间轴，点击节点切换正文，集成 restore 按钮 | 版本 15% 直接得分项 | 3h |
| P0-4 | **晴天 MCP 实时数据接入（至少 1 个 demo）**：后端加 `McpClient` 调 `api-sc.wengegroup.com`，给"传播分析"或"政策影响"类报告注入实时舆情 | 爆点素材 + 区别于其他队"纯 Claude 干聊" | 4h |
| P0-5 | **示例报告素材制备**：跑 5 类 ×2 篇共 10 篇入库，3 篇改写演示录屏 | 赛题硬性交付物（不交不得分） | 3h（跑数据+截图） |

合计 ~19h / 3 天内可完成。

---

## 三、v1.2 加分（P1，有余力再做）

| # | 功能 | 价值 |
|---|---|---|
| P1-1 | Word/PDF 导出（`apache-poi-ooxml` + `openhtmltopdf`，嵌入中文字体） | 完整性 10% 有感知 |
| P1-2 | 章节单段重生成 `/regenerate-section` + 前端"重写本段"按钮 | 生成 25% 的细腻度 |
| P1-3 | 字数目标注入 Prompt（`target_word_count`）+ 前端滑块 | 可控性演示点 |
| P1-4 | `analyze` 真正实现"不落库预览"，前端模板中心加"试分析"按钮 | 产品体验加分 |
| P1-5 | 知识库文档详情页加全文查看 + 关键词高亮（S1 用户故事③） | 检索 25% 细节 |
| P1-6 | 仪表盘接入"今日生成数 / Token 消耗"真实指标 | 完整性印象分 |
| P1-7 | 报告详情页 Markdown 渲染支持 mermaid/表格美化 | 视觉冲击 |
| P1-8 | 改写模式命名对齐 PRD（当前 POLISH/EXPAND/CONDENSE/CUSTOM ↔ PRD 承诺 data_update / angle_shift / expand / style_shift），至少对外文案统一 | 避免评审按 PRD 找不到对应模式 |

---

## 四、放弃（Won't Do）清单 —— 明确砍掉，避免团队误入

| 砍掉项 | 理由 |
|---|---|
| **多租户业务隔离** | PRD §7 已声明本次不做；`tenant_id` 列保留即可。现在补会牵动 35 处查询，ROI 极低，赛场不考。 |
| **向量数据库** | `CLAUDE.md` 明确"未经计划更新不得引入向量库"。MySQL FULLTEXT + `ft-min-token-size=1` 在中文短语上够用；换向量引擎来不及压测。 |
| **细粒度按部门 / 角色授权** | PRD §7 声明"只做登录+全员可写"。赛题评分表无权限项。 |
| **手机 / 移动端适配** | PRD §6 只要求 iPad 最窄宽度；赛场评审都在投影/PC 看，投入产出比最低。 |
| **Redis 缓存层真正接入** | docker-compose 已起 Redis，但 MVP 链路不依赖；上线缓存引入新 bug 面，不如省下来做 P0。 |
| **流式 WebSocket 替代** | `CLAUDE.md` 硬约束"流式只走 SSE"，禁止引入 WS。 |
| **报告重复度 / 事实性后校验** | 赛题模块三有提"重复性分析"，但实现需额外 LLM 调用与规则库，时间不够；用 RAG 角标代偿"可溯源"这一评分锚点即可。 |

---

## 附：最刺眼的一个 Gap（一句话）

**后端已把 RAG chunk 的文件名 + chunkIndex + 相似度算好并返给前端，但生成 Prompt 不要求 Claude 标角标、前端也没有角标 hover 组件 —— 这意味着"检索 25% + 生成 25%"两项评分最直观的加分点（引用溯源可视化）在演示里完全看不到，必须在 P0-1 立刻补上。**
