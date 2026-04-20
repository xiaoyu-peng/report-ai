# 报告写作场景 MCP 需求分析

> 基于 ReportAI 产品工作流，分析还缺哪些 MCP，按优先级排序

---

## 一、报告写作完整工作流与 MCP 需求映射

```
用户输入主题/关键词
    │
    ├─→ ① 信息检索（搜索互联网+知识库+舆情数据）
    │     ✅ 晴天 search-mcp（互联网发文检索）
    │     ✅ 晴天 sass-mcp（舆情分析指标）
    │     ✅ 本地 RAG（MySQL FULLTEXT）
    │     ❌ 通用 Web 搜索（政策原文/行业报告/技术文档）
    │     ❌ URL 抓取（指定网页内容入库）
    │
    ├─→ ② 数据分析（统计/图表/趋势）
    │     ✅ 晴天 sass-mcp（声量/情感/热文/渠道/地域等 41 个指标）
    │     ❌ 图表生成（将数据转为可视化图表嵌入报告）
    │     ❌ 数据计算（对原始数据做统计/对比/预测）
    │
    ├─→ ③ 内容生成（AI 写作 + 引用溯源）
    │     ✅ LLM 流式生成（Claude/豆包）
    │     ✅ 引用溯源（RAG chunk 角标）
    │     ❌ 翻译辅助（中英双语报告/引用外文资料）
    │
    ├─→ ④ 改写优化（4 模式 + 段落级改写）
    │     ✅ 全文改写（数据更新/视角调整/内容扩展/风格转换）
    │     ✅ 段落级改写（改写/扩写/精简）
    │     ✅ 续写新章节
    │     ❌ 事实核查（验证报告中数据/引用的准确性）
    │
    ├─→ ⑤ 版本管理（diff/回滚/对比）
    │     ✅ 版本 diff + 回滚
    │     ✅ 红绿双栏可视化
    │
    └─→ ⑥ 导出交付（Word/PDF/分享）
          ✅ Word 导出（POI）
          ✅ PDF 导出（html2pdf）
          ❌ 图表嵌入导出（含 ECharts 图表的 Word/PDF）
```

---

## 二、缺口分析：6 个关键 MCP 需求

### 🔴 P0 — 必须有（直接影响报告内容质量）

#### 1. Web 搜索 MCP — Tavily / Brave Search

**为什么需要：**
- 当前只有晴天 MCP 搜索舆情文章，但报告写作还需要搜索**政策原文、行业报告、技术文档、学术论文**等非舆情内容
- 例如写"政策影响分析报告"时，需要搜索国务院/各部委官网的政策文件原文
- 写"科技情报专题报告"时，需要搜索 arXiv 论文、技术博客

**推荐方案：Tavily MCP**
- 免费额度 1000 次/月，足够开发和演示
- 搜索质量高，支持深度搜索模式（Tavily Extract 可抓取网页全文）
- 安装简单：`npx -y tavily-mcp`

**你需要做的：**
1. 去 https://tavily.com 注册账号，获取 API Key
2. 在 Cherry Studio / Claude Code 配置中添加：

```json
"tavily": {
  "type": "stdio",
  "command": "npx",
  "args": ["-y", "tavily-mcp"],
  "env": {
    "TAVILY_API_KEY": "tvly-你的key"
  }
}
```

#### 2. Fetch MCP — URL 内容抓取

**为什么需要：**
- 用户经常需要引用某个网页的内容（如政策原文链接、行业报告页面）
- 当前后端有 URL 抓取入库功能，但前端没有入口
- Fetch MCP 可以让 AI 直接读取指定 URL 的内容并提取关键信息

**推荐方案：Fetch MCP（官方）**
- 完全免费，无需 API Key
- 支持抓取任意 URL 并转为 Markdown

**你需要做的：**
直接在配置中添加：

```json
"fetch": {
  "type": "stdio",
  "command": "npx",
  "args": ["-y", "@modelcontextprotocol/server-fetch"]
}
```

### 🟡 P1 — 强烈建议（提升报告专业度和演示效果）

#### 3. ECharts MCP — 图表生成

**为什么需要：**
- 传播分析报告需要嵌入**声量趋势图、情感分布饼图、渠道声量柱状图、地域热力图**
- 当前晴天 MCP 返回的是原始数据（JSON），报告里只能用文字描述，没有可视化图表
- ECharts MCP 可以让 AI 根据数据自动生成 ECharts 配置，前端直接渲染

**推荐方案：ECharts MCP**
- 完全免费
- 生成 ECharts option JSON，前端用 ECharts 渲染

**你需要做的：**
1. 安装前端 ECharts：`cd report-ai/frontend && npm install echarts`
2. 在 Cherry Studio 配置中添加：

```json
"echarts": {
  "type": "stdio",
  "command": "npx",
  "args": ["-y", "echarts-mcp"]
}
```

#### 4. Filesystem MCP — 本地文件读写

**为什么需要：**
- 让 AI 直接读写项目文件，加速开发
- 批量生成样例报告、风格分析 JSON
- 调试时直接查看日志文件

**你需要做的：**
```json
"filesystem": {
  "type": "stdio",
  "command": "npx",
  "args": ["-y", "@modelcontextprotocol/server-filesystem", "/Users/penghui/工作/Coding/Ai挑战赛/第二站"]
}
```

### 🟢 P2 — 可选（锦上添花）

#### 5. MySQL MCP — 数据库调试

**为什么需要：**
- 调试 RAG FULLTEXT 检索时，直接查数据库验证
- 演示时可以展示"AI 直接查数据库"的能力

**你需要做的：**
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

#### 6. Sequential Thinking MCP — 深度推理

**为什么需要：**
- 写复杂报告时（如政策影响分析），需要多步推理
- Sequential Thinking 让 AI "思考更深入"，生成更有逻辑的报告

**你需要做的：**
```json
"sequential-thinking": {
  "type": "stdio",
  "command": "npx",
  "args": ["-y", "@modelcontextprotocol/server-sequential-thinking"]
}
```

---

## 三、推荐配置方案

### 最小配置（3 个 MCP，5 分钟搞定）

```json
{
  "qingtian-search": {
    "type": "streamableHttp",
    "url": "https://api-sc.wengegroup.com/search-mcp",
    "headers": { "appkey": "OOn05z7m" }
  },
  "qingtian-sass": {
    "type": "streamableHttp",
    "url": "https://api-sc.wengegroup.com/sass-mcp",
    "headers": { "appkey": "OOn05z7m" }
  },
  "fetch": {
    "type": "stdio",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-fetch"]
  }
}
```

### 推荐配置（6 个 MCP，15 分钟搞定）

在最小配置基础上增加：

```json
{
  "tavily": {
    "type": "stdio",
    "command": "npx",
    "args": ["-y", "tavily-mcp"],
    "env": { "TAVILY_API_KEY": "tvly-你的key" }
  },
  "filesystem": {
    "type": "stdio",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-filesystem", "/Users/penghui/工作/Coding/Ai挑战赛/第二站"]
  },
  "echarts": {
    "type": "stdio",
    "command": "npx",
    "args": ["-y", "echarts-mcp"]
  }
}
```

### 完整配置（9 个 MCP，30 分钟搞定）

在推荐配置基础上增加：

```json
{
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
  },
  "sequential-thinking": {
    "type": "stdio",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-sequential-thinking"]
  },
  "github": {
    "type": "stdio",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-github"],
    "env": { "GITHUB_PERSONAL_ACCESS_TOKEN": "ghp_你的token" }
  }
}
```

---

## 四、后端集成方案

对于需要集成到 ReportAI 后端的 MCP（Tavily/Fetch），有两种方案：

### 方案 A：后端直接调用（推荐，已实现框架）

类似晴天 MCP 的方式，在后端新增 `TavilyClient` + `FetchClient`：

```java
// report-hub-knowledge/src/main/java/com/reportai/hub/knowledge/mcp/TavilyClient.java
@Component
public class TavilyClient {
    @Value("${tavily.api-key:}")
    private String apiKey;
    
    public JsonNode search(String query) {
        // 调用 Tavily REST API
    }
    
    public JsonNode extract(String url) {
        // 调用 Tavily Extract API 抓取网页全文
    }
}
```

### 方案 B：前端直接调用（快速验证）

前端 workspace 页面直接调 Tavily API，搜索结果展示在侧栏。

---

## 五、你需要做的清单

| # | 任务 | 需要你操作 | 预计时间 |
|---|------|-----------|---------|
| 1 | 注册 Tavily 获取 API Key | ✅ 去 tavily.com 注册 | 2 分钟 |
| 2 | 配置 Fetch MCP | 无需操作，直接 npx | 0 分钟 |
| 3 | 配置 Filesystem MCP | 无需操作，直接 npx | 0 分钟 |
| 4 | 配置 ECharts MCP | 无需操作，直接 npx | 0 分钟 |
| 5 | 安装前端 ECharts | `npm install echarts` | 1 分钟 |
| 6 | 配置 MySQL MCP | Docker 起来后即可用 | 2 分钟 |
| 7 | 配置 Sequential Thinking | 无需操作，直接 npx | 0 分钟 |
| 8 | 配置 GitHub MCP | 需要 GitHub PAT | 5 分钟 |

**唯一需要你注册的是 Tavily API Key**，其他都是零配置即装即用。
