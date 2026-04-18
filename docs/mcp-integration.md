# 晴天自研 MCP 接入方案

> 来源：`docs/晴天自研的MCP.txt`
> 探测时间：2026-04-18 11:00（已通过 `initialize` + `tools/list` 双向确认可达）。

## 0. 在 Cherry Studio (macOS) 中配置（手动接入，免编码验证）

Cherry Studio 支持 streamableHttp 类型的 MCP Server，可直接把这两个晴天服务挂进来，用于在桌面客户端里和 LLM 对话并调用工具，便于赛前调试 Prompt 与排查响应体字段。

### 0.1 通过 UI 添加（推荐）

1. 打开 **Cherry Studio → 左下角"设置" (齿轮图标) → 左侧菜单 "MCP 服务器"**；
2. 点击右上角 **"+ 添加服务器"**；
3. 按下表逐项填写，**两个服务各添加一次**：

**① 晴天信息检索**

| 字段 | 值 |
| --- | --- |
| 名称 Name | `qingtian-search` |
| 类型 Type | `可流式传输的HTTP (streamableHttp)` |
| URL | `https://api-sc.wengegroup.com/search-mcp` |
| 请求头 Headers | 新增一行：Key = `appkey`，Value = `OOn05z7m` |
| 超时 Timeout | `60s`（`article-detail` 偶发较慢）|
| 描述 Description | `晴天信息检索：互联网发文检索 / 文章详情 / 媒体账号` |

**② 晴天分析组件**

| 字段 | 值 |
| --- | --- |
| 名称 Name | `qingtian-sass` |
| 类型 Type | `可流式传输的HTTP (streamableHttp)` |
| URL | `https://api-sc.wengegroup.com/sass-mcp` |
| 请求头 Headers | Key = `appkey`，Value = `OOn05z7m` |
| 超时 Timeout | `120s`（图表聚合类较慢）|
| 描述 Description | `晴天舆情分析：40+ 个指标，含声量/情感/热文/传播路径` |

4. 保存后在列表里把两个服务的 **"启用"开关** 打开；Cherry Studio 会主动调用 `tools/list`，几秒钟后右侧会出现工具计数（`search-mcp` 显示 3 个，`sass-mcp` 显示 40+ 个）。数量对上即配置成功。
5. 回到聊天界面，在输入框上方的 **"工具"按钮** 里勾选这两个服务，模型回答时就能直接触发晴天工具。

### 0.2 通过配置文件落盘（便于跨机同步）

Cherry Studio 在 macOS 下把 MCP 配置写在：

```
~/Library/Application Support/CherryStudio/config.json
```

直接在 `mcpServers` 节点追加以下两段（若已有其他 MCP，保留并追加即可）：

```json
{
  "mcpServers": {
    "qingtian-search": {
      "type": "streamableHttp",
      "url": "https://api-sc.wengegroup.com/search-mcp",
      "headers": {
        "appkey": "OOn05z7m"
      },
      "timeout": 60000,
      "description": "晴天信息检索：互联网发文检索 / 文章详情 / 媒体账号",
      "disabled": false
    },
    "qingtian-sass": {
      "type": "streamableHttp",
      "url": "https://api-sc.wengegroup.com/sass-mcp",
      "headers": {
        "appkey": "OOn05z7m"
      },
      "timeout": 120000,
      "description": "晴天舆情分析：声量/情感/热文/榜单/传播路径 40+ 个指标",
      "disabled": false
    }
  }
}
```

> 改完文件必须**完全退出 Cherry Studio 再重启**（Cmd+Q，不是关闭窗口），否则不会重新加载配置。

### 0.3 冒烟测试

在 Cherry Studio 聊天里直接发下面两条提示词验证连通：

1. `用 qingtian-search 搜最近 7 天关于"人工智能+文旅"的热文，取前 5 条，返回标题和互动数据。`
2. `用 qingtian-sass 对关键词"DeepSeek"做一次 overview 分析，给出渠道分布、情感分布和核心关键词。`

若模型能返回结构化结果（含 `uuid`、`publishTime`、`readCount` 等字段），说明两端打通。

### 0.4 常见坑位

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 列表显示"连接失败" | Header 名写成 `Appkey` / `APPKEY`（大小写敏感）| 改回小写 `appkey` |
| 工具列表为空 | 选了 `sse` 或 `stdio` 类型 | 必须选 **streamableHttp** |
| 超时 / 调用被截断 | `timeout` 太小（默认 30s）| `sass-mcp` 拉长到 120s |
| 模型回答没有触发工具 | 聊天顶部"工具"按钮没勾 | 勾上 `qingtian-search` / `qingtian-sass` |
| Header 不生效 | 在 URL 里拼 `?appkey=xxx` | appkey 只走 Header，不走 query string |
| 回复里 `publishTime` 丢失 | 模型自作主张截断字段 | 在系统提示里强制"必须原样保留 uuid 与 publishTime" |

### 0.5 调试用的 curl 对照

把 UI 跑不通的请求复制到终端重放，能快速定位是网络还是客户端问题：

```bash
# initialize
curl -sS -X POST https://api-sc.wengegroup.com/search-mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'appkey: OOn05z7m' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"cli","version":"0.1"}}}'

# tools/list
curl -sS -X POST https://api-sc.wengegroup.com/sass-mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'appkey: OOn05z7m' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | jq '.result.tools | length'
```

预期返回：search-mcp = `3`，sass-mcp = `40+`。

---

## 1. 服务清单

| 名称 | URL | 协议 | 请求头 | 定位 |
| --- | --- | --- | --- | --- |
| 晴天信息检索 | `https://api-sc.wengegroup.com/search-mcp` | streamableHttp (JSON-RPC 2.0) | `appkey: OOn05z7m` | 互联网发文内容检索 |
| 晴天分析组件 | `https://api-sc.wengegroup.com/sass-mcp` | streamableHttp (JSON-RPC 2.0) | `appkey: OOn05z7m` | 舆情分析图表指标 |

两个服务均遵循 MCP 标准，可通过 `initialize` → `tools/list` → `tools/call` 调用。

## 2. search-mcp 工具集（信息检索，3 个）

| tool name | 用途 |
| --- | --- |
| `article-search` | 互联网发文内容检索（关键词、时间范围、渠道、情感等多维过滤），返回分页文章列表 |
| `article-detail` | 根据 `uuid + publishTime` 获取单篇文章完整详情（正文、互动数据、IP、图片等）|
| `media-account-search` | 按账号名 / 渠道 / 认证类型检索媒体账号信息 |

典型链路：`article-search` 取到候选文章 → 选取若干 `uuid+publishTime` → 逐条 `article-detail` 取全文 → 入库。

## 3. sass-mcp 工具集（舆情分析，40+ 个）

**基础面板类**
- `overview` — 舆情综合概述（渠道/情感/关键词一次拿齐）
- `info-overview` — 四项核心指标（信息总量、媒体总量、峰值传播量等）
- `interaction-overview` — 互动总量（点赞、评论、转发、阅读）

**传播趋势类**
- `exposure-trend` / `stage-envolution` / `interactive-trend` / `polarity-tendency`
- `channel-debut` — 渠道首发溯源

**榜单类**
- `hot-article` / `hot-theme` / `hot-phrases` / `hot-words` / `hot-person` / `hot-org`
- `active-account` / `active-website` / `get-hot-list`（各平台实时热搜）

**情感与观点**
- `emotional-distribution`（大模型驱动）/ `expert-opinion` / `weibo-netizens-view` / `generate-event-topic-info`

**渠道 / 媒体分布**
- `datasourceSound` / `media-volume-distributed` / `media-level-distributed`
- `certification-ratio` / `gender-distribution` / `writing-habits`

**地理 / 语种 / 国家**
- `geographic-distribution` / `mention-distribution` / `language` / `language-volume` / `trans-country-count`

**专项**
- `influence-indexation`（影响力指数）
- `hot-article-diffusion`（传播路径）
- `content-classification`（内容分类统计）
- `twitter-user-portrait`（Twitter 用户画像）
- `search-keyword-ai-optimization`（自然语言 → 搜索表达式）
- `search-scheme-tree`（查询已有舆情主题树）

> 完整入参 schema 已缓存在 `/tmp/search_tools.json`、`/tmp/sass_tools.json`，可在需要时重新拉取。

## 4. 在 ReportAI 中的使用策略

### 4.1 作为"知识库来源"（模块一）
新增"从晴天 MCP 抓取"入口：
1. 前端提供一个"订阅主题"表单（关键词、时间窗、渠道）；
2. 后端定时调 `article-search` 分页拉取 → 逐篇 `article-detail` 取全文；
3. Tika 解析结果直接走 knowledge-chunk 入库管道，与 PDF 上传产物同表存储。

### 4.2 作为"生成时的动态事实源"（模块二 / 三）
生成报告时，后端除本地 FULLTEXT 检索外，**并行**触发：
- `search-keyword-ai-optimization` 把主题翻成检索表达式；
- `article-search` 拉取最近 7 / 30 天热文作为补充 chunk；
- `hot-theme` / `hot-words` 作为"报告要点"候选；
- 把 MCP 返回的 snippet 和本地 chunk 合并排序后，再送给 Claude 生成。

### 4.3 作为"传播分析报告"的数据来源（模块三专项）
生成传播分析类报告时，按固定编排调用：

| 报告章节 | 依赖 MCP 工具 |
| --- | --- |
| 事件回顾 / 时间线 | `stage-envolution` + `channel-debut` |
| 声量 / 渠道 / 情感 | `overview` + `datasourceSound` + `emotional-distribution` |
| 关键节点与热文 | `hot-article` + `hot-article-diffusion` |
| 网民观点 / 专家观点 | `weibo-netizens-view` + `expert-opinion` |
| 媒体级别与认证 | `media-level-distributed` + `certification-ratio` |
| 地域 / 语种分布 | `geographic-distribution` + `language-volume` |
| 影响力评估 | `influence-indexation` + `info-overview` |
| 事件话题简介 | `generate-event-topic-info` |

### 4.4 政策影响 / 行业分析报告的辅助
- `search-keyword-ai-optimization` + `article-search`：抓取最新解读文；
- `content-classification`：判断关注点集中在哪个子领域；
- `hot-theme`：自动挖掘"热点梳理"章节的子话题。

## 5. 后端调用代码位置（规划）

```
report-hub-knowledge/
└── src/main/java/com/reportai/hub/knowledge/mcp/
    ├── QingTianMcpClient.java        # 封装 JSON-RPC + streamableHttp + appkey
    ├── SearchMcpService.java         # article-search / detail / account
    ├── SassMcpService.java           # 40+ 舆情指标（枚举 + 类型安全包装）
    └── dto/                          # 入参出参 POJO（根据 tools/list 的 schema 生成）
```

在 `application.yml` 中配置：

```yaml
qingtian:
  mcp:
    appkey: ${QINGTIAN_APPKEY:OOn05z7m}
    search-url: https://api-sc.wengegroup.com/search-mcp
    sass-url: https://api-sc.wengegroup.com/sass-mcp
    connect-timeout: 5s
    read-timeout: 30s
```

> appkey 先走环境变量注入，防止 commit 时硬编码进仓库。

## 6. 错误处理约束

- MCP 返回 `error` 时，只记录日志，不阻塞报告生成主流程——MCP 是增强而非必需；
- 超时默认 30s；超时则回退到纯本地 RAG；
- 对同一 `uuid+publishTime` 的文章详情做 Redis 缓存（TTL 24h），避免重复扣费。