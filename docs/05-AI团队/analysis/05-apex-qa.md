# 05 · Apex 答辩风险清单 + 测试计划

> 作者：Apex（首席质检官）｜日期：2026-04-18｜版本：v1.0
> 基线：backend 8081 / frontend 3001 全栈已通，init.sql 已重建，5 端点冒烟 200。
> 目标：为 4/21 答辩建立质量底线，锁定 Golden Path、封堵高风险场景、固化评估打分卡。

---

## 1. 答辩演示关键路径（Golden Path）

**脚本目标**：5 分钟内端到端跑通"登录 → 知识库 → 上传 → 模板 → SSE 生成 → 改写 → 版本对比 → 导出"，评委直观感受 T5 五个模块全部可用。

| 步骤 | 动作 | 前端展示 | 后端日志（`docker compose logs -f backend`）| 已知踩点 |
| --- | --- | --- | --- | --- |
| 1 | 打开 http://localhost:3001，`admin / admin123` 登录 | Element-Plus 登录页 → 跳转 `/dashboard`，左侧菜单亮起 | `POST /api/v1/login 200`，`JwtUtil` 签发 token | 若 BCrypt 与 init.sql seed 不一致 → 401；务必提前 `down -v` 重置 |
| 2 | 点「知识库 → 新建」，命名"政策影响-2026Q1" | 卡片式列表新增一项 | `INSERT INTO knowledge_base`，`tenant_id` 自动注入 | tenant 未隔离会出现跨用户可见 |
| 3 | 进入详情，上传 1 份样例 PDF（`docs/以稿写稿-报告样例/政策影响类.pdf`）+ 1 个 URL（任选公开政策页） | 进度条 100%，文档列表出现两行，状态 `READY` | Tika 解析日志 `TikaParser: 12345 chars`，`ChunkService: produced N chunks`，`FULLTEXT index updated` | 超过 10 MB 的 PDF Tika 解析可能 > 15 s，建议提前预热 |
| 4 | 「模板中心 → 新建」，粘贴一份参考报告全文 | 模板卡片出现，右下角角标 `风格分析完成` | `LlmClient: claude/doubao analyze 2.1 s` | Claude endpoint 429 时降级到豆包，演示前确认 provider 配置 |
| 5 | 「写作工作台」选知识库 + 模板 + 主题「2026 Q1 新能源车补贴政策影响」，点「开始生成报告」 | 右侧编辑器显示"AI 正在思考…"，随后 token 逐字流入 | `SSE /reports/{id}/generate`，`ReportGenerationService: first token @320 ms`，持续 `emitter.send(token)` | **SSE 最高危**：若 nginx `proxy_read_timeout` 没设 300 s，或中间存在企业代理/VPN，长报告会在 30/60 s 被切断 |
| 6 | 生成结束后点「改写 → 视角调整 / 扩展 / 数据更新 / 风格转换」任意两项 | 编辑器重新流入，底部状态切为"改写中" | `SSE /reports/{id}/rewrite`，`mode=PERSPECTIVE` 等 | **当前代码只实现了 POLISH/EXPAND/CONDENSE/CUSTOM，不是赛题要求的 4 模式**，必须在 4/19 前对齐（见风险 R1）|
| 7 | 「报告库」选该报告 → 「版本」Tab → 选 v1 vs v3 → 点 diff | 并排高亮新增/删除/修改的段落 | `GET /reports/{id}/versions/1/diff/3`，`VersionService.diff` 返回行级 patch | 行级 diff 在长段落下噪声大，演示前建议用 200 字短段落 |
| 8 | 点「导出 Word / PDF」 | 浏览器弹下载 | `ReportController.export: generated .docx 38 KB` | 导出功能尚未验收（见待办 D3）|

**兜底动作**：若现场 LLM 流式挂了，用预录屏幕 30 s 片段替代；所有报告提前生成 10 篇落入 `reports` 表作静态演示。

---

## 2. 答辩高风险场景 TOP 10

| # | 场景 | 可能炸点 | 概率 | 自测方法 | 规避策略 |
| --- | --- | --- | --- | --- | --- |
| R1 | **4 种改写模式与赛题不匹配** | 前端下拉是 POLISH/EXPAND/CONDENSE/CUSTOM，赛题明文要求 数据更新 / 视角调整 / 内容扩展 / 风格转换 → 评委现场对照赛题扣 25% | **H** | 打开 `report-ai/frontend/src/views/workspace/index.vue:145-148`，检查 dropdown command 值 | **P0 必修**：改为 DATA_UPDATE / PERSPECTIVE / EXPAND / STYLE，后端 `RewriteMode` enum 同步；Prompts 分支对应 4 套模板 |
| R2 | SSE 被企业代理/nginx 切断 | 长报告（>5000 字）生成超 60 s，前端显示"AI 正在思考…"卡住 | **H** | `curl -N -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/reports/1/generate`，观察有无 30s 或 60s 整点断流 | 确认 `frontend/nginx.conf` 已有 `proxy_read_timeout 300s; proxy_buffering off;`；演示机直连 localhost 绕过公司代理；报告长度上限降到 3500 字 |
| R3 | 中文 PDF Tika 解析乱码/空字符 | 扫描版 PDF 无文本层 → chunks 全空 → RAG 召回为 0 | M | `curl -F "file=@扫描版.pdf" http://localhost:8081/api/v1/knowledge/bases/1/documents`，再 `GET /knowledge/search?kbId=1&q=关键词` 看 hits | 演示素材统一用数字 PDF/Word；`DocumentService` 在 chunk 数为 0 时给出明确报错提示 |
| R4 | 豆包 endpoint 401/429 | `application.yml` 切 provider=doubao，但 API-Key 过期或超额 | M | `curl -X POST $DOUBAO_URL -H "Authorization: Bearer $KEY" -d '{"model":"...","messages":[{"role":"user","content":"ping"}]}'` | 启动前跑冒烟；配 provider 自动降级（Claude → 豆包）；备用 API-Key 写在 `docker-compose.override.yml` |
| R5 | 浏览器兼容：Safari 对 SSE 严格 | Safari 对 `text/event-stream` + `\n\n` 帧解析敏感；字段大小写要求严格 | M | 在 Safari 17 打开 workspace 点生成，F12 看 EventSource readyState | 现场强制用 Chrome/Edge；nginx 响应加 `X-Accel-Buffering: no` |
| R6 | 大文件上传超时 | Spring Boot 默认 `spring.servlet.multipart.max-file-size=1MB`，>10 MB PDF 直接 413 | M | `curl -F "file=@20mb.pdf" …/documents` 观察返回码 | `application.yml` 中已应设 `max-file-size=100MB` 和 `max-request-size=100MB`；上传前检查 |
| R7 | 重复登录 session 冲突 | 两个浏览器同时登录 admin，token 覆盖，老会话收到 401 | L | Chrome + Safari 各登一次，在 Safari 操作 | 演示只用单浏览器；JWT 无状态本应兼容，核心在前端 store 未做广播隔离 |
| R8 | 路由刷新丢 token | F5 后 Pinia 状态重置，router guard 误判未登录 | M | 登录后任意页面按 F5 | 确认 `stores/auth.ts` 用 `localStorage` 持久化；router `beforeEach` 从 storage 回灌 |
| R9 | FULLTEXT 中文分词失效 | 如果 init.sql 漏了 `ngram` 解析器或 `innodb-ft-min-token-size` 默认 3，单字查询召回 0 | M | `docker exec mysql mysql -uroot -proot123456 -e "SHOW VARIABLES LIKE 'ft_min%'"`，期望 `innodb_ft_min_token_size=1` | compose 已设置，演示前再查一次；FULLTEXT 建表须 `WITH PARSER ngram` |
| R10 | 演示机断网 / Claude 区域限制 | 评委现场无外网 | M | 断网跑一遍 Golden Path | 必须准备 ≥10 篇预生成报告 + 屏幕录制备份；豆包 endpoint 国内可达 |

---

## 3. 核心场景测试用例清单（25 条，P0 × 12）

| ID | 模块 | 场景 | 前置 | 步骤 | 预期 | 优先级 |
| --- | --- | --- | --- | --- | --- | --- |
| TC01 | 认证 | 正确登录 | seed admin 就绪 | POST `/api/v1/login {admin,admin123}` | 200，返回 token + roles=['ADMIN'] | P0 |
| TC02 | 认证 | 错误密码 | - | POST login 错密码 | 401，msg 含"用户名或密码错误" | P0 |
| TC03 | 认证 | Token 过期 | 改 JwtUtil ttl=1s | 2s 后访问 `/dashboard/stats` | 401 | P1 |
| TC04 | 知识库 | 新建 | - | POST `/knowledge/bases` | 200，返回 id，list 可见 | P0 |
| TC05 | 知识库 | 跨租户隔离 | 用户 A、B 各建 1 个 | B 以自己 token 调 `GET bases` | 看不到 A 的 | P0 |
| TC06 | 知识库 | 软删除后不可见 | 已有 kb=5 | DELETE `/bases/5` → GET list | 5 不在列表，DB `deleted=1` | P1 |
| TC07 | 文档 | PDF 上传 | kb=1 | multipart 上传 3 MB PDF | 200，chunk_count>0，status=READY | P0 |
| TC08 | 文档 | 重复文件名 | kb=1 已有"a.pdf" | 再传同名 | 允许或提示重名，数据不串 | P1 |
| TC09 | 文档 | URL 抓取 | 公网可达 | POST `/bases/1/url {url:"https://..."}` | 200，content 非空 | P1 |
| TC10 | 文档 | 非法格式 | - | 上传 `.exe` | 400，msg 含"格式不支持" | P1 |
| TC11 | RAG | 中文单字查询 | kb 含 chunks | `GET /search?kbId=1&q=新能源&topK=10` | hits>0，含 docName/chunkNo/score | P0 |
| TC12 | RAG | 零结果 | - | `q=完全不存在的词xyz123` | hits=[]，不报错 | P1 |
| TC13 | 模板 | 创建并风格分析 | - | POST `/templates` 带 content≥500 字 | 200，返回 id，style_json 非空 | P0 |
| TC14 | 模板 | 列表分页 | 已有 30 条 | GET `?current=2&size=10` | records.length=10，total=30 | P1 |
| TC15 | 报告 | 创建草稿 | - | POST `/reports {title,topic,kbId,templateId}` | 200，status=DRAFT | P0 |
| TC16 | SSE 生成 | 首 token 到达 | 草稿已建 | 浏览器 EventSource 连 `/reports/1/generate` | 3s 内收到 token 事件 | P0 |
| TC17 | SSE 生成 | 完整结束 | 同上 | 等待 `done` 事件 | 最终 content 长度>1000，DB status=DONE | P0 |
| TC18 | SSE 生成 | 长报告不断流 | 模板指定 5000 字 | 跑到底 | 无 30s/60s 整点切断 | P0 |
| TC19 | SSE 生成 | 客户端中断 | - | 收到前 100 token 后 close EventSource | 后端 log 打印 SSE disconnected，不抛异常 | P1 |
| TC20 | 改写 | 4 模式各跑一次 | 有 content 的报告 | POST `/reports/1/rewrite` 4 次，mode 轮换 | 4 次输出结构差异可辨（diff 相似度 < 0.8） | P0 |
| TC21 | 版本 | 多版本列表 | 改写 3 次 | GET `/reports/1/versions` | 返回 ≥3 条，versionNum 递增 | P0 |
| TC22 | 版本 | diff 输出 | 同上 | GET `/versions/1/diff/3` | 返回 added/removed/modified 分段 | P0 |
| TC23 | 版本 | 回滚不破坏历史 | - | POST `/versions/1/restore` | 新增 vN+1，v1 仍存在 | P1 |
| TC24 | 导出 | Word | content 就绪 | GET `/reports/1/export?fmt=docx` | 返回 application/vnd.openxmlformats… | P1 |
| TC25 | 日志 | AI 调用日志 | 跑过一次生成 | GET `/logs/invocation` | 可见 provider/model/耗时/token | P1 |

**P0 优先级合计 12 条**，覆盖登录、多租户、RAG、SSE 全程、四模式、版本 diff —— 这 12 条必须在 4/20 20:00 前全绿，否则不得进入答辩彩排。

---

## 4. AI 输出质量评估标准（6 维度打分卡，满分 30 分）

用于「生成 3 次取最优」的选型依据。每维度 1–5 分，3 分合格，4 分良好，5 分优秀。总分 ≥ 24（80%）方可入选演示稿池。

| 维度 | 权重 | 1 分 | 3 分 | 5 分 | 对应赛题评分项 |
| --- | --- | --- | --- | --- | --- |
| **结构完整度** | 6 | 无小标题，通段 | 有 3+ 小标题，但层级混乱 | 严格复现样例结构（背景 / 核心数据 / 分析 / 结论 / 建议），层级清晰 | 报告生成质量 25% |
| **引用准确度** | 5 | 无任何来源标注 | 有引用但来源错配 | 每处数据/观点后有 `[来源：文档名·块号]`，一一可溯回 RAG | 检索准确性 25% |
| **语气一致性** | 5 | 口语化或机翻腔 | 与样例基本一致但偶有跳脱 | 通篇与模板样例风格（政府公文 / 行业分析 / 日报）高度一致 | 仿写还原度（生成质量 25%） |
| **字数达标** | 3 | ±50% | ±20% | ±10% 以内，落在要求区间 | 系统完整性 10% |
| **事实性** | 6 | 出现凭空数据或自相矛盾 | 关键结论有凭据，细节处偶有虚构 | 所有数字、机构名、法条均来自知识库 chunks | 检索准确性 25% |
| **可读性** | 5 | 段落混乱、长句堆砌 | 结构可读，但术语过密 | 段落逻辑分明，术语首次出现有解释 | 生成质量 25% |

**使用方式**：答辩前选 5 个主题，每个主题生成 3 次，由 Apex 按上表打分，取最高分入选演示库。所有生成记录保留在 `reports` 表，答辩时可即时调用。

---

## 5. 必交答辩材料清单

对照赛题 PDF「总体要求」第 1-5 条 与 T5 「交付要求」第 1-6 条：

| # | 材料 | 状态 | 归属 | 截止 |
| --- | --- | --- | --- | --- |
| M1 | 可运行 Web 系统（5 模块可用）| 已有（4/18 冒烟通过）| Bolt | — |
| M2 | ≥ 10 篇不同主题完整报告（含引用）| **待做** | Pixel + Vera（批量生成 + 人工审核）| 4/20 12:00 |
| M3 | 3 篇参考报告 × 4 种改写模式演示 | **待做（阻塞于 R1 四模式修复）** | Bolt 修 R1 → Pixel 跑演示 | 4/20 18:00 |
| M4 | ≥ 3 个改写前后 diff 截图 / 录屏 | **待做** | Pixel | 4/20 20:00 |
| M5 | 设计说明文档（含架构图、端口、部署）| 部分（CLAUDE.md + plans 已有，需合成一份 PDF）| Arch | 4/20 22:00 |
| M6 | AI 工具使用日志 | 已有（`docs/AI工具使用日志.md`），4/19–4/21 继续追加 | Leo | 持续 |
| M7 | 演示视频（≤ 5 分钟，Golden Path 全程）| **待做** | Pixel + Lens | 4/21 10:00 |
| M8 | 答辩 PPT | **待做**（5–8 页：痛点/架构/演示/AI 工具链/评分映射）| Leo + Lens | 4/21 10:00 |
| M9 | 代码仓库 URL | 已有 https://github.com/xiaoyu-peng/report-ai | Leo | — |
| M10 | Docker 部署文档 + `.env.example` | 部分（compose 已有，env 示例待补）| Bolt | 4/20 12:00 |
| M11 | 最终压缩包「姓名-T5智能报告写作平台.zip」| 待做（含 M1-M10）| Leo | 4/21 14:00 |

**关键卡点**：M3 / M4 阻塞于 R1 —— 4 模式必须在 **4/19 24:00 前**完成后端 enum + 前端 dropdown + Prompts 四套模板对齐，否则 25% 「改写质量」分直接扣光。

---

## 最可能让我们当场翻车的一个场景

**前端改写下拉仍是 POLISH/EXPAND/CONDENSE/CUSTOM，评委对照赛题"数据更新 / 视角调整 / 内容扩展 / 风格转换"发现对不上 —— 直接丢掉 25% 改写分外加"审题不清"的印象分。**
