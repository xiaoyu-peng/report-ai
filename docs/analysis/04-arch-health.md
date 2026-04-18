# 04 · 架构健康度 + 技术风险评估（Arch）

> 作者：Arch（AI 首席架构官）｜日期：2026-04-18｜面向：答辩技术问答 + v1.1 迭代
> 评估范围：`report-ai/backend` 5 个 Maven 模块、`database/init.sql`、`docker-compose.yml`、`application.yml`

---

## 1. 架构全景图

```text
                         浏览器 (Vue 3 + Element Plus + Pinia)
                                      │
                                      │  HTTP / SSE (text/event-stream)
                                      ▼
                    ┌──────────────────────────────────┐
                    │  前端容器  frontend:80 (宿主 3001)│
                    │     nginx 反向代理 /api/v1 →     │
                    └──────────────────┬───────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────┐
│  backend 容器  report-ai-backend:8080 (宿主 8081) Spring Boot 3.2.5│
│                                                                    │
│   report-hub-api (主入口, spring-boot-maven-plugin repackage)      │
│       │                                                            │
│       ├─► report-hub-system  (user / role / dept / log)            │
│       ├─► report-hub-knowledge (KB, Tika, RAG FULLTEXT)            │
│       ├─► report-hub-report  (gen / rewrite / version / diff)      │
│       │         └── depends on ↑ knowledge (RagSearchService)      │
│       └─► report-hub-common (Result, JWT, Security, LlmClient *)   │
│                                                                    │
│   SSE endpoint: GET /api/v1/reports/{id}/generate                  │
│   SseEmitter(300s) + newCachedThreadPool("report-sse-*")           │
└────────────┬──────────────────┬──────────────────┬─────────────────┘
             │ JDBC             │ Lettuce          │ HTTPS
             ▼                  ▼                  ▼
       MySQL 8.0           Redis 7          LLM Provider
       3307→3306           6380→6379        ┌──────────────┬────────────┐
       db=report_ai        (当前未使用)      │ Doubao/Ark   │ Claude     │
       innodb-ft-min-      appendonly yes   │ /chat/       │ /v1/       │
       token-size=1        未设 maxmemory   │ completions  │ messages   │
       FULLTEXT BOOLEAN                     │ (SDK 2.0.0)  │ (JDK http) │
                                            └──────────────┴────────────┘
                                            @ConditionalOnProperty 二选一
```

---

## 2. 依赖分层正确性

**结论：目前分层干净，无循环依赖。** 5 个模块依赖关系严格单向。

- **`common` 放 LlmClient：方向正确。** `LlmClient` / `LlmProperties` / `LlmAutoConfiguration` 是「跨领域的基础设施」，`report` 要用、未来 `knowledge`（自动抽风格摘要）也会用，下沉 common 避免了双向依赖。支撑证据：`report-hub-common/src/main/java/com/reportai/hub/common/llm/LlmClient.java:11`、`ClaudeLlmClient.java:32` 的 `@ConditionalOnProperty` 在装配期选 provider。
- **`report` 依赖 `knowledge`：合理。** 报告生成先检索再写，单向依赖符合「领域下游消费上游基础设施」的模式（见 `report-hub-report/pom.xml` 的 `<dependency>report-hub-knowledge</dependency>` + `ReportGenerationServiceImpl.java:39` 注入 `RagSearchService`）。反向依赖不存在。
- **可优化项：`common` 把 `spring-boot-starter-data-redis` / `knife4j` 都硬拽进来**，`report-hub-common/pom.xml:48-54,60-63`。这意味着以后任何纯 util 子模块都会多这 30+MB 的传递依赖。赛后可拆 `common-core` / `common-web` / `common-llm`，当前不动。
- **Tika 只在 knowledge 出现**，没有泄漏到 report / api，符合「文档解析属于知识领域」的边界（`report-hub-knowledge/pom.xml:17-24`）。

---

## 3. 技术风险清单 TOP 8

| # | 风险 | 触发条件 | 影响面 | 缓解方案 | 紧急度 |
|---|------|----------|--------|----------|--------|
| 1 | **SSE 超时与 nginx 缓冲**：`ReportStreamController.java:28` 的 `SSE_TIMEOUT_MS = 5*60*1000`，若模板篇幅长（5 种样例最大 5000 字）+ Claude 冷启动，单次生成可能 > 5min 断流；同时 nginx 默认 `proxy_buffering on` 会把 SSE 整段缓存。 | 长文生成 / 网络抖动 | 用户看到「卡住」、无法演示流式 | 把超时拉到 10min；`application.yml` 暂无，但 nginx 反代需加 `proxy_buffering off; proxy_read_timeout 600s; chunked_transfer_encoding on;`；前端加心跳 `:ping` 注释帧 | **P0** |
| 2 | **LLM provider 无熔断 / failover**：`LlmAutoConfiguration` 靠 `@ConditionalOnProperty` 在启动期二选一（`ClaudeLlmClient.java:32` / `DoubaoLlmClient.java:29`），一旦选中的 provider 500 或超时，`BusinessException` 直接抛到 SSE，不会自动切换到另一家。 | Claude 限流、豆包 endpoint 配额耗尽 | 演示当场失败 | 加 `CompositeLlmClient`：两个 Bean 同装、失败降级；或前端捕获 error 事件提示「切换 LLM」按钮 | **P0** |
| 3 | **缺少 tenant_id 过滤**：`database/init.sql:10,31,45,60` 都有 `tenant_id` 列，但 `knowledge_base` / `report` / `report_template` 表**没有 tenant_id**（`init.sql:85,133,149`），且 `KnowledgeChunkMapper.java:30` 的 FULLTEXT SQL 也只按 `kb_id` 过滤。多租户场景下会串数据。 | 未来接入多企业 | 数据越权 | 所有业务表补 `tenant_id`；MP 加 `TenantLineInnerInterceptor` | P1 |
| 4 | **大知识库下 FULLTEXT 延迟**：`innodb-ft-min-token-size=1` 让中文单字建索引，10w+ chunk 时 `ft_content` 索引可能 2GB+，单查询 P99 可到秒级；`RagSearchServiceImpl.java:56` 还把中文逐字切 token + 前缀 `+`，组合爆炸进一步放大。 | KB 文档 ≥ 5000 份 | RAG 延迟拖垮 SSE 首 token | top-k 降到 5；对 `kb_id` 建复合索引 `(kb_id, doc_id)`；query 里剪掉停用词「的 了 在」；赛后引 ES / 向量库 | P1 |
| 5 | **并发生成同一 report 的 version 冲突**：`init.sql:184` 有 `UNIQUE KEY uk_report_ver (report_id, version_num)`，但 `RewriteServiceImpl.java:39-50` 用「查最大 + 1」而**非数据库序列或悲观锁**，两个用户同时改写同一 report → `DuplicateKeyException` 炸 SSE。 | 并发改写同一报告 | 第二位用户看到 500 | 加 `@Transactional` + `SELECT ... FOR UPDATE`；或改用 `INSERT ... SELECT COALESCE(MAX(version_num),0)+1` 原子化 | P1 |
| 6 | **Redis 被引入却未使用**：`report-hub-common/pom.xml:48-52` 与 `report-hub-api/pom.xml` 都引入了 `spring-boot-starter-data-redis`，`application.yml:17-22` 也配好了，但整个代码库 0 个 `RedisTemplate` / `@Cacheable` 使用点（Grep 验证）。启动期会额外吃 ~30MB 内存、健康检查浪费。 | 默认启动 | 资源浪费，评委会追问「你 Redis 做什么了」 | 要么删依赖，要么给 RAG 结果加 5 分钟短 TTL 缓存（key=kbId+md5(query)）作为答辩亮点 | P1 |
| 7 | **密钥明文 & 无轮转**：`application.yml:44,52,59` 的 `JWT_SECRET` / `DOUBAO_API_KEY` / `ANTHROPIC_API_KEY` 全部走 env，`docker-compose.yml` 写死了 fallback 默认值（`report-ai-jwt-secret-key-2026-must-be-long-enough-for-hs256`）。JWT 过期 24h (`expiration: 86400000`)，一旦 secret 泄漏，24h 内所有签发 token 全部有效且无法吊销。 | secret 进了 git / 容器日志 | 全量用户账户可伪造 | secret 改为容器启动随机（docker secret）；给 JWT 加 `jti` + Redis 黑名单；提供 `/api/v1/auth/rotate-secret` 强制下线全量 session | P2 |
| 8 | **MySQL 容器用 root 连接**：`docker-compose.yml` 的 backend 环境 `SPRING_DATASOURCE_USERNAME: root`，而 `init.sql` 只创建了 `reportai` 账号并预留权限。以 root 跑服务 = 赛场评审扣「安全意识」分。 | 默认 compose up | 权限越界 + 扣分 | 改成 `reportai/reportai123`；对初始化脚本补 `GRANT SELECT,INSERT,UPDATE,DELETE ON report_ai.*` | P2 |

---

## 4. 可答辩亮点 TOP 5

1. **`@ConditionalOnProperty` 双 provider 热切换**（`ClaudeLlmClient.java:32` + `DoubaoLlmClient.java:29`）：改一个环境变量 `LLM_PROVIDER=claude|doubao` 即可零代码切换，业务层只面对 `LlmClient` 接口。评委问「为什么不用策略模式手动注册」可以回答：Spring 装配期确定，避免运行时分支开销与状态同步问题。
2. **LlmClient 下沉到 `common`（而非 `report`）**：把 LLM 看作与「日志、JWT、Redis」同级的基础设施，避免 knowledge 想做「风格摘要」时反向依赖 report 造成环。这是今天下午才完成的架构调整，有决策故事可讲。
3. **CJK FULLTEXT 三件套**：`innodb-ft-min-token-size=1`（docker-compose）+ 中文逐字切 token（`RagSearchServiceImpl.java:56-73`）+ BOOLEAN MODE `+term*`。解决了「MySQL 原生不支持中文分词」的顽疾，纯开源栈跑通 RAG，成本≈0。
4. **版本表 append-only + LCS 行级 diff**（`VersionServiceImpl.java:105-149`）：回滚用「复制一个新版本」而非覆盖（`restore` 方法 L72-101），天然可追溯；diff 用 LCS 再合并相邻 DELETE+INSERT 为 REPLACE（L151-169），前端只需画三种颜色。对标 Git 的子集实现，自研、可讲。
5. **SSE 专用线程池**（`ReportStreamController.java:33-37`）：`newCachedThreadPool` + daemon 命名 `report-sse-{nanoTime}`，不阻塞 Tomcat worker，生成任务异常时 `completeWithError` + `log.debug` 吞 IOException，保证客户端断连不污染日志。

---

## 5. v1.1 架构演进建议（按 ROI 排序）

1. **【赛时必做，1h】** SSE 心跳 + nginx 反代参数。修 ReportStreamController 每 15s 发 `:keepalive` 注释帧；前端 `frontend/nginx.conf` 加 `proxy_buffering off` / `proxy_read_timeout 600s`。解决风险 #1，**直接影响演示成败**。
2. **【赛时必做，2h】** `CompositeLlmClient` 做 failover。两个 Bean 同装，首选失败后自动降级并在 SSE 里推一条 `provider_switched` 事件。既堵风险 #2，又是答辩彩蛋。
3. **【ROI 高，3h】** Redis 做 RAG 查询缓存：key=`rag:{kbId}:{md5(query)}:{topK}`，value=JSON、TTL 300s。同一报告多轮改写时重复检索命中，SSE 首 token 时延下降 60%+，顺便让「为什么引 Redis」有答案。
4. **【中等优先，2h】** VersionMapper 加 `INSERT ... SELECT COALESCE(MAX(version_num),0)+1 FROM report_version WHERE report_id=?`，一条 SQL 原子生成下个版本号，堵风险 #5 的并发冲突。
5. **【可选，4h】** MP `TenantLineInnerInterceptor` + 给 `knowledge_base` / `report` 补 `tenant_id`。为 v1.1 的多租户演示铺路，赛时不接入，但答辩「未来规划」里提一嘴加分。

---

## 总结：最需要警惕的 1 个架构坑

> **SSE 生成流被 nginx 默认 `proxy_buffering on` 整段缓冲、外加 5 分钟 SseEmitter 超时双杀**——演示当场就可能出现「进度条卡住几十秒然后一次性吐完」甚至直接超时断流，直接毁掉「流式生成」这个核心卖点。上场前务必先压一次长文用例并把 nginx / SseEmitter 超时同时放宽到 10 分钟。
