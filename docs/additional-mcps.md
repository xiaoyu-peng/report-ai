# 建议引入的第三方 MCP

> 目的：在 Cherry Studio / Claude Code 里挂上这些 MCP，直接加速 ReportAI 的 VibeCoding 过程。
> 选型原则：**"能省事 + 对赛题评分直接加分"**，不盲目装多。

## Tier 1 · 强烈建议（评分+开发效率双收益）

### 1. filesystem — 让 LLM 直接读写本地文件
- 包名：`@modelcontextprotocol/server-filesystem`（官方参考实现）
- Cherry Studio 配置：

```json
"filesystem": {
  "type": "stdio",
  "command": "npx",
  "args": ["-y", "@modelcontextprotocol/server-filesystem", "/Users/penghui/工作/Coding/Ai挑战赛/第二站"]
}
```

- **用在哪**：Cherry Studio 里调 LLM 批处理样例报告（抽结构、生成 prompt、产出"风格分析 JSON"），不用手动拷贝粘贴；路径限制在本项目根目录，安全。

### 2. github — 管 PR / issues / Actions
- 包名：`@modelcontextprotocol/server-github`
- 需要 `GITHUB_PERSONAL_ACCESS_TOKEN` 环境变量（就是现在这个 ghp_…，强烈建议**revoke 重发**再用）。
- Cherry Studio 配置：

```json
"github": {
  "type": "stdio",
  "command": "npx",
  "args": ["-y", "@modelcontextprotocol/server-github"],
  "env": {"GITHUB_PERSONAL_ACCESS_TOKEN": "${GITHUB_TOKEN}"}
}
```

- **用在哪**：交卷要交"AI 工具使用日志"，所有 commit/PR 可以让 LLM 直接读 GitHub API 归档，避免手抄；比赛期间也方便让 Claude Code 看远端 CI 状态。

### 3. MySQL 只读 MCP — 调试 RAG FULLTEXT
- 推荐：`designcomputer/mysql_mcp_server`（https://github.com/designcomputer/mysql_mcp_server）
- 或 `benborla/mcp-server-mysql`（只读，更安全）
- Cherry Studio（docker-compose 起来后的 MySQL：host 端口 3307）：

```json
"mysql-reportai": {
  "type": "stdio",
  "command": "npx",
  "args": ["-y", "@benborla/mcp-server-mysql"],
  "env": {
    "MYSQL_HOST": "127.0.0.1",
    "MYSQL_PORT": "3307",
    "MYSQL_USER": "reportai",
    "MYSQL_PASS": "reportai123",
    "MYSQL_DB": "report_ai"
  }
}
```

- **用在哪**：Task 4 调 FULLTEXT 查询时，让 LLM 直接 `SHOW INDEX FROM knowledge_chunk`、`EXPLAIN MATCH AGAINST ...`，比手动 `docker exec mysql ...` 快 5 倍。官方仓库**已归档**旧版数据库 MCP，请用社区维护的上面这两个。

## Tier 2 · 报告类型专项（对赛题评分"报告质量 25%"有直接提升）

### 4. Docling (IBM) — 替代 Tika 做 PDF 解析
- 仓库：`DS4SD/docling` + 配套 MCP
- 核心能力：**版式识别 / 阅读顺序 / 表格结构**，远强于 Apache Tika 的纯文本抽取。赛题给的 5 份 PDF 样例都有复杂表格（政策影响报告里的"适用人群表"、传播分析里的"渠道分布表"），Tika 抽出来会糊成一片。
- 风险：Python 侧服务，要多起一个容器；建议 **先用 Tika 跑通，改造时替换成 Docling 调用，作为第二轮优化**。不要第一版就上。

### 5. Fast.io（或 Vectara / Qdrant）— 备选 RAG 方案
- 本站赛题的评分 "知识检索准确性 25%" 直接考 RAG 质量。当前计划用 MySQL FULLTEXT 做 baseline，**第二轮可接 Qdrant 向量检索**做 A/B 对比：
  - 如果向量检索显著更好，演示里加一页"FULLTEXT vs 向量" 对比，直接收 25% 加分；
  - 如果差不多，则不切，节省时间。
- Qdrant MCP（`qdrant/qdrant-mcp`）可走 Docker 本地部署，不依赖云端。

## Tier 3 · 可选（不紧急，但装了不碍事）

| MCP | 用途 |
| --- | --- |
| `@modelcontextprotocol/server-fetch` | 通用 URL 抓取；实现模块一"通过 URL 写入知识库"时比自己写 WebClient 省事 |
| `@playwright/mcp` | 前端跑起来后做 E2E 冒烟测试；Claude Code 已有 `webapp-testing` skill 覆盖同类能力 |
| `@context7/mcp-server` | **已在会话里** — Vue/Spring Boot/MyBatis Plus 的最新文档；继续保留 |
| `figma` | **已在会话里** — 如果后面有 Figma 设计稿导入，保留 |

## 取舍建议

**本周只加 3 个**：`filesystem` + `github` + `mysql-reportai`。
- 它们全部是 **stdio 类型**，Cherry Studio 里复制粘贴一次就行；
- 覆盖开发 (fs) + 交付 (github 日志) + 调试 (mysql) 三条主线；
- 不引入新的运行时依赖（都用 npx，无需预装）。

剩下的 Docling / Qdrant / fetch 等到**第二轮优化**时视剩余时间再加，不要一开始就堆到八九个，Cherry Studio 工具列表乱了反而降低模型调度准确率。

## 安全复盘

- `ghp_...` token 已经在聊天里明文出现过。push 完本次 commits 后**立刻去 GitHub Settings → Developer settings → Personal access tokens → Revoke**，然后重发一枚新 token，新 token 通过环境变量（`export GITHUB_TOKEN=...`）注入上面的 github MCP 配置，**不要再粘贴给任何模型**。
- `appkey: OOn05z7m` 是晴天 MCP 的业务 key，如果赛事方是敏感渠道，也建议问组委会是否需要换专属 key。

## 参考

- 官方 MCP 服务器清单: <https://github.com/modelcontextprotocol/servers>
- awesome-mcp-servers (社区): <https://github.com/wong2/awesome-mcp-servers>, <https://github.com/punkpeye/awesome-mcp-servers>
- Spring AI MCP 官方文档: <https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html>
- MySQL MCP: <https://github.com/designcomputer/mysql_mcp_server>
- Docling: <https://github.com/DS4SD/docling>