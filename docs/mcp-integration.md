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

## 2. search-mcp 工具集（信息检索，共 **3** 个）

实测命中数量：`tools/list → 3`。以下为完整清单，**必填参数**已在列出，其余参数为可选过滤条件。

| # | Tool | 必填 | 用途 |
| --- | --- | --- | --- |
| 1 | `article-search` | `articleInfo`（内含 `searchMode`）| 互联网发文检索（关键词、时间、渠道、情感、内容匹配方式多维过滤），返回分页文章列表；支持简易 / 高级（布尔表达式）两种关键词模式 |
| 2 | `article-detail` | `uuid`, `publishTime` | 根据文章唯一 ID + 发布时间拉单篇全文与全部互动数据（ES 路由必须精确到秒）；支持 `isHighLight` 关键词高亮 |
| 3 | `media-account-search` | — | 按账号名 / 渠道 / 认证类型检索媒体账号（返回粉丝数、简介、认证、IP 属地等），**仅查账号主体信息，不查文章** |

**典型调用链**：`article-search`（拿 `uuid` + `publishTime`）→ `article-detail`（拿全文）→ Tika 解析 → 入 knowledge chunk 表。

## 3. sass-mcp 工具集（舆情分析，共 **41** 个）

实测命中数量：`tools/list → 41`。以下按**功能分组**给出全量清单；绝大多数工具共享一组 **"舆情主题 + 时间 + 关键词"过滤参数**（见 §3.0），个别工具有额外必填项单独标注。

### 3.0 通用过滤参数（几乎所有工具共用）

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `schemeId` | long | 舆情监测主题 ID（来自 `search-scheme-tree`）。和关键词二选一 |
| `startPubTime` / `endPubTime` | string `yyyy-MM-dd HH:mm:ss` | 时间窗 |
| `searchKeywordType` | 0 / 1 | 0 = 高级模式（走 `keywordExp`），1 = 简易模式（走 `mustKeyWord` / `anyKeyWord` / `needExclude`）|
| `mustKeyWord` / `anyKeyWord` / `needExclude` | string[] | 简易模式三组关键词 |
| `keywordExp` | string[] | 高级模式布尔表达式，如 `(华为\|小米)&手机` |
| `dataSource` | int[] | 渠道大类：微博 / 微信 / 新闻 / 论坛 / 视频 / 客户端等 |
| `articleType` | int | 文章类型：原创 / 转发 / 评论 |
| `isSimpleComplex` | boolean | 是否合并复杂搜索 |
| `realTime` | boolean | 实时数据 / 已入库数据 |

下面的 41 个工具表中 "**额外必填**" 一列只列在 §3.0 之外**特有**的必填项。

### 3.1 基础面板（3 个）

| # | Tool | 额外必填 | 用途 |
| --- | --- | --- | --- |
| 1 | `overview` | — | 舆情综合概述：**渠道分布 + 情感分布 + 关键词** 一次返回，做报告首屏 |
| 2 | `info-overview` | — | 四项核心指标：信息总量、媒体总量、峰值传播量、日均传播量 |
| 3 | `interaction-overview` | — | 互动总量：点赞 / 评论 / 转发 / 阅读 合计 |

### 3.2 传播趋势（5 个）

| # | Tool | 额外必填 | 用途 |
| --- | --- | --- | --- |
| 4 | `exposure-trend` | — | 媒体曝光趋势（支持按媒体类型 / 情感 / 发文类型堆叠），时间曲线 |
| 5 | `stage-envolution` | — | 事件阶段演化（萌芽 → 爆发 → 持续 → 消退），按 `dateInterval` 聚合 |
| 6 | `interactive-trend` | `interactiveType` | 按互动指标类型画趋势曲线。`interactiveType`：10=点赞 / 11=评论 / 12=阅读 / 15=转发 / 36=累加 |
| 7 | `polarity-tendency` | — | 情感倾向（正 / 中 / 负）随时间变化的时序曲线 |
| 8 | `channel-debut` | — | 渠道首发溯源：每个渠道下最早的原文，追踪首发来源 |

### 3.3 榜单（9 个）

| # | Tool | 额外必填 | 用途 |
| --- | --- | --- | --- |
| 9 | `hot-article` | — | 热文榜（按点赞 / 转发 / 评论 / 阅读 / 热度排序），返回完整元信息 |
| 10 | `hot-theme` | — | 热点主题聚类：抽出代表性话题句 + 关联文章 |
| 11 | `hot-phrases` | — | 热点短语（多词组合）TopN，适合短语云 |
| 12 | `hot-words` | — | 热点单词 TopN，适合词云 |
| 13 | `hot-person` | — | NLP 人名识别得出的热门人物榜 |
| 14 | `hot-org` | — | NLP 组织识别得出的热门机构榜 |
| 15 | `active-account` | — | 活跃账号榜（按发文量）|
| 16 | `active-website` | — | 活跃站点榜 |
| 17 | `get-hot-list` | `type`, `source` | **各平台实时 / 历史热搜榜**：抖音 `1`、快手 `2`、知乎 `5`、B 站 `8`、微博 `12`、头条 等 |

### 3.4 情感与观点（4 个）

| # | Tool | 额外必填 | 用途 |
| --- | --- | --- | --- |
| 18 | `emotional-distribution` | `updateFlag`, `comIndex` | **LLM 驱动**的情感 + 情绪分布（愤怒 / 悲伤 / 喜悦 等细粒度情绪）|
| 19 | `expert-opinion` | `sortInfo` | 专家观点榜（算法预抽取，含观点 / 热度 / 情感）|
| 20 | `weibo-netizens-view` | — | 微博等社交平台**普通网民**观点榜 |
| 21 | `generate-event-topic-info` | — | **LLM 自动生成**事件话题简介（可直接作为报告"事件概述"段） |

### 3.5 渠道 / 媒体分布（6 个）

| # | Tool | 额外必填 | 用途 |
| --- | --- | --- | --- |
| 22 | `datasourceSound` | — | 渠道声量饼图：微博 / 微信 / 新闻 / 论坛 / 视频 / 客户端比例 |
| 23 | `media-volume-distributed` | `mediaTagList` | 按媒体标签（中央 / 省级 / 地市 / 境外等）的发文量 |
| 24 | `media-level-distributed` | — | 媒体级别雷达图（中央 / 省级 / 境外重点 / 地市 / 商业门户）|
| 25 | `certification-ratio` | — | 账号认证类型分布（蓝 V / 黄 V / 机构 / 未认证）|
| 26 | `gender-distribution` | — | 发文账号性别分布 |
| 27 | `writing-habits` | — | 24 小时各时段发文量分布，揭示发文时间规律 |

### 3.6 地理 / 语种（5 个）

| # | Tool | 额外必填 | 用途 |
| --- | --- | --- | --- |
| 28 | `geographic-distribution` | `mapType`, `dataType` | **发布地域**分布：全球（国家）/ 境内（省）/ 省内（市级）|
| 29 | `mention-distribution` | `metricType`, `mapType`, `dataType` | **提及地域**分布：从正文中 NLP 抽取地名做频次统计 |
| 30 | `language` | — | 传播语种数量（去重后）|
| 31 | `language-volume` | — | 各语种发文量与占比 |
| 32 | `trans-country-count` | — | 传播覆盖国家数量，评估国际传播广度 |

### 3.7 专项 / 辅助（9 个）

| # | Tool | 额外必填 | 用途 |
| --- | --- | --- | --- |
| 33 | `influence-indexation` | — | 影响力指数：跨媒体互动指标归一化 + 对数综合计算 |
| 34 | `hot-article-diffusion` | `articlePublishTime`, `fingerprint` | 热文传播路径 / 相似文章网络 / 首发溯源 |
| 35 | `content-classification` | — | 内容分类统计：政治 / 经济 / 社会 / 科技 / 教育 / 文化 / 军事 / 体育… |
| 36 | `twitter-user-portrait` | `userInfos` | Twitter 账号用户画像 + 社交关系图谱（原创 / 转发 / 评论互动）|
| 37 | `search-keyword-ai-optimization` | `keyword` | **自然语言 → 布尔搜索表达式**。给 LLM 做关键词生成的第一步 |
| 38 | `search-scheme-tree` | — | 查询当前账号下已创建的舆情监测主题树（拿到 `schemeId`）|
| 39 | `search-infos` | — | 按条件返回信息列表（标题 / 正文 / 互动 / 情感 / IP 等，含分页）|
| 40 | `sound-proportion` | `schemeIdList` | 多主题声量占比对比分析 |
| 41 | `get-media-list` | `mediaAccountList` | 指定媒体账号列表的发文量 / 互动数据统计 |

> **完整入参 schema**（含每个字段的 description / 枚举值 / 子对象结构）已作为 JSON 快照入库：
> - `docs/mcp-schemas/search-mcp.tools.json`（3 个工具）
> - `docs/mcp-schemas/sass-mcp.tools.json`（41 个工具）
>
> 查阅命令和重新拉取方式见 [`docs/mcp-schemas/README.md`](./mcp-schemas/README.md)。

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