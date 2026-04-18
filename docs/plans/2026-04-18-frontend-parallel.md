# 前端 Vue 3 并行执行计划（Task 7 + 8）

> **总管：** Leo（主会话）—— 负责拆解、派发、合并、构建验证、commit。
> **并行角色：** 4 个 Agent 并发执行，彼此文件不重叠。

## 上下文

- 后端 8081 端口已可用（fat jar 已打出），所有 `/api/v1/*` 端点就位。
- 第一站前端 **完整存在** 于 `/Users/penghui/工作/Coding/Ai挑战赛/skill-mcp-hub/frontend/`，可直接复制 `src/layouts/`、`src/stores/`、`src/utils/`、`src/views/{login,logs,users,error,permission}`。
- 目标目录 `/Users/penghui/工作/Coding/Ai挑战赛/第二站/report-ai/frontend/` 当前为空。
- 代理端口 3001 → 后端 8081。

## 并行拆解（4 个 Agent）

**拆解原则：** 每个 Agent 管辖互不重叠的文件范围，不共享状态。

### Agent A · Pixel-Scaffold · 脚手架与框架
**职责：** 复制第一站通用资源，搭建 Vue 项目骨架。
**独占文件：**
- `frontend/package.json` / `vite.config.ts` / `tsconfig.json` / `index.html` / `.env.development`
- `frontend/src/main.ts` / `App.vue`
- `frontend/src/layouts/` / `stores/` / `utils/` / `styles/`
- `frontend/src/router/index.ts`
- `frontend/src/api/auth.ts` / `user.ts` / `log.ts`（从第一站复制）
- `frontend/src/views/{login,error,permission}/`（从第一站复制）

**关键约束：**
- `vite.config.ts` 代理 `/api` 指向 `http://localhost:8081`
- 所有 `import` 路径用 `@/*` 别名
- 路由按 Task 7 Step 3 定义（新增 workspace/templates/reports/knowledge，保留 users/logs）
- 登录逻辑、JWT 拦截器不改动

### Agent B · Pixel-Knowledge · 知识库模块
**职责：** 知识库列表 + 文档管理页面。
**独占文件：**
- `frontend/src/api/knowledge.ts`
- `frontend/src/views/knowledge/list.vue`
- `frontend/src/views/knowledge/detail.vue`

**后端契约：**
- `GET  /api/v1/knowledge/bases` · `POST /api/v1/knowledge/bases` · `DELETE /api/v1/knowledge/bases/{id}`
- `GET  /api/v1/knowledge/bases/{kbId}/documents` · `POST …/documents`（multipart）· `DELETE /api/v1/knowledge/documents/{id}`

### Agent C · Pixel-Workspace · 写作工作台（核心页）
**职责：** 报告生成工作台 + SSE 流式接收 + 4 模式改写入口 + 版本对比。
**独占文件：**
- `frontend/src/api/report.ts`
- `frontend/src/views/workspace/index.vue`
- `frontend/src/views/dashboard/index.vue`（仪表盘 stats 卡）

**后端契约：**
- `POST /api/v1/reports`（创建草稿）· `GET /api/v1/reports/{id}/generate`（SSE 流式）
- `POST /api/v1/reports/{id}/rewrite`（4 模式改写 SSE）· `PUT /api/v1/reports/{id}`（保存）
- `GET  /api/v1/dashboard/stats`
- SSE 走 `fetch` + `ReadableStream`，在 header 注入 `Authorization: Bearer <token>`（EventSource 无法带 header）

### Agent D · Pixel-Templates · 模板中心 + 报告库 + Docker 化
**职责：** 模板 CRUD、风格分析上传、报告库列表、前端 Dockerfile + nginx。
**独占文件：**
- `frontend/src/views/templates/index.vue`
- `frontend/src/views/reports/index.vue`
- `frontend/Dockerfile`
- `frontend/nginx.conf`
- `docker-compose.yml`（追加 frontend 服务，**只追加不重写**，需先读一遍再编辑）

**后端契约：**
- `GET /api/v1/templates` · `POST /api/v1/templates` · `DELETE /api/v1/templates/{id}`
- `POST /api/v1/templates/analyze`（multipart file 上传，返回 AI 风格说明）
- `GET /api/v1/reports` · `DELETE /api/v1/reports/{id}`

## 合并与验证（Leo 总管）

1. **冲突检查：** 每个 Agent 独占文件清单已保证无冲突；唯一共用文件是 `docker-compose.yml`，Agent D 独占追加。
2. **构建验证：**
   ```bash
   cd report-ai/frontend && npm install && npm run build
   ```
3. **提交策略：** 4 个 Agent 完成后合为 1 个 commit `feat(task7-8): Vue 3 前端全量实现 + Docker 化`，追加 AI 日志 1 行。
4. **不做的事：** 不起开发服务器做 e2e 测试（赛时无需）；不改动后端；不改第一站任何文件。
