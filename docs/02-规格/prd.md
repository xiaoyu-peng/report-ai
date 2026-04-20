# ReportAI PRD（精简版）

> 产品官-Lens 产出。目的：给后端 Task 3-6、前端 Task 7 一份统一接口契约，免得各自约定不一致。
> 完整度只追求"够后续 AI 上手"，不追求详尽市场分析。

## 1. 产品定位

- 一句话：**"传资料、选模板、一键出稿"** 的企业级报告生成工具。
- 目标用户：政府机构、咨询公司、投研团队、公关市场部——需要高频输出标准化分析报告的组织。
- 差异点：**知识库驱动 + 以稿写稿**双引擎（竞品普遍只做前者）。

## 2. 核心用户故事（按评分权重排序）

### S1（知识检索 25%）
- 作为使用者，**我把公司历史报告批量上传成知识库**，系统能识别 PDF / Word / TXT / Markdown 并自动分块入库；
- 我输入一个问题，系统返回相关片段，**每段必须标出原文件 + 段落位置 + 相似度**；
- 我能在知识库页面点进某份文档查看全文，并高亮关键词。

### S2（报告生成 25%）
- 我选一个"报告类型"（5 种内置：政策 / 行业 / 传播 / 科技 / 专题），输入主题和关键词；
- 系统**流式**（SSE）产出一篇结构化报告，章节层级正确，**每段引用都带溯源角标**；
- 生成完成后我能逐段修改大纲、单独重生成某一节。

### S3（以稿写稿 / 改写 25%）
- 我上传一份参考报告，选一种改写模式：
  - **数据更新**：保留结构，把旧数据换成新数据；
  - **视角调整**：同内容换受众（例：行业报告 → 领导简报）；
  - **内容扩展**：基于原稿框架补新章节 / 新案例；
  - **风格转换**：正式 ↔ 通俗，中文 ↔ 英文。
- 改写后进入**修订痕迹视图**，左右对照 + diff 高亮，能一键接受 / 拒绝。

### S4（对比展示与版本管理 15%）
- 每次生成 / 改写都存一个版本，侧边有版本树；
- 任两个版本可双栏 diff；
- 终版可导出 Word / PDF。

### S5（系统完整性 10%）
- 登录 / 登出 / 用户管理 / 操作日志齐全（复用第一站）；
- Docker 一键启动，落地 http://localhost:3001 能直接用。

## 3. 信息架构（前端页面）

```
登录页
 └─ 管理后台（AdminLayout，左侧菜单）
     ├─ 仪表盘                (/dashboard)
     ├─ 知识库
     │   ├─ 列表              (/knowledge)
     │   └─ 详情（文档管理）   (/knowledge/:id)
     ├─ 写作工作台 ★核心       (/workspace)
     ├─ 模板中心              (/templates)
     ├─ 报告库                (/reports)
     ├─ 用户管理              (/users)      ← 复用第一站
     ├─ 部门管理              (/departments) ← 复用第一站
     └─ 操作日志              (/logs)        ← 复用第一站
```

## 4. 接口契约（v1）

所有接口走 `/api/v1`，成功返回统一 `Result<T>`（code/msg/data），分页返回 `PageResult<T>`（list/total/page/size）。登录以外的接口都要带 `Authorization: Bearer <jwt>`。

### 4.1 认证与仪表盘

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| POST | `/api/v1/auth/login` | 登录返回 JWT |
| POST | `/api/v1/auth/logout` | 登出 |
| GET  | `/api/v1/auth/me` | 当前用户信息 |
| GET  | `/api/v1/dashboard/stats` | 知识库数/文档数/报告数/今日生成数 |

### 4.2 知识库模块

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET    | `/api/v1/knowledge/bases` | 知识库列表（分页）|
| POST   | `/api/v1/knowledge/bases` | 创建知识库 |
| PUT    | `/api/v1/knowledge/bases/{id}` | 更新 |
| DELETE | `/api/v1/knowledge/bases/{id}` | 删除（软）|
| GET    | `/api/v1/knowledge/bases/{id}/documents` | 文档列表 |
| POST   | `/api/v1/knowledge/bases/{id}/documents` | **上传文档（multipart）**，立即返 docId，异步解析 |
| POST   | `/api/v1/knowledge/bases/{id}/url` | 通过 URL 抓取并入库 |
| DELETE | `/api/v1/knowledge/documents/{id}` | 删文档（软）|
| GET    | `/api/v1/knowledge/documents/{id}` | 文档详情（含全文）|
| GET    | `/api/v1/knowledge/search?kbId=x&q=...&topK=10` | **RAG 检索**，返回片段 + 溯源 + 相似度 |

### 4.3 模板中心

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET  | `/api/v1/templates` | 模板列表（5 个内置 + 用户自建）|
| POST | `/api/v1/templates` | 新建模板（上传 PDF → Tika → Claude 风格分析）|
| POST | `/api/v1/templates/analyze` | 单独触发"风格分析"（返回 JSON：结构 / 语气 / 引用模式）|
| GET  | `/api/v1/templates/{id}` | 模板详情 |
| PUT  | `/api/v1/templates/{id}` | 更新 |
| DELETE | `/api/v1/templates/{id}` | 删除 |

### 4.4 报告生成（含改写）

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET  | `/api/v1/reports` | 报告列表（分页 + 按类型 / 状态过滤）|
| POST | `/api/v1/reports` | **创建草稿**：body={title, topic, kbId, templateId, keyPoints[]}，返回 id |
| GET  | `/api/v1/reports/{id}` | 报告详情（含当前版本 + 版本列表）|
| PUT  | `/api/v1/reports/{id}` | 保存编辑后的正文 |
| DELETE | `/api/v1/reports/{id}` | 删除 |
| **GET** | **`/api/v1/reports/{id}/generate`** | **SSE 流式生成**：实时推送每段 token |
| POST | `/api/v1/reports/{id}/regenerate-section` | 重生成指定章节（body={sectionId}）|
| POST | `/api/v1/reports/{id}/rewrite` | **改写**：body={mode: data_update\|angle_shift\|expand\|style_shift, refReportId} |
| GET  | `/api/v1/reports/{id}/versions` | 版本列表 |
| GET  | `/api/v1/reports/{id}/versions/{vA}/diff/{vB}` | 两版本 diff（返回 JSON patch）|
| POST | `/api/v1/reports/{id}/versions/{v}/restore` | 回滚到指定版本 |
| GET  | `/api/v1/reports/{id}/export?format=docx\|pdf` | 导出 |

### 4.5 系统管理（复用第一站）

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| GET/POST/PUT/DELETE | `/api/v1/users` | 用户 CRUD |
| GET/POST/PUT/DELETE | `/api/v1/roles` | 角色 CRUD |
| GET/POST/PUT/DELETE | `/api/v1/departments` | 部门 CRUD |
| GET | `/api/v1/logs` | 操作日志列表 |

## 5. 验收点（给 Apex 做自检清单）

| 维度 | 验收项 | 权重 |
| --- | --- | --- |
| 知识检索 | ① 批量上传 PDF/Word/TXT/MD 均成功；② RAG 结果带溯源 + 相似度；③ URL 抓取能入库 | 25% |
| 报告生成 | ① 5 种类型各生成 ≥ 2 篇，合计 ≥ 10 篇；② 流式可见；③ 引用角标可点击跳转到原文；④ 大纲可单段重生成 | 25% |
| 改写 | ① 4 种模式各演示 ≥ 1 次；② diff 视图能显示新增/删除/修改 3 种色块 | 25% |
| 对比 / 版本 | ① 版本树可切换；② 任两版本可 diff；③ 回滚生效 | 15% |
| 完整性 | ① docker compose up 一键起；② 无登录不能访问业务接口；③ 操作日志完整 | 10% |

## 6. 非功能要求

- SSE 单报告生成 P95 ≤ 30 s；
- 10 个并发生成不崩；
- MySQL FULLTEXT 支持单字中文（`innodb-ft-min-token-size=1`，Task 1 已设）；
- 前端移动端兼容到 iPad 最窄宽度即可（不做手机屏）；
- 所有文案中文，导出 PDF 字体嵌入。

## 7. 范围外（本次不做）

- 多租户隔离（schema 预留 tenant_id，暂不实现业务分离）；
- 细粒度按部门授权（只做登录 + 全员可写）；
- 向量检索（仅作 Tier 2 可选优化，见 `docs/additional-mcps.md`）；
- 手机原生 App。
