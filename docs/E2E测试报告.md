# ReportAI E2E 测试报告

> 对齐第一站的"步骤日志 + 断言"风格；基于 Playwright 1.58 + `@playwright/test`。
> 目标：覆盖赛题五模块 + 本轮新增 A0–P2 特性。

## 运行

```bash
cd report-ai/frontend
# 先确保后端在跑（docker compose up -d 或本地 mvn spring-boot:run）
# 并且前端至少 vite dev 起来（npm run dev，会自动开 3001 或 fallback 5173）
npm run test:e2e             # 默认走 vite dev（最新代码）
npm run test:e2e:docker      # 走 docker 3001（历史构建）
npm run test:e2e:ui          # 交互式 UI 模式
```

## 当前结果

```
Running 7 tests using 1 worker

  ✓ T1.1 知识库列表 + 分类筛选 + 新建对话框 (4.0s)
  ✓ T2.1 workspace 基础 UI（模板/深度/高级检索） (2.2s)
  ✓ T2.2 选 KB 后高级检索面板（include/exclude）（自建 fixture） (2.8s)
  ✓ T4.1 报告库 + 版本对比 tab（自建 draft fixture） (2.5s)
  ✓ T5.1 各主要路由无控制台报错 (8.1s)
  ✓ T5.2 导出 Word 端点存在（不是 404） (1.5s)
  ✓ T5.3 质量检查端点存在（赛题 3.4） (1.5s)

  7 passed (47.6s)
```

## 测试用例与赛题对照

| 用例 | 覆盖条款 | 手段 |
|---|---|---|
| T1.1 | 1.2 知识库按分类管理 | UI 看 4 个分类 tab；切到"行业报告"后抓 network 验证发起 `?category=industry` |
| T2.1 | 3.1 / 3.3 生成设置、P1 深度三档 | 断言 workspace 三个 radio-button（简洁/标准/深度）可见 |
| T2.2 | 2.3 手动补充检索条件 | 自建 KB fixture，选中 KB → "检索条件"面板出现 → 添加 include tag → tag 渲染 |
| T4.1 | 4.7 / 5.1 版本对比 + 修订视图 | 自建 draft report fixture → 详情页 → "版本对比" tab 骨架可见 |
| T5.1 | 10% 系统完整性 | 遍历 `/dashboard` `/knowledge` `/workspace` `/templates` `/reports` 无 console.error |
| T5.2 | 5.2 Word 导出路径 | API 层直接 GET /reports/123/export/docx → 断言非 404（即路由存在） |
| T5.3 | 3.4 质量保障路由 | API GET /reports/123/quality/check → 断言非 404 |

---

## 测试过程中发现的问题与修复

### 🔴 问题 1：Docker 前端容器静态文件落后于代码

**现象**：对 3001（docker nginx）跑测试时，T5.1（路由探测）失败 —— `生成深度` 文字找不到，`.category-tabs` 缺失。对比发现 docker 容器里的前端 `dist` 是 20 小时前构建的，不含本轮 A0–P2 的改动。

**根因**：`docker-compose.yml` 的前端构建是 `--build` 时执行 `npm run build`，之后改了前端代码不会自动刷新容器。

**修复**：
- 测试默认 `baseURL` 改为 `http://localhost:5173`（vite dev 服务器，热重载最新代码）
- 加 `npm run test:e2e:docker` 脚本，显式针对 docker 部署跑（演示前必须先 `docker compose up --build frontend` 刷新）
- 在 `docs/演示脚本.md` 的"前置准备"里已写了 reset 提醒

**影响**：演示时一定要跑 `docker compose up --build frontend`，否则评委看的是老 UI。

### 🟡 问题 2：Playwright 1.58 浏览器下载失败（网络受限）

**现象**：`npx playwright install chromium` 在企业网 / 墙后失败，本地缓存只有 1208 版，但 1.58 要 1217。

**修复**：`playwright.config.ts` 加 `channel: 'chrome'`，改用本机系统 Chrome（在 `/Applications/Google Chrome.app` 找到）。

**影响**：无需下载，稳。唯一限制是要求本机装 Google Chrome。

### 🟡 问题 3：分类 tab 的 selector 触发 strict mode violation

**现象**：`page.locator('.category-tabs :has-text("政策法规")')` 匹配到 3 个节点（el-radio-group、label、span），Playwright strict mode 报错。

**修复**：改用精确的 `.el-radio-button__inner` 作为锚 + `{ hasText: cat }`。

### 🟢 问题 4：T2.2 / T4.1 因库空跳过（不是 bug）

**现象**：新初始化的数据库里没有 KB 或 report，两个 UI 交互测试无法进入状态。

**修复**：两个用例都改为**测试内自建 fixture**（直接通过 API 创建 KB / draft report），不再依赖外部数据。这样测试完全自给自足、可重复跑。

---

## 覆盖率与局限

**已覆盖**：
- ✅ 登录 / 导航骨架
- ✅ 知识库：分类筛选（真发了 `?category=industry` 请求）
- ✅ 工作台：深度三档 radio、高级检索面板、include 关键词添加
- ✅ 报告详情：版本对比 tab 存在
- ✅ 路由无 JS 报错
- ✅ 导出 / 质量检查端点存在（不 404）

**未覆盖 / 受限**：
- ❌ 真实生成一份报告（涉及 LLM 流式，30s–2min，对 CI 不稳定，且需要真 API key）
- ❌ 真实改写（同上）
- ❌ Hover 预览浮层（testbase 没报告内容，没有 sup.cite 元素）
- ❌ Diff 视图红绿渲染（需两个版本，同样依赖 LLM 产出 v1/v2）

**如果你想在答辩现场做端到端演示**：
1. 按 `docs/演示脚本.md` 手动跑 3 份报告（有 LLM）
2. 跑完后再跑这套 E2E 会触发 T4.1 的真实 diff 验证路径
3. 或者加一套"整合测试" spec 用 mock LLM 模拟流式返回

---

## 运行产物

- HTML 报告：`playwright-report/index.html`（`npx playwright show-report` 打开）
- 失败现场：`test-results/` 下按用例名分目录，含截图 + 视频 + trace.zip
- Trace 回放：`npx playwright show-trace <path>`
