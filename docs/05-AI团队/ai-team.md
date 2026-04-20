# ReportAI 虚拟 AI 团队（7 角色分工）

> 把整个 VibeCoding 过程按岗位拆成 7 个专项角色。每条任务先"认角色再动手"——在 Cherry Studio / Claude Code 里以对应 persona（系统提示词）启动会话，避免"什么都写却什么都不深"。

## 角色清单

| 角色 | 职责定位（来自赛事团队共识） |
| --- | --- |
| **大总管-Leo** | 超级 AI 智能体总调度官，统筹协调专项智能体，任务到这里拆解分发，结果在这里汇总交付 |
| **调研官-Vera** | AI 首席调研官，负责市场调研、用户需求挖掘、竞品分析，将模糊想法转化为有数据支撑的需求 |
| **产品官-Lens** | AI 首席产品官，负责 PRD 撰写、需求拆解、功能优先级规划，将调研洞察转化为清晰的产品方案和 Skill 需求文档 |
| **设计官-Pixel** | AI 首席设计官，负责 UI 交互设计、AI 交互范式、设计规范建设，让产品好看又好用，好设计不需要解释 |
| **架构官-Arch** | AI 首席架构官，负责系统架构设计、API 规范、技术选型决策 |
| **开发官-Bolt** | AI 首席开发官，负责全栈代码实现、服务部署、AI 特性开发，需求到这里下一步就是能跑的代码 |
| **质检官-Apex** | AI 首席质检官，负责测试用例设计、Bug 根因分析、AI 输出质量评估，没过这关谁说上线都不行 |

## 映射到本项目的任务与产物

| 阶段 | 主角色 | 协作 | 已产出 / 待产出 |
| --- | --- | --- | --- |
| 赛题分析 | 调研官-Vera | Leo | ✅ `docs/requirements.md` — 5 模块 + 评分标准梳理 |
| 样例研究 | 调研官-Vera | Lens | ✅ `docs/report-style-guide.md` — 5 类报告结构与风格 |
| MCP 能力探测 | 架构官-Arch | Vera | ✅ `docs/mcp-integration.md` + `docs/mcp-schemas/*.json` |
| 第三方 MCP 选型 | 架构官-Arch | Leo | ✅ `docs/additional-mcps.md` — Tier 1/2/3 推荐 |
| 实施计划 | 架构官-Arch | Lens, Bolt | ✅ `docs/plans/2026-04-15-report-ai.md` — 8 Task 分解 |
| **PRD（接口契约）** | **产品官-Lens** | Arch | 🔨 待建：`docs/prd.md`（含 30+ 接口列表 / 5 模块用户故事 / 验收点）|
| **UI / 交互稿** | **设计官-Pixel** | Lens | 🔨 待建：`docs/design/` 下放 workspace 写作台 / 修订痕迹视图草图 |
| Docker + DB + 脚手架 | 开发官-Bolt | Arch | ✅ Task 1 — `report-ai/docker-compose.yml` + `init.sql` |
| 后端 common | 开发官-Bolt | Arch | ✅ Task 2 — 移植 27 类 + BUILD SUCCESS |
| 后端 system / knowledge / report | 开发官-Bolt | Arch | 🔨 Task 3-5 |
| 前端 Vue | 开发官-Bolt | Pixel | 🔨 Task 7 |
| **单测 / E2E 冒烟** | **质检官-Apex** | Bolt | 🔨 Task 8 之后补一份 `docs/qa/test-plan.md` + Playwright 脚本 |
| **赛题自检 / 评分模拟** | **质检官-Apex** | Leo | 🔨 最终产出：按评分 5 维度（25/25/25/15/10）自评，附证据截图 |
| 交卷打包 | 大总管-Leo | 全员 | 🔨 `build_delivery.sh` + `docs/AI工具使用日志.md`（赛题强制要求）|

## 用法建议（Cherry Studio / Claude Code）

### 1. 每次开新会话前先 pin 一个角色
在 Cherry Studio 里给每个角色建一个 **"智能体"**（Agents 标签）：
- 名称：用上面表格里的中文名；
- 系统提示词：直接粘贴上面表格"职责定位"那栏，并追加本项目 CLAUDE.md 前两段（仓库状态 + 参考资料）作为背景。

在 Claude Code 里可以用 `Agent` 工具 + 对应 prompt 实现同样效果，但**更推荐为每个角色建一个自定义 subagent**（`.claude/agents/<name>.md`），然后用 `Agent(subagent_type="bolt")` 之类调用。

### 2. 典型任务流水线

```
用户需求描述
   ↓
Leo（拆解）
   ├→ Vera（是否需要先调研？）
   ├→ Lens（PRD 切割？）
   ├→ Pixel（UI 稿？）
   ├→ Arch（架构 / 技术选型？）
   ├→ Bolt（撸代码）
   └→ Apex（跑测试 / 验收）
   ↓
Leo（汇总 + commit + 更新 README 进度）
```

### 3. 角色之间传递上下文

每个专项角色完成自己那部分后，**必须**往指定 MD 文件里追加或更新段落，避免信息只在对话里流转后丢失：

- Vera → `docs/requirements.md`、`docs/report-style-guide.md`
- Lens → `docs/prd.md`
- Pixel → `docs/design/*.md` 或 `docs/design/*.png`（Figma 导出）
- Arch → `docs/plans/*.md`、`docs/mcp-integration.md`、`docs/adr/*.md`（可选）
- Bolt → 代码 + commit message
- Apex → `docs/qa/test-plan.md`、`docs/qa/self-evaluation.md`
- Leo → `README.md` 的"开发进度"清单 + `docs/AI工具使用日志.md`

## 当下要优先补的 3 份产物（评分强相关）

1. **`docs/prd.md`**（Lens）— 30+ 接口清单 + 用户故事 + 验收点。**赛题评分"报告生成质量 25% / 改写质量 25%"直接考用户故事覆盖度。**
2. **`docs/design/workspace.md` 或 PNG**（Pixel）— 至少画出"写作工作台"与"修订痕迹视图"两张核心屏。**评分"对比展示与版本管理 15%"直接看这里。**
3. **`docs/AI工具使用日志.md`**（Leo）— 从现在起每次 commit 都要追加一行"XX 角色用 XX 工具解决了 XX"，**赛题硬性要求，没有不得答辩**。

剩余 Task 3-8 全部归 Bolt 主办，Arch / Apex 在节点上复核。
