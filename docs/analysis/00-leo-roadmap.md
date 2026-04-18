# Leo 汇总：5 角色调研 → 赛前冲刺路线图

> **汇总人：** Leo（大总管）
> **输入：** Vera / Lens / Pixel / Arch / Apex 5 份独立分析（详见同目录）
> **产出：** 按"P0 必做 / P1 加分 / P2 弃做"三档合成的最终行动清单
> **时限：** 2026-04-21 答辩日前

---

## 一、最痛的 5 个发现（每角色 1 句话）

| 角色 | 最痛发现 | 损失 |
|---|---|---|
| **Vera** | 市面无"私域 KB + 实时 MCP + 多模式可视化"三合一，赛题 75% 核心分全覆盖 | 差异化故事没讲就丢 |
| **Lens** | 后端 RAG 已返 chunk 元数据（filename/chunkIndex/score），前端 0 渲染 `[n]` 角标/溯源面板 | 覆盖 50% 分值（检索 25% + 生成 25%）的最低成本加分点 |
| **Pixel** | `workspace/index.vue:174-183` 用 `el-input textarea` 承接 SSE，纯后台感，无 AI 气质 | "生成感"没做 → 演示开场 30 秒掉分 |
| **Arch** | SSE 被 nginx 默认 `proxy_buffering on` 整段缓冲 + SseEmitter 5min 超时 | 演示当场可能卡死，核心卖点归零 |
| **Apex** | 前端改写 4 模式是 POLISH/EXPAND/CONDENSE/CUSTOM；赛题要求 **数据更新/视角调整/内容扩展/风格转换** | 直接丢 25% 改写分 + 审题不清印象分 |

**Leo 判断：** Apex 这条是"不修就必输"，Arch 是"不修就可能输"，Lens+Pixel 是"修了能多赢"，Vera 是"话术弹药"。

---

## 二、P0 必做（答辩前 72 小时内）

按性价比（分值影响 ÷ 工时）排序。

### P0-1 · 4 改写模式对齐赛题术语 ⏱️ 4h
**问题：** 赛题评分明确要求"数据更新 / 视角调整 / 内容扩展 / 风格转换"，当前实现是 PMS 通用词。
**修改范围：**
- `RewriteMode.java` 枚举重命名：DATA_REFRESH / PERSPECTIVE_SHIFT / CONTENT_EXPAND / STYLE_TRANSFORM
- `Prompts.java` 4 个 rewrite system prompt 语义对齐赛题定义
- 前端 `workspace/index.vue` dropdown label + value 同步
- 数据库 `report_version.source_mode` 既有值迁移
**归属：** Bolt + Pixel
**验收：** 赛题 PDF 原词可在 UI + 后端日志对上

### P0-2 · SSE 生成不卡死 ⏱️ 2h
**问题：** nginx 默认 `proxy_buffering on` 会整段缓冲；当前 `ReportStreamController` 的 SseEmitter 超时 5min 不够长文。
**修改范围：**
- `frontend/nginx.conf` 已有 `proxy_buffering off` → 再加 `proxy_cache off`、`chunked_transfer_encoding on`、`add_header X-Accel-Buffering no`
- `ReportStreamController` SSE_TIMEOUT_MS 放到 10min
- backend 直接响应头带 `X-Accel-Buffering: no`
**归属：** Arch + Bolt
**验收：** 5000+ 字长报告流式输出全程可见 token 逐字到达

### P0-3 · RAG 引用溯源可视化 ⏱️ 6h
**问题：** 覆盖赛题 50% 分值的最直观加分点，当前一片空白。
**修改范围：**
- `Prompts.generateReport()` 改 prompt 要求 LLM 在关键句末打 `[1] [2]` 角标
- 生成前端 SSE 收到的文本解析 `[n]`，渲染成可 hover 的 `<sup>` 元素
- 右侧新增"引用面板"组件：生成过程中当前被引用的 chunk 高亮成淡紫色 + 呼吸光；hover 角标浮出原文 tooltip
- `/api/v1/reports/{id}/chunks` 新增端点返回本次生成用到的 chunks（含 filename/content/score）——后端 `ReportGenerationServiceImpl` 已持有该数据，只需存到 report.metadata
**归属：** Bolt（后端） + Pixel（前端交互）
**验收：** 生成 demo 时评委能看到"模型每写一句话在看哪份文档"

### P0-4 · 流式打字光标 + Markdown 渐进渲染 ⏱️ 4h
**问题：** 当前 textarea 纯文本，无 AI 感；报告里 `# / ## / **` 是乱码字符串。
**修改范围：**
- workspace 主编辑区 `el-input textarea` → `div contenteditable="false"` + v-html 渐进插入
- SSE 每到新 token：先 markdown 解析，再追加到 DOM，尾部插入 `<span class="cursor blink">▍</span>`
- 引入 `markdown-it` + `DOMPurify`（Pixel 建议，成本极低）
- reports 详情 dialog 同步用 `markdown-it` 替换自写正则
**归属：** Pixel
**验收：** 打字机效果 + 标题/粗体/列表正常渲染

### P0-5 · 答辩 Demo 数据铺垫 ⏱️ 3h
**问题：** 评委 5 分钟里不能让他看到空白表格 / 空知识库。
**修改范围：**
- `init.sql` 加 seed：3 个知识库（政策/行业/舆情各 1）+ 每库 3 份文档（用样例报告切成 chunks）
- 5 个内置模板的 `styleDescription` 补齐（当前可能是空）
- 创建 1-2 份示范 report 作为"历史报告库"展品
**归属：** Bolt + Lens（内容）
**验收：** 登录后首页 stats 卡全非零，4 页都有东西看

### P0-6 · 必交材料闭环 ⏱️ 2h
**问题：** Apex 列了 11 项，部分缺失。
**修改范围：**
- 演示视频脚本 + 3 分钟录屏（Apex 的 Golden Path 即脚本）
- 部署文档 README.md（1 分钟 docker compose 拉起）
- AI 工具使用日志 lint（已有，加最后几行）
- PPT（总管职责，3-5 页：痛点/差异化/演示/架构/答谢）
**归属：** Leo 牵头，Apex 验收

**P0 总工时：** ≈ 21h（2.5 人日 × 7 角色并行 ≈ 1 个工作日可完成）

---

## 三、P1 加分（有余力再上）

| # | 项目 | 工时 | 价值 | 归属 |
|---|---|---|---|---|
| P1-1 | 晴天 MCP（search + sass）实接入，workspace 支持"引入外部行业数据源" | 8h | Vera 提到的核心差异化 | Bolt + Arch |
| P1-2 | 段落级改写入口（hover 段落浮按钮，与全文改写互补）| 5h | Pixel 爆点 4⭐ | Pixel |
| P1-3 | 版本 diff 视图做"并列双栏（左旧右新，增删着色）"| 4h | 赛题 25% 改写项的最佳可视 | Pixel + Bolt |
| P1-4 | dark mode 切换（CSS var 方案即可） | 3h | 答辩演示气质分 | Pixel |
| P1-5 | Tika 解析状态 SSE 推进（替代上传后 setTimeout 2s 轮询） | 3h | AI 感 + 流畅度 | Bolt |
| P1-6 | Redis 用起来（token 黑名单 / 生成任务锁） | 3h | 修 Arch 扣分点：配了却不用 | Bolt |
| P1-7 | docker-compose backend 改用 `reportai` 账号 | 0.5h | 修 Arch 扣分点：root 连 DB | Bolt |
| P1-8 | LLM failover（豆包挂自动切 Claude，反之）| 4h | 演示保命 | Arch |

**P1 总工时：** ≈ 30h（若 P0 1 日内完成，剩余 2 日可摊出 ≥ 4 项）

---

## 四、P2 / Won't Do（明确砍掉）

| # | 项目 | 砍掉理由 |
|---|---|---|
| 1 | 向量数据库（Qdrant / Milvus） | CLAUDE.md 明确禁止；MySQL FULLTEXT + CJK 分词对 MVP 足够 |
| 2 | 多租户 UI 切换 | tenant_id 列存在但非评分项，演示单租户即可 |
| 3 | 并发改写冲突处理 | Arch 风险 P2，演示场景不会触发 |
| 4 | export/docx/pdf 下载 | Lens 在 Won't Do；前端直接"复制 md"够用，评分未强调 |
| 5 | 操作审计日志可视化 | 后端有，前端已复用第一站页，不作演示重点 |
| 6 | MFA 登录 | User 实体有 mfa_enabled 字段但赛题无要求 |
| 7 | 更细的权限控制（按钮级） | 后端 @PreAuthorize 缺，但答辩期 admin 一个角色足够 |

---

## 五、差异化话术（答辩 3 分钟开场）

源自 Vera：

> "市面上的 AI 写作产品，要么是 Notion/豆包拉公网，要么是 Jasper/秘塔做单篇改写，要么是 WPS 灵犀做通用助理。**ReportAI 是行业里第一个把「企业私域知识库 + 实时行业 MCP 数据 + 四模式并列可视改写」三件套合在一起的产品**，直接对应赛题 75% 的核心评分项（知识检索 25% + 内容生成 25% + 多模式改写 25%）。接下来 5 分钟的演示会把这三件事现场各走一遍。"

---

## 六、排期建议

- **Day 1（今日 16:00 起 到次日 12:00）**：P0-1 / P0-2 / P0-4 / P0-5 并发；P0-3 开工（最大项）
- **Day 2（次日 12:00-24:00）**：P0-3 收尾 + P0-6 必交材料；P1 挑 3 项
- **Day 3（答辩前最后 24h）**：整体彩排 3 遍 + 剩余 P1；录屏定稿

---

## 七、下一步动作

Leo 已将本路线图落档。建议：

1. 立刻派发 **P0-1**（对齐 4 模式术语）给 Bolt，1 commit 搞定
2. 立刻派发 **P0-2**（nginx SSE 保命）给 Arch，1 commit 搞定
3. P0-3/P0-4 列为下一轮并行任务（Bolt 后端 + Pixel 前端并发）
4. P0-5 / P0-6 留作 Day 2 收尾

要不要我现在就把 **P0-1 和 P0-2** 两个最紧急的修复并发派出去？
