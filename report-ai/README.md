# ReportAI · 智能报告写作平台

AI 闯关赛第二站 T5 赛题（"知识库生成、以稿写稿"）参赛作品。一键起栈 → 选模板/知识库/主题 → 生成带引用溯源的完整报告，支持 4 种整稿改写 + 续写 + 段落级 AI 工具 + 修订视图。

> 项目目录约定与开发计划见仓库根 `CLAUDE.md` 与 `docs/03-计划/2026-04-18-report-ai.md`。

---

## 1. 五分钟启动

```bash
cp .env.example .env
# 填入：DOUBAO_API_KEY / DOUBAO_ENDPOINT（必填）
#       TAVILY_API_KEY（可选，未配也能跑）

./start.sh             # 全栈 Docker 起：mysql + redis + es + backend + frontend
./start.sh --seed      # 起完后顺带把示例 PDF 灌进知识库（首次推荐）
./start.sh --dev       # 仅起依赖（mysql+redis+es），前后端在 IDE/npm 里跑
```

启动后：

| 入口 | 地址 | 默认账号 |
| --- | --- | --- |
| 前端 | http://localhost:3001 （Docker）/ http://localhost:5173 （`--dev`） | `admin / admin123` |
| 后端 | http://localhost:8081 （Docker）/ http://localhost:8080 （`--dev`） | — |
| Swagger | `<后端>/swagger-ui/index.html` | — |
| Kibana（ES 调试，可选） | http://localhost:5601 | — |

> 端口刻意避开第一站；详见根目录 `CLAUDE.md` 端口表。

---

## 2. 五个模块对照赛题

| 赛题模块 | 入口页 | 关键能力 |
| --- | --- | --- |
| ① 知识库 | 顶部"知识库" | 多 KB 管理、PDF/DOCX/TXT 上传、Tika 解析、ES bigram 索引、原文预览 |
| ② 写作模板 | "模板中心" | 风格分析（结构 + 语气 + 用词）、内置 5 套样例模板、原文预览 |
| ③ 写作工作台 | "写作工作台" | 选 KB + 模板 + 主题 → SSE 流式生成；自动调用晴天 MCP / Tavily；引用 `[n]` 角标 + 右栏溯源；数据来源面板 |
| ④ 改写 / 以稿写稿 | 工作台"改写"菜单 + 段落 BubbleMenu | 4 模式整稿改写：DATA_UPDATE / ANGLE_SHIFT / EXPAND / STYLE_SHIFT；段落级 改写 / 扩写 / 精简；续写新章节 |
| ⑤ 报告库 + 修订视图 | "报告库" → 报告详情 | 多版本管理、版本对比（三色词级 diff）、覆盖度仪表盘、引用列表附录、Word/PDF 导出 |

---

## 3. 交付资产

| 类别 | 位置 |
| --- | --- |
| 源码 | 本仓库（`backend/` 多模块 + `frontend/` Vue3） |
| 数据库 init | `database/init.sql` + `database/migration/V*.sql` |
| 设计说明 | `docs/07-设计/2026-04-19-reportai-t5-design.md` |
| AI 工具使用日志 | `docs/06-审计与演示/AI工具使用日志.md` |
| 现场演示脚本 | `docs/06-审计与演示/2026-04-20-现场演示脚本.md` |
| E2E 测试 | `docs/06-审计与演示/E2E测试报告.md` + `scripts/e2e-smoke.md` |
| 演示数据一键灌库 | `scripts/demo-deliverables.sh`（3 主题 × 4 改写 + 1 续写） |

---

## 4. 故障速查

| 现象 | 自检 |
| --- | --- |
| 引用溯源空白 | 看 `report-ai/logs/backend.log` 的 `RAG search` 日志；ES 索引：`curl http://localhost:9200/_cat/indices` |
| 生成接口返回 "未配置 doubao.endpoint" | `.env` 里 `DOUBAO_ENDPOINT=` 没填；填入后重启后端 |
| Word 导出 500 | 看后端日志末尾，多半是 OOXML 模板渲染问题；详见 `docs/06-审计与演示/E2E测试报告.md` |
| 前端 5173/3001 打不开 | `docker compose ps` 看 frontend 是否绿；或 `npm run dev` 单独跑 |

更详细的端到端验收清单见 `scripts/e2e-smoke.md`。
