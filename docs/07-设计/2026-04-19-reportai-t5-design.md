# ReportAI T5 — 知识库驱动 + 以稿写稿 设计文档

- 日期：2026-04-19
- 范围：竞赛 T5 五大模块全部交付，A+C 战略（深耕评分项 + 5 个杀手锏交互全做）
- 作者：penghui × Claude
- 实施周期：2 天（Apr 19 ~ Apr 20），Apr 21 录屏与提交

---

## 1. 战略与评分对齐

竞赛评分权重：知识检索 25% + 报告生成 25% + 改写质量 25% + 对比/版本 15% + 体验 10%。本设计在保留现有 ReportAI（Vue3 + Spring Boot + pgvector + Doubao + SSE）的基础上，**增量改造** 而非重写。每个评分维度都对应一个"看得见"的产品差异化点。

### 5 个杀手锏交互（评分加成）

| 编号 | 交互 | 命中评分项 |
|---|---|---|
| ① | 修订视图 diff（词级三色 + 接受/拒绝） | 改写 25% + 对比 15% |
| ② | 引用溯源高亮（点 [n] 弹窗：文档+页码+原文片段+排除按钮） | 检索 25% |
| ③ | 段落级 AI 浮窗（Tiptap BubbleMenu：优化/续写/改数据/换风格） | 生成 25% + 体验 10% |
| ④ | 知识库覆盖度仪表盘（饼图 + 未覆盖段落列表 + 事实性可疑列表） | 生成 25% |
| ⑤ | 大纲拖拽 + 章节级独立流式生成（每章 pending/generating/done） | 生成 25% + 体验 10% |

---

## 2. 整体架构

### 2.1 复用与新增

**复用：** report-hub-{common,system,knowledge,report,api}、pgvector、Doubao LlmClient、Apache Tika、Apache POI、SSE 通道、ElementPlus。

**前端栈升级：**
- 工作台编辑器：Monaco → **Tiptap v2**（支持 BubbleMenu、自定义 Mark、引用脚注节点）
- Diff 渲染：新增 `diff-match-patch`（词级 diff，比 jsdiff 更精细）
- 大纲拖拽：新增 `vuedraggable@4`
- 图表：ECharts（覆盖度饼图）

### 2.2 新增 / 改动数据模型

```sql
-- ① 引用溯源表（核心）
CREATE TABLE report_citation (
  id              BIGSERIAL PRIMARY KEY,
  report_id       BIGINT NOT NULL,
  version_id      BIGINT,
  section_index   INT NOT NULL,
  paragraph_index INT NOT NULL,
  citation_marker INT NOT NULL,          -- 正文 [n] 编号
  chunk_id        BIGINT NOT NULL,
  doc_id          BIGINT NOT NULL,
  doc_title       VARCHAR(255),
  page_start      INT,
  page_end        INT,
  snippet         TEXT,
  accepted        BOOLEAN DEFAULT TRUE,  -- 用户排除后置 false
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_report (report_id, version_id)
);

-- ② 章节流式生成状态表
CREATE TABLE report_section (
  id            BIGSERIAL PRIMARY KEY,
  report_id     BIGINT NOT NULL,
  section_index INT NOT NULL,
  title         VARCHAR(255),
  prompt        TEXT,
  status        VARCHAR(20) DEFAULT 'pending', -- pending/generating/done/failed
  content       TEXT,
  word_count    INT DEFAULT 0,
  citation_count INT DEFAULT 0,
  started_at    TIMESTAMP, finished_at TIMESTAMP,
  UNIQUE (report_id, section_index)
);

-- ③ 用户排除的引用块
CREATE TABLE report_excluded_chunk (
  report_id BIGINT NOT NULL,
  chunk_id  BIGINT NOT NULL,
  PRIMARY KEY (report_id, chunk_id)
);

-- ④ 报告质量检查结果
CREATE TABLE report_quality (
  report_id           BIGINT PRIMARY KEY,
  coverage_rate       DECIMAL(5,2),  -- 0~100
  citations_total     INT,
  paragraphs_total    INT,
  paragraphs_cited    INT,
  kb_distribution     JSONB,         -- {"行业报告": 12, "政策法规": 4}
  suspicious_facts    JSONB,         -- [{"text":"35%","reason":"...","severity":"warn"}]
  checked_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ⑤ KnowledgeChunk 增加段落定位
ALTER TABLE knowledge_chunk ADD COLUMN paragraph_index INT;

-- ⑥ ReportTemplate 增加可复用大纲
ALTER TABLE report_template ADD COLUMN outline_json JSONB;
```

---

## 3. 模块一：知识库构建（已基本完成，补强项）

| 现状 | 补强 |
|---|---|
| 4 分类 + 19 URL 已入库 | 补「政策法规/行业报告/历史报告/媒体资讯」缺口分类 |
| Tika 解析 + pgvector | **重新入库**所有文档：分块时记录 `paragraph_index`（按 `\n\n` 段落切分，再按字数滑窗） |
| 增删改查已有 | 列表页加「重新嵌入」按钮（重跑分块+embedding） |

---

## 4. 模块二：知识检索与引用（25%）

### 4.1 混合检索

```
输入：query, kb_ids[], top_k=12, include_kw[], exclude_kw[], excluded_chunk_ids[]
步骤：
  a. 向量召回（pgvector cosine, top_k×3）
  b. BM25 召回（pg_trgm or LIKE %query% 简化版, top_k×3）
  c. 过滤 excluded_chunk_ids
  d. 关键词过滤：必须含 include_kw（任一），不含 exclude_kw（任一）
  e. 融合分数：final = 0.7 * vector_score + 0.3 * bm25_score
  f. 取 top_k 返回（含 highlightSpans）
```

接口签名（**新增 / 改造**）：

```java
// RagSearchService
RagSearchResponse search(RagSearchQuery q);  // q 持有所有参数

// dto.RagSearchQuery
Long reportId;            // 用于过滤 excluded_chunk_ids
List<Long> kbIds;
String query;
int topK;
List<String> includeKeywords;
List<String> excludeKeywords;
```

### 4.2 引用埋点（生成时）

Doubao 生成 prompt 末尾追加：

```
请在每个事实性陈述句末尾标注引用，格式为 [CITE:chunk_id1,chunk_id2]，
仅可引用以下检索结果，不得编造 chunk_id：
{{#hits}}
- chunk_id={{chunkId}} | {{docTitle}} P{{pageStart}}-{{pageEnd}} | {{snippet}}
{{/hits}}
```

后端 `CitationParser`：流式收尾后正则 `\[CITE:([\d,]+)\]` → 替换为 `[1][2]` → 写 `report_citation`。

### 4.3 前端 CitationPopover

- 报告 HTML 渲染时把 `[1]` 包成 `<sup data-citation-id="1" class="citation-marker">[1]</sup>`
- hover/click 触发 ElementPlus `el-popover` 显示卡片
- 卡片字段：`docTitle / 第 12-13 页 / 第 4 段 / snippet（关键词蓝色）`
- 「打开原文」→ 跳到知识库文档详情页并锚定段落
- 「排除此引用」→ POST `/reports/{id}/citations/{markerId}/exclude` → 重新生成该段

---

## 5. 模块三：AI 报告生成（25%）

### 5.1 大纲拖拽（交互 ⑤）

`OutlineEditor.vue`：

```
[+] 章节1：行业概况              [字数] 800  [参考资料] 4 块  [⋮]
[+] 章节2：技术演进              [字数] 1200 [参考资料] 6 块  [⋮]
  └ [+] 2.1 关键技术路线
  └ [+] 2.2 专利分布
[+] 章节3：政策环境              [字数] 600  [参考资料] 3 块  [⋮]
```

- vuedraggable 支持拖拽排序、嵌套
- 「保存为模板」→ 写 `report_template.outline_json`
- 「从模板载入」→ 下拉选

### 5.2 章节级独立流式（交互 ⑤）

```
POST /api/v1/reports/{id}/generate
  → 拆 outline 为 N 个 report_section 行，status=pending
  → 立即返回 {jobId, sections: [...]}

EventSource /api/v1/reports/{id}/sections/{idx}/stream
  事件流：
    {type:"start"}
    {type:"token", text:"...."}
    {type:"citation", marker:1, chunkId:4521, docTitle:"...", pageStart:12}
    {type:"done", wordCount:800, citationCount:5}
```

前端 `SectionStreamView.vue`：每章独立 `<el-card>` + 进度条 + 重试按钮。失败章节红色边框，点 retry 重发 EventSource。

### 5.3 段落级 AI 浮窗（交互 ③）

Tiptap `BubbleMenu` 在选中段落时浮起 4 个图标按钮：

| 按钮 | 触发后端 | UX |
|---|---|---|
| ✨ 优化 | `RewriteService.streamRewriteSection(mode=POLISH)` | 行内 ghost text 流式渲染，回车确认替换 |
| ➕ 续写 | `RewriteService.streamRewriteSection(mode=CONTINUE)` | 在选中段落末尾追加 |
| 🔢 改数据 | `RewriteService.streamRewriteSection(mode=DATA_UPDATE)` | 弹窗让用户选 KB 范围，然后改写 |
| 🎭 换风格 | `RewriteService.streamRewriteSection(mode=STYLE_SWITCH)` | 弹窗选「正式/通俗/中/英」 |

### 5.4 报告质量检查 + 覆盖度仪表盘（交互 ④）

`QualityCheckJob`（生成完成时异步触发）：

```
1. 统计 paragraphs_total = 解析报告 markdown 段落数
2. 统计 paragraphs_cited = report_citation 表里 distinct (section, paragraph)
3. coverage_rate = paragraphs_cited / paragraphs_total * 100
4. kb_distribution = group by doc.kb_id count(citation)
5. suspicious_facts = 抽报告里的数字/日期实体 → 让 Doubao 跟来源 chunk snippet 校验，输出 JSON
6. 写 report_quality 表
```

`CoverageDashboard.vue`：

- 顶部 3 大数字卡：覆盖率 / 引用总数 / 来源文档数
- 中部 ECharts 饼图：知识库命中分布
- 底部两栏：未引用段落列表（点击跳转） + 事实性可疑列表

---

## 6. 模块四：以稿写稿 + 改写（25%）

### 6.1 4 模式真区分

| 模式 | 预处理 | Prompt 关键约束 |
|---|---|---|
| `DATA_UPDATE` | LLM 抽取原稿数字/日期/实体 → KB 检索新值 → 生成 old→new 映射 | 严格保持段落结构、句式、修辞，仅替换数据点；输出 unified diff 注释每个改动 |
| `PERSPECTIVE_SHIFT` | 弹窗选 persona（领导简报 / 记者深度 / 学生科普 / 外宾英文） | 保持事实，重写视角与受众语气，长度可压缩 30% |
| `CONTENT_EXPAND` | LLM 提议 2~3 个新章节 → 用户勾选 → RAG 检索素材 | 在原稿基础上 **追加** 新章节，不改原文 |
| `STYLE_SWITCH` | 弹窗选「正式↔通俗 / 中↔英」 | 保持信息量，仅改语气/语种 |

### 6.2 AI 续写

`POST /api/v1/reports/{id}/continue` body `{newSectionTitle, hint}` → 把原稿全文注入 system prompt，流式输出新章节，落到 report_section 表 `section_index = max + 1`。

### 6.3 修订视图 diff（交互 ①）

`DiffView.vue`：

- 左右两栏（旧版 / 新版） + 底栏统一 diff
- `diff-match-patch` 词级 diff
- 三色：新增绿底、删除红底删除线、修改黄底
- 每段右侧 `[✓ 接受] [✗ 拒绝]` → 拒绝则该段回滚原文
- 顶部「全部接受 / 全部拒绝」批量

---

## 7. 模块五：版本管理与导出

### 7.1 版本树视图

复用 `ReportVersion` 表，新组件 `VersionTimeline.vue`：

- 左侧：版本卡片列表（含 mode 标签、字数、时间、操作人）
- 右侧：选中两版本 → DiffView 对比
- 操作：回滚到此版本

### 7.2 导出

- DOCX：`DocxExportServiceImpl` 已有，**升级**：用 `XWPFFootnote` 把 [n] 替换为 Word 真脚注
- PDF：调用宿主 LibreOffice headless `soffice --headless --convert-to pdf`（CI/Docker 容易部署）

---

## 8. 演示交付清单

### 8.1 数据 seed（`scripts/demo-seed.sh`）

- 4 个知识库（政策法规 / 行业报告 / 历史报告 / 媒体资讯），合计 ≥ 30 篇文档
- 3 篇参考报告（来自 `docs/以稿写稿-报告样例/`）入「历史报告」KB

### 8.2 生成演示（3 篇）

| 主题 | 主用 KB | 字数目标 |
|---|---|---|
| 2024 新能源汽车产业洞察 | 行业报告 + 媒体资讯 | 3500 |
| ChatGPT 对教育行业影响政策建议 | 政策法规 + 媒体资讯 | 3000 |
| 2024 Q3 消费电子市场月报 | 行业报告 | 2500 |

### 8.3 改写演示（3 ×4 = 12）

3 篇参考报告 × 4 模式 = 12 个改写产物，每个保存独立 ReportVersion。

### 8.4 续写演示

「政策影响报告」续写「实施路径与挑战」新章节。

### 8.5 录屏脚本（5 段，每段 1~2 分钟）

1. 知识库管理：上传 PDF → 重新嵌入 → 增删改
2. 报告生成：填表 → 大纲拖拽 → 章节流式 → 引用 popover → 覆盖度仪表盘
3. 改写：4 模式各演示一次 + 修订视图 diff
4. 续写 + 段落浮窗
5. 版本对比 + DOCX/PDF 导出

---

## 9. 时间表

| 时段 | 内容 | 验收 |
|---|---|---|
| **Day1 上午**（4 月 19 下午–晚） | DB 迁移（3 张新表 + 2 alter） + chunk 段落定位重新入库 + 混合检索 | curl 检索接口返回含 paragraphIndex 和 highlightSpans |
| **Day1 晚** | 引用埋点 + CitationParser + report_citation 写库 | 生成测试报告，DB 里能看到 citation 行 |
| **Day2 上午** | Tiptap 接入工作台 + CitationPopover + ParagraphAiToolbar | Tiptap 渲染报告，hover [n] 出弹窗 |
| **Day2 中午** | OutlineEditor 拖拽 + 章节级 SSE | 大纲拖拽工作，每章独立流式 |
| **Day2 下午** | 4 改写模式 prompt + DiffView + 续写 | 4 模式产物可见区分度 |
| **Day2 晚** | 覆盖度仪表盘 + 事实性检查 + 版本树 | 仪表盘有数据 |
| **Apr 21 上午** | 演示 seed 跑 3+12+1 → DOCX/PDF 导出脚注 | 12 改写版本入库 |
| **Apr 21 下午** | **webapp-testing Chrome 端到端验证** + 录屏 + 提交 | 全流程 Playwright 跑通 0 错误 |

---

## 10. 验收标准（Definition of Done）

1. 5 模块全部可用，全流程 Playwright 跑通无 console 错误
2. 至少 3 篇生成报告 + 12 改写版本 + 1 续写产物入库
3. 引用 popover 能正确显示 docTitle / pageRange / snippet
4. 修订 diff 三色高亮工作，「接受/拒绝」单段生效
5. 覆盖度仪表盘有真实数据（覆盖率 ≥ 60%）
6. DOCX 导出含 Word 真脚注；PDF 导出可打开
7. 录屏 5 段完整覆盖评分 5 维度
8. **必须** 用 webapp-testing 在 Chrome 跑端到端验证后再交付

---

## 11. 风险与回退

| 风险 | 回退 |
|---|---|
| Tiptap 接入复杂超时 | 段落浮窗降级为按钮列表（非 BubbleMenu） |
| 段落级 SSE 多通道并发问题 | 降级为顺序生成（一章完再下一章） |
| LibreOffice PDF 导出环境依赖 | 降级为前端 jsPDF 截图导出 |
| Doubao 不稳输出 [CITE:...] | 改为后端二次召回比对 + 自动注入引用 |

---

## 12. 交付资产清单（与赛题硬性要求一一对应）

| 赛题要求 | 交付物位置 | 备注 |
| --- | --- | --- |
| ① 可直接打开使用的完整 Web 系统（5 模块） | `report-ai/` 全栈 + `report-ai/start.sh` | `cp .env.example .env` → 填 key → `./start.sh --seed`；首页 admin/admin123；详见 `report-ai/README.md` |
| ② 3 份不同主题的完整报告（含引用来源标注） | DB `report` 表，由 `scripts/demo-deliverables.sh` 末尾打印 id；前端「报告库」直接看 | 主题：政策影响（AI+制造）/ 传播分析（五一出行）/ 行业分析（2026 中国 AI），均带 RAG 引用 `[n]` |
| ③ 3 篇参考报告 × 4 种改写模式 | 上述 3 份各跑 4 模式（DATA_UPDATE / ANGLE_SHIFT / EXPAND / STYLE_SHIFT），每次产出新 `report_version` | 共 3 × 4 = 12 个 rewrite 版本 |
| ④ ≥1 个续写演示（从参考稿续写新章节） | 首份报告额外 1 个 CONTINUATION 版本（"风险与对策"） | 续写不替换原稿，追加在末尾 |
| ⑤ 改写前后对比展示（修订模式视图） | 报告详情页「版本对比」tab，三色词级 diff（绿增 / 红删 / 黄改） | 任选 v1↔v2 演示 |
| ⑥ 设计说明文档 + AI 工具使用日志 | 本文件 + `docs/06-审计与演示/AI工具使用日志.md` | 现场脚本：`docs/06-审计与演示/2026-04-20-现场演示脚本.md` |

> 交付数据复现命令：在 `report-ai/` 目录执行 `bash scripts/demo-deliverables.sh`，约 15 分钟生成 3 份报告 × 5 个版本 + 1 续写。
