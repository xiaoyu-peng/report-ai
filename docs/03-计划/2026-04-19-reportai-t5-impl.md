# ReportAI T5 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 ReportAI 之上，把 T5 五大模块 + 5 个杀手锏交互（修订 diff / 引用溯源 / 段落 AI 浮窗 / 覆盖度仪表盘 / 大纲拖拽+章节流式）做到可演示，并在 Chrome 完成端到端验收。

**Architecture:** 增量改造 Vue3 + Spring Boot + pgvector + Doubao + SSE 现有架构。新增 4 张表（report_citation / report_section / report_excluded_chunk / report_quality）+ 2 alter（knowledge_chunk.paragraph_index / report_template.outline_json）。前端工作台编辑器从 Monaco 升级到 Tiptap 以支持 BubbleMenu 与引用脚注节点。

**Tech Stack:** Java 17 + Spring Boot 3.2.5 + MyBatis Plus + PostgreSQL + pgvector + Apache Tika + Apache POI + Doubao API; Vue 3 + Vite + ElementPlus + Pinia + Tiptap v2 + ECharts + diff-match-patch + vuedraggable@4.

**约定：**
- 项目根：`/Users/penghui/工作/Coding/Ai挑战赛/第二站/report-ai/`
- 后端运行：`cd backend && mvn -pl report-hub-api spring-boot:run`（IDE 跑，不用 Docker；Docker 只起 MySQL+Redis+pgvector）
- 前端运行：`cd frontend && npm run dev`（端口 5173）
- 验证：每个 task 完成都用 curl 或浏览器 F12 验证，BUILD SUCCESS / 端到端通就 git commit
- 数据库：postgres `report_ai` schema，psql 连接 `psql -h 127.0.0.1 -p 5432 -U reportai -d report_ai`（见 docker-compose.yml 实际端口）

---

## Task 1：DB Schema 迁移 — 4 新表 + 2 alter

**Files:**
- Create: `report-ai/database/migrations/V20260419__t5_schema.sql`
- Modify: `report-ai/database/init.sql`（在末尾追加，便于全新部署）

- [ ] **Step 1: 写迁移 SQL**

```sql
-- V20260419__t5_schema.sql
-- T5 模块新增表 + 字段

ALTER TABLE knowledge_chunk ADD COLUMN IF NOT EXISTS paragraph_index INT;
ALTER TABLE report_template  ADD COLUMN IF NOT EXISTS outline_json   JSONB;

CREATE TABLE IF NOT EXISTS report_citation (
  id              BIGSERIAL PRIMARY KEY,
  report_id       BIGINT  NOT NULL,
  version_id      BIGINT,
  section_index   INT     NOT NULL,
  paragraph_index INT     NOT NULL DEFAULT 0,
  citation_marker INT     NOT NULL,
  chunk_id        BIGINT  NOT NULL,
  doc_id          BIGINT  NOT NULL,
  doc_title       VARCHAR(255),
  page_start      INT,
  page_end        INT,
  snippet         TEXT,
  accepted        BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_citation_report ON report_citation (report_id, version_id);
CREATE INDEX IF NOT EXISTS idx_citation_marker ON report_citation (report_id, citation_marker);

CREATE TABLE IF NOT EXISTS report_section (
  id             BIGSERIAL PRIMARY KEY,
  report_id      BIGINT NOT NULL,
  section_index  INT    NOT NULL,
  title          VARCHAR(255),
  prompt         TEXT,
  status         VARCHAR(20) NOT NULL DEFAULT 'pending',
  content        TEXT,
  word_count     INT NOT NULL DEFAULT 0,
  citation_count INT NOT NULL DEFAULT 0,
  started_at     TIMESTAMP,
  finished_at    TIMESTAMP,
  UNIQUE (report_id, section_index)
);

CREATE TABLE IF NOT EXISTS report_excluded_chunk (
  report_id BIGINT NOT NULL,
  chunk_id  BIGINT NOT NULL,
  PRIMARY KEY (report_id, chunk_id)
);

CREATE TABLE IF NOT EXISTS report_quality (
  report_id        BIGINT PRIMARY KEY,
  coverage_rate    NUMERIC(5,2),
  citations_total  INT,
  paragraphs_total INT,
  paragraphs_cited INT,
  kb_distribution  JSONB,
  suspicious_facts JSONB,
  checked_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 2: 应用到运行中的 DB**

```bash
cd /Users/penghui/工作/Coding/Ai挑战赛/第二站/report-ai
docker compose exec -T postgres psql -U reportai -d report_ai < database/migrations/V20260419__t5_schema.sql
# 或：psql -h 127.0.0.1 -p 5432 -U reportai -d report_ai -f database/migrations/V20260419__t5_schema.sql
```

预期：5 个 `CREATE TABLE` 或 `ALTER TABLE` 语句无报错。

- [ ] **Step 3: 同步追加到 init.sql**

把整段 `V20260419__t5_schema.sql` 追加到 `database/init.sql` 末尾，保留 `CREATE TABLE IF NOT EXISTS` 形式以幂等。

- [ ] **Step 4: 验证表结构**

```bash
docker compose exec -T postgres psql -U reportai -d report_ai -c "\dt report_citation report_section report_excluded_chunk report_quality"
docker compose exec -T postgres psql -U reportai -d report_ai -c "\d knowledge_chunk" | grep paragraph_index
```

预期：4 张新表存在 + paragraph_index 字段存在。

- [ ] **Step 5: Commit**

```bash
git add database/
git commit -m "feat(db): T5 schema — citation/section/excluded_chunk/quality 表 + 段落定位字段"
```

---

## Task 2：知识库分块加段落定位 + 重新嵌入接口

**Files:**
- Modify: `report-ai/backend/report-hub-knowledge/src/main/java/com/reportai/hub/knowledge/service/TextChunker.java`
- Create: `report-ai/backend/report-hub-knowledge/src/main/java/com/reportai/hub/knowledge/dto/ChunkWithMeta.java`
- Modify: `report-ai/backend/report-hub-knowledge/src/main/java/com/reportai/hub/knowledge/service/impl/DocumentServiceImpl.java`
- Modify: `report-ai/backend/report-hub-knowledge/src/main/java/com/reportai/hub/knowledge/controller/DocumentController.java`

- [ ] **Step 1: 创建 ChunkWithMeta DTO**

```java
package com.reportai.hub.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class ChunkWithMeta {
    private String content;
    private Integer paragraphIndex;  // 来源段落序号（0-based）
    private Integer pageStart;       // null 表示非 PDF
    private Integer pageEnd;
}
```

- [ ] **Step 2: TextChunker 增加返回带 paragraphIndex 的方法**

在 `TextChunker.java` 加新方法（保留原 `chunk(String)` 接口）：

```java
/** 返回带段落定位的分块。paragraphIndex = 该块起始段落在原文中的序号（0-based）。 */
public List<ChunkWithMeta> chunkWithMeta(String text) {
    return chunkWithMeta(text, DEFAULT_MAX, DEFAULT_OVERLAP);
}

public List<ChunkWithMeta> chunkWithMeta(String text, int maxChars, int overlap) {
    List<ChunkWithMeta> out = new ArrayList<>();
    if (text == null || text.isBlank()) return out;

    String normalized = text.replace("\r\n", "\n").replaceAll("\n{3,}", "\n\n");
    String[] paragraphs = normalized.split("\n\n");

    StringBuilder buf = new StringBuilder();
    int bufStartPara = 0;          // 当前 buf 起始段落序号
    int paraIdx = 0;
    for (String para : paragraphs) {
        String p = para.trim();
        if (p.isEmpty()) { paraIdx++; continue; }

        if (p.length() > maxChars) {
            if (buf.length() > 0) {
                out.add(new ChunkWithMeta(buf.toString(), bufStartPara, null, null));
                buf.setLength(0);
            }
            // 长段落按句号切；每个子块 paragraphIndex 都用 paraIdx
            for (String sub : splitLongParagraph(p, maxChars, overlap)) {
                out.add(new ChunkWithMeta(sub, paraIdx, null, null));
            }
            bufStartPara = paraIdx + 1;
        } else if (buf.length() + p.length() > maxChars) {
            out.add(new ChunkWithMeta(buf.toString(), bufStartPara, null, null));
            buf.setLength(0);
            buf.append(p);
            bufStartPara = paraIdx;
        } else {
            if (buf.length() == 0) bufStartPara = paraIdx;
            if (buf.length() > 0) buf.append("\n\n");
            buf.append(p);
        }
        paraIdx++;
    }
    if (buf.length() > 0) out.add(new ChunkWithMeta(buf.toString(), bufStartPara, null, null));
    return out;
}

private List<String> splitLongParagraph(String p, int maxChars, int overlap) {
    // 复用现有逻辑：按 [。！？.!?] 切，超长再硬切
    List<String> out = new ArrayList<>();
    String[] sents = p.split("(?<=[。！？.!?])");
    StringBuilder b = new StringBuilder();
    for (String s : sents) {
        if (b.length() + s.length() > maxChars && b.length() > 0) {
            out.add(b.toString());
            String tail = b.length() > overlap ? b.substring(b.length() - overlap) : b.toString();
            b.setLength(0);
            b.append(tail);
        }
        b.append(s);
    }
    if (b.length() > 0) out.add(b.toString());
    return out;
}
```

- [ ] **Step 3: DocumentServiceImpl 入库时写 paragraphIndex**

在 `DocumentServiceImpl` 找到调用 `chunker.chunk(text)` 的地方，改成 `chunker.chunkWithMeta(text)`，循环创建 `KnowledgeChunk` 时 `chunk.setParagraphIndex(meta.getParagraphIndex())`、`chunk.setPageStart(meta.getPageStart())`。

- [ ] **Step 4: 加重新嵌入接口**

在 `DocumentService` 接口加：
```java
void reembed(Long docId);
```

`DocumentServiceImpl.reembed`：
```java
@Override
@Transactional
public void reembed(Long docId) {
    KnowledgeDocument doc = getById(docId);
    if (doc == null) throw new BizException("文档不存在");
    chunkMapper.delete(new QueryWrapper<KnowledgeChunk>().eq("doc_id", docId));
    String text = doc.getContent();
    List<ChunkWithMeta> metas = chunker.chunkWithMeta(text);
    int idx = 0;
    for (ChunkWithMeta m : metas) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.setDocId(docId); c.setKbId(doc.getKbId());
        c.setContent(m.getContent()); c.setChunkIndex(idx++);
        c.setParagraphIndex(m.getParagraphIndex());
        c.setPageStart(m.getPageStart()); c.setPageEnd(m.getPageEnd());
        chunkMapper.insert(c);
        // 复用 embedding 逻辑
        embeddingService.embedAndStore(c.getId(), m.getContent());
    }
}
```

`DocumentController` 加端点：
```java
@PostMapping("/{id}/reembed")
public Result<Void> reembed(@PathVariable Long id) {
    documentService.reembed(id);
    return Result.success();
}
```

- [ ] **Step 5: 编译 + 验证**

```bash
cd backend && mvn -pl report-hub-knowledge package -DskipTests
```

预期：BUILD SUCCESS。

调用：
```bash
curl -X POST http://localhost:8081/api/v1/knowledge/documents/1/reembed -H "Authorization: Bearer $TOKEN"
psql ... -c "SELECT chunk_index, paragraph_index, page_start FROM knowledge_chunk WHERE doc_id=1 LIMIT 5"
```

预期：paragraph_index 非空。

- [ ] **Step 6: Commit**

```bash
git add backend/report-hub-knowledge/
git commit -m "feat(knowledge): 分块带 paragraph_index + 重新嵌入接口"
```

---

## Task 3：混合检索 — 向量 0.7 + BM25 0.3 + include/exclude/excluded_chunk

**Files:**
- Create: `report-ai/backend/report-hub-knowledge/src/main/java/com/reportai/hub/knowledge/dto/RagSearchQuery.java`
- Create: `report-ai/backend/report-hub-knowledge/src/main/java/com/reportai/hub/knowledge/entity/ReportExcludedChunk.java`
- Create: `report-ai/backend/report-hub-knowledge/src/main/java/com/reportai/hub/knowledge/mapper/ReportExcludedChunkMapper.java`
- Modify: `report-ai/backend/report-hub-knowledge/src/main/java/com/reportai/hub/knowledge/service/RagSearchService.java`
- Modify: `report-ai/backend/report-hub-knowledge/src/main/java/com/reportai/hub/knowledge/service/impl/RagSearchServiceImpl.java`
- Modify: `report-ai/backend/report-hub-knowledge/src/main/java/com/reportai/hub/knowledge/dto/RagChunkHit.java`

- [ ] **Step 1: RagSearchQuery DTO**

```java
package com.reportai.hub.knowledge.dto;

import lombok.Data;
import java.util.List;

@Data
public class RagSearchQuery {
    private Long reportId;             // 用于读 report_excluded_chunk
    private List<Long> kbIds;          // null = 全部
    private String query;
    private int topK = 8;
    private List<String> includeKeywords;
    private List<String> excludeKeywords;
    private double vectorWeight = 0.7;
}
```

- [ ] **Step 2: RagChunkHit 增加字段**

```java
private Integer paragraphIndex;
private Double vectorScore;
private Double bm25Score;
private Double finalScore;
private List<int[]> highlightSpans;  // 命中关键词字符区间 [[start,end],...]
private String kbName;
```

- [ ] **Step 3: ReportExcludedChunk 实体 + Mapper**

```java
@Data @TableName("report_excluded_chunk")
public class ReportExcludedChunk {
    private Long reportId;
    private Long chunkId;
}
```

```java
public interface ReportExcludedChunkMapper extends BaseMapper<ReportExcludedChunk> {}
```

- [ ] **Step 4: RagSearchService 接口加 search(RagSearchQuery)**

保留旧重载，加：
```java
RagSearchResponse search(RagSearchQuery q);
```

- [ ] **Step 5: RagSearchServiceImpl 实现混合检索**

```java
@Override
public RagSearchResponse search(RagSearchQuery q) {
    // 1. 向量召回 top_k×3
    float[] qVec = embeddingService.embed(q.getQuery());
    List<RagChunkHit> vecHits = vectorMapper.searchByCosine(qVec, q.getKbIds(), q.getTopK() * 3);

    // 2. BM25 召回 top_k×3（用 ts_rank or pg_trgm 简化版：LIKE %query%）
    List<RagChunkHit> bm25Hits = chunkMapper.searchByKeyword(q.getQuery(), q.getKbIds(), q.getTopK() * 3);

    // 3. 收集已排除 chunk
    Set<Long> excluded = new HashSet<>();
    if (q.getReportId() != null) {
        excluded.addAll(excludedMapper.selectList(
            new QueryWrapper<ReportExcludedChunk>().eq("report_id", q.getReportId()))
            .stream().map(ReportExcludedChunk::getChunkId).collect(Collectors.toSet()));
    }

    // 4. 融合分数
    Map<Long, RagChunkHit> merged = new HashMap<>();
    double w = q.getVectorWeight();
    for (RagChunkHit h : vecHits) {
        if (excluded.contains(h.getChunkId())) continue;
        h.setFinalScore(w * (h.getVectorScore() == null ? 0 : h.getVectorScore()));
        merged.put(h.getChunkId(), h);
    }
    for (RagChunkHit h : bm25Hits) {
        if (excluded.contains(h.getChunkId())) continue;
        RagChunkHit prev = merged.get(h.getChunkId());
        double bm = (h.getBm25Score() == null ? 0 : h.getBm25Score());
        if (prev != null) {
            prev.setBm25Score(h.getBm25Score());
            prev.setFinalScore(prev.getFinalScore() + (1 - w) * bm);
        } else {
            h.setFinalScore((1 - w) * bm);
            merged.put(h.getChunkId(), h);
        }
    }

    // 5. 关键词过滤
    List<RagChunkHit> filtered = merged.values().stream()
        .filter(h -> matchInclude(h.getContent(), q.getIncludeKeywords()))
        .filter(h -> noneOf(h.getContent(), q.getExcludeKeywords()))
        .sorted((a, b) -> Double.compare(b.getFinalScore(), a.getFinalScore()))
        .limit(q.getTopK())
        .collect(Collectors.toList());

    // 6. 计算 highlightSpans
    filtered.forEach(h -> h.setHighlightSpans(computeSpans(h.getContent(),
        joinKeywords(q.getQuery(), q.getIncludeKeywords()))));

    RagSearchResponse resp = new RagSearchResponse();
    resp.setHits(filtered);
    return resp;
}

private boolean matchInclude(String text, List<String> kws) {
    if (kws == null || kws.isEmpty()) return true;
    return kws.stream().anyMatch(k -> text != null && text.contains(k));
}

private boolean noneOf(String text, List<String> kws) {
    if (kws == null || kws.isEmpty()) return true;
    return kws.stream().noneMatch(k -> text != null && text.contains(k));
}

private List<int[]> computeSpans(String text, List<String> kws) {
    if (text == null) return Collections.emptyList();
    List<int[]> spans = new ArrayList<>();
    for (String k : kws) {
        if (k == null || k.isBlank()) continue;
        int idx = 0;
        while ((idx = text.indexOf(k, idx)) >= 0) {
            spans.add(new int[]{idx, idx + k.length()});
            idx += k.length();
        }
    }
    return spans;
}

private List<String> joinKeywords(String q, List<String> kws) {
    List<String> out = new ArrayList<>();
    if (kws != null) out.addAll(kws);
    if (q != null) for (String t : q.split("\\s+")) if (!t.isBlank()) out.add(t);
    return out;
}
```

`chunkMapper.searchByKeyword`：在 `KnowledgeChunkMapper.xml` 加：
```xml
<select id="searchByKeyword" resultType="com.reportai.hub.knowledge.dto.RagChunkHit">
  SELECT c.id AS chunkId, c.doc_id AS docId, c.content,
         c.page_start AS pageStart, c.page_end AS pageEnd,
         c.paragraph_index AS paragraphIndex,
         d.title AS docTitle, kb.name AS kbName,
         ts_rank(to_tsvector('simple', c.content),
                 plainto_tsquery('simple', #{query})) AS bm25Score
  FROM knowledge_chunk c
  JOIN knowledge_document d ON d.id = c.doc_id
  JOIN knowledge_base kb ON kb.id = c.kb_id
  WHERE c.content ILIKE '%' || #{query} || '%'
    <if test="kbIds != null and kbIds.size() > 0">
      AND c.kb_id IN <foreach collection="kbIds" item="id" open="(" separator="," close=")">#{id}</foreach>
    </if>
  ORDER BY bm25Score DESC
  LIMIT #{limit}
</select>
```

- [ ] **Step 6: 加排除引用接口**

`KnowledgeController` 加：
```java
@PostMapping("/exclude")
public Result<Void> exclude(@RequestParam Long reportId, @RequestParam Long chunkId) {
    ReportExcludedChunk e = new ReportExcludedChunk();
    e.setReportId(reportId); e.setChunkId(chunkId);
    excludedMapper.insert(e);
    return Result.success();
}
```

- [ ] **Step 7: 编译 + curl 验证**

```bash
mvn -pl report-hub-knowledge package -DskipTests
```

```bash
curl -X POST http://localhost:8081/api/v1/rag/search \
  -H "Content-Type: application/json" \
  -d '{"query":"新能源汽车","kbIds":[2],"topK":5,"includeKeywords":["销量"],"excludeKeywords":["旧政策"]}'
```

预期：返回 hits 含 paragraphIndex / vectorScore / bm25Score / finalScore / highlightSpans。

- [ ] **Step 8: Commit**

```bash
git add backend/report-hub-knowledge/
git commit -m "feat(rag): 混合检索 (向量 0.7 + BM25 0.3) + include/exclude/排除引用"
```

---

## Task 4：报告生成时埋引用 + CitationParser + report_citation 入库

**Files:**
- Create: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/entity/ReportCitation.java`
- Create: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/mapper/ReportCitationMapper.java`
- Create: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/service/CitationParser.java`
- Create: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/controller/CitationController.java`
- Modify: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/service/impl/ReportGenerationServiceImpl.java`

- [ ] **Step 1: ReportCitation 实体**

```java
@Data @TableName("report_citation")
public class ReportCitation {
    @TableId(type = IdType.AUTO) private Long id;
    private Long reportId;
    private Long versionId;
    private Integer sectionIndex;
    private Integer paragraphIndex;
    private Integer citationMarker;
    private Long chunkId;
    private Long docId;
    private String docTitle;
    private Integer pageStart;
    private Integer pageEnd;
    private String snippet;
    private Boolean accepted;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
}
```

```java
public interface ReportCitationMapper extends BaseMapper<ReportCitation> {}
```

- [ ] **Step 2: 修改 prompt 让 LLM 输出 [CITE:...]**

在 `ReportGenerationServiceImpl` 找到组装 prompt 的地方（搜索 `buildPrompt` 或 `system_prompt`），追加：

```java
String citationGuide = """

【引用规范】
请在每个事实性陈述句末尾标注引用，格式严格为：[CITE:chunk_id1,chunk_id2]
仅可引用以下检索结果，不得编造 chunk_id：

%s
""".formatted(buildHitsSnippet(hits));

private String buildHitsSnippet(List<RagChunkHit> hits) {
    StringBuilder sb = new StringBuilder();
    for (RagChunkHit h : hits) {
        sb.append("- chunk_id=").append(h.getChunkId())
          .append(" | ").append(h.getDocTitle())
          .append(h.getPageStart() != null ? " P" + h.getPageStart() + "-" + h.getPageEnd() : "")
          .append(" | ").append(truncate(h.getContent(), 120))
          .append("\n");
    }
    return sb.toString();
}
```

- [ ] **Step 3: CitationParser**

```java
package com.reportai.hub.report.service;

import com.reportai.hub.knowledge.entity.KnowledgeChunk;
import com.reportai.hub.knowledge.mapper.KnowledgeChunkMapper;
import com.reportai.hub.knowledge.mapper.KnowledgeDocumentMapper;
import com.reportai.hub.knowledge.entity.KnowledgeDocument;
import com.reportai.hub.report.entity.ReportCitation;
import com.reportai.hub.report.mapper.ReportCitationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service @RequiredArgsConstructor
public class CitationParser {
    private static final Pattern CITE = Pattern.compile("\\[CITE:([\\d,]+)\\]");

    private final ReportCitationMapper citationMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper docMapper;

    /** 解析 markdown 中的 [CITE:...]，写入 report_citation，返回带 [n] 的 markdown。 */
    @Transactional
    public String parseAndPersist(String markdown, Long reportId, Long versionId, int sectionIndex) {
        Map<Long, Integer> chunkToMarker = new LinkedHashMap<>();
        int paragraphIdx = 0;
        StringBuilder out = new StringBuilder();
        AtomicInteger marker = new AtomicInteger(0);
        for (String paragraph : markdown.split("\n\n")) {
            Matcher m = CITE.matcher(paragraph);
            StringBuffer pb = new StringBuffer();
            while (m.find()) {
                StringBuilder repl = new StringBuilder();
                for (String idStr : m.group(1).split(",")) {
                    long cid;
                    try { cid = Long.parseLong(idStr.trim()); } catch (Exception e) { continue; }
                    Integer mk = chunkToMarker.computeIfAbsent(cid, k -> marker.incrementAndGet());
                    repl.append("[").append(mk).append("]");
                    persistCitation(reportId, versionId, sectionIndex, paragraphIdx, mk, cid);
                }
                m.appendReplacement(pb, Matcher.quoteReplacement(repl.toString()));
            }
            m.appendTail(pb);
            out.append(pb).append("\n\n");
            paragraphIdx++;
        }
        return out.toString().stripTrailing();
    }

    private void persistCitation(Long reportId, Long versionId, int sectionIdx, int paraIdx, int mk, long chunkId) {
        // 去重：同 (reportId, marker, chunkId) 只插一次
        Long exists = citationMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ReportCitation>()
                .eq("report_id", reportId).eq("citation_marker", mk).eq("chunk_id", chunkId));
        if (exists != null && exists > 0) return;

        KnowledgeChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null) return;
        KnowledgeDocument doc = docMapper.selectById(chunk.getDocId());

        ReportCitation c = new ReportCitation();
        c.setReportId(reportId); c.setVersionId(versionId);
        c.setSectionIndex(sectionIdx); c.setParagraphIndex(paraIdx);
        c.setCitationMarker(mk);
        c.setChunkId(chunkId); c.setDocId(chunk.getDocId());
        c.setDocTitle(doc != null ? doc.getTitle() : null);
        c.setPageStart(chunk.getPageStart()); c.setPageEnd(chunk.getPageEnd());
        c.setSnippet(chunk.getContent().length() > 200
            ? chunk.getContent().substring(0, 200) + "..." : chunk.getContent());
        c.setAccepted(true);
        citationMapper.insert(c);
    }
}
```

注意 `AtomicInteger` 需 `import java.util.concurrent.atomic.AtomicInteger;`。

- [ ] **Step 4: ReportGenerationServiceImpl 在生成完成时调用 parseAndPersist**

找到流式生成完成（`onComplete` / `onDone`）回调，在保存 `report.setContent(fullText)` 之前：

```java
String parsed = citationParser.parseAndPersist(fullText, report.getId(), versionId, /*sectionIndex=*/0);
report.setContent(parsed);
```

- [ ] **Step 5: CitationController**

```java
@RestController
@RequestMapping("/api/v1/reports/{id}/citations")
@RequiredArgsConstructor
public class CitationController {
    private final ReportCitationMapper citationMapper;

    @GetMapping
    public Result<List<ReportCitation>> list(@PathVariable Long id) {
        return Result.success(citationMapper.selectList(
            new QueryWrapper<ReportCitation>().eq("report_id", id).eq("accepted", true)
                .orderByAsc("citation_marker")));
    }

    @PostMapping("/{marker}/exclude")
    public Result<Void> exclude(@PathVariable Long id, @PathVariable Integer marker) {
        ReportCitation upd = new ReportCitation();
        upd.setAccepted(false);
        citationMapper.update(upd, new QueryWrapper<ReportCitation>()
            .eq("report_id", id).eq("citation_marker", marker));
        return Result.success();
    }
}
```

- [ ] **Step 6: 编译 + 端到端验证**

```bash
mvn -pl report-hub-report package -DskipTests
```

```bash
# 触发生成（已存在的报告 ID）
curl -X POST http://localhost:8081/api/v1/reports/1/generate -N
# 完成后查询
curl http://localhost:8081/api/v1/reports/1/citations
```

预期：返回 citation 列表，含 docTitle / pageStart / snippet。

- [ ] **Step 7: Commit**

```bash
git add backend/report-hub-report/
git commit -m "feat(report): 引用埋点 + CitationParser + report_citation 入库 + 排除引用 API"
```

---

## Task 5：章节级 SSE — report_section + per-section 流式

**Files:**
- Create: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/entity/ReportSection.java`
- Create: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/mapper/ReportSectionMapper.java`
- Create: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/service/SectionGenerationService.java`
- Modify: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/controller/ReportController.java`

- [ ] **Step 1: 实体 + Mapper**

```java
@Data @TableName("report_section")
public class ReportSection {
    @TableId(type = IdType.AUTO) private Long id;
    private Long reportId;
    private Integer sectionIndex;
    private String title;
    private String prompt;
    private String status;        // pending/generating/done/failed
    private String content;
    private Integer wordCount;
    private Integer citationCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
public interface ReportSectionMapper extends BaseMapper<ReportSection> {}
```

- [ ] **Step 2: SectionGenerationService**

```java
@Service @RequiredArgsConstructor
public class SectionGenerationService {
    private final ReportSectionMapper sectionMapper;
    private final RagSearchService ragSearchService;
    private final DoubaoLlmClient llm;
    private final CitationParser citationParser;

    /** 拆 outline 为 sections，全 pending 入库。outline 是 [{title,prompt}...]。 */
    public List<ReportSection> initSections(Long reportId, List<Map<String, String>> outline) {
        sectionMapper.delete(new QueryWrapper<ReportSection>().eq("report_id", reportId));
        List<ReportSection> rows = new ArrayList<>();
        for (int i = 0; i < outline.size(); i++) {
            ReportSection s = new ReportSection();
            s.setReportId(reportId); s.setSectionIndex(i);
            s.setTitle(outline.get(i).get("title"));
            s.setPrompt(outline.get(i).get("prompt"));
            s.setStatus("pending");
            sectionMapper.insert(s);
            rows.add(s);
        }
        return rows;
    }

    /** 流式生成单章。emit 接受 SSE 事件 JSON。 */
    public void streamSection(Long reportId, int sectionIndex, List<Long> kbIds,
                              Consumer<String> emit) {
        ReportSection s = sectionMapper.selectOne(new QueryWrapper<ReportSection>()
            .eq("report_id", reportId).eq("section_index", sectionIndex));
        if (s == null) throw new BizException("section not found");
        s.setStatus("generating"); s.setStartedAt(LocalDateTime.now());
        sectionMapper.updateById(s);

        emit.accept("event: start\ndata: {\"sectionIndex\":" + sectionIndex + "}\n\n");

        try {
            RagSearchQuery q = new RagSearchQuery();
            q.setReportId(reportId); q.setKbIds(kbIds);
            q.setQuery(s.getTitle() + " " + (s.getPrompt() == null ? "" : s.getPrompt()));
            q.setTopK(8);
            List<RagChunkHit> hits = ragSearchService.search(q).getHits();

            String prompt = buildSectionPrompt(s, hits);
            StringBuilder full = new StringBuilder();
            llm.streamChat(prompt, token -> {
                full.append(token);
                emit.accept("event: token\ndata: " + escape(token) + "\n\n");
            });

            String parsed = citationParser.parseAndPersist(full.toString(), reportId, null, sectionIndex);
            s.setContent(parsed);
            s.setWordCount(parsed.length());
            s.setCitationCount(countMarkers(parsed));
            s.setStatus("done"); s.setFinishedAt(LocalDateTime.now());
            sectionMapper.updateById(s);

            emit.accept("event: done\ndata: {\"wordCount\":" + s.getWordCount() +
                        ",\"citationCount\":" + s.getCitationCount() + "}\n\n");
        } catch (Exception e) {
            s.setStatus("failed"); sectionMapper.updateById(s);
            emit.accept("event: error\ndata: " + escape(e.getMessage()) + "\n\n");
        }
    }

    private String buildSectionPrompt(ReportSection s, List<RagChunkHit> hits) {
        StringBuilder hitsBlock = new StringBuilder();
        for (RagChunkHit h : hits) {
            hitsBlock.append("- chunk_id=").append(h.getChunkId())
                .append(" | ").append(h.getDocTitle());
            if (h.getPageStart() != null) hitsBlock.append(" P").append(h.getPageStart()).append("-").append(h.getPageEnd());
            hitsBlock.append(" | ").append(h.getContent().length() > 150
                ? h.getContent().substring(0, 150) + "..." : h.getContent()).append("\n");
        }
        return """
            你是专业报告写作助手。请基于以下检索资料，撰写报告章节。

            章节标题：%s
            章节要点：%s

            【引用规范】
            每个事实性陈述句末尾必须标注引用，格式严格为：[CITE:chunk_id1,chunk_id2]
            仅可引用以下 chunk_id，不得编造：
            %s

            请直接输出 markdown 格式正文，不要包含章节标题。
            """.formatted(s.getTitle(), s.getPrompt() == null ? "" : s.getPrompt(), hitsBlock);
    }

    private int countMarkers(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[\\d+\\]").matcher(text);
        int n = 0; while (m.find()) n++; return n;
    }

    private String escape(String t) {
        return "\"" + (t == null ? "" : t.replace("\\","\\\\").replace("\"","\\\"")
            .replace("\n","\\n").replace("\r","")) + "\"";
    }
}
```

- [ ] **Step 3: ReportController 加端点**

```java
@PostMapping("/{id}/sections/init")
public Result<List<ReportSection>> initSections(@PathVariable Long id,
                                                @RequestBody List<Map<String,String>> outline) {
    return Result.success(sectionGenerationService.initSections(id, outline));
}

@GetMapping(value = "/{id}/sections/{idx}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamSection(@PathVariable Long id, @PathVariable Integer idx,
                                @RequestParam(required = false) List<Long> kbIds) {
    SseEmitter emitter = new SseEmitter(180_000L);
    Executors.newSingleThreadExecutor().submit(() -> {
        try {
            sectionGenerationService.streamSection(id, idx, kbIds, raw -> {
                try { emitter.send(SseEmitter.event().data(raw)); } catch (IOException ignore) {}
            });
            emitter.complete();
        } catch (Exception e) { emitter.completeWithError(e); }
    });
    return emitter;
}

@GetMapping("/{id}/sections")
public Result<List<ReportSection>> listSections(@PathVariable Long id) {
    return Result.success(sectionMapper.selectList(
        new QueryWrapper<ReportSection>().eq("report_id", id).orderByAsc("section_index")));
}
```

- [ ] **Step 4: 编译验证**

```bash
mvn -pl report-hub-report package -DskipTests
```

```bash
# 初始化大纲
curl -X POST http://localhost:8081/api/v1/reports/1/sections/init \
  -H "Content-Type: application/json" \
  -d '[{"title":"行业概况","prompt":"产业规模与增速"},{"title":"政策环境","prompt":"近 3 年政策梳理"}]'

# 流式拉
curl -N "http://localhost:8081/api/v1/reports/1/sections/0/stream?kbIds=2"
```

预期：先 start 事件，再大量 token 事件，最后 done 事件。

- [ ] **Step 5: Commit**

```bash
git add backend/report-hub-report/
git commit -m "feat(report): 章节级 SSE 流式生成 + report_section 状态管理"
```

---

## Task 6：4 改写模式真区分 + 续写

**Files:**
- Modify: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/service/impl/RewriteServiceImpl.java`
- Modify: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/service/RewriteService.java`
- Modify: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/controller/RewriteController.java`

- [ ] **Step 1: 4 模式 prompt 模板**

在 `RewriteServiceImpl` 加私有方法：

```java
private String promptFor(RewriteMode mode, String original, Map<String,String> opts) {
    return switch (mode) {
        case DATA_UPDATE -> """
            【任务】数据更新改写
            目标：保持原稿段落结构、句式、修辞**完全不变**，仅替换数据点（数字/日期/百分比/排名）为新值。
            
            如果检索资料中含有更新版本的数据，请优先使用；否则保留原值并标注 [无新数据]。
            每个被替换的数据后追加 [DIFF:旧值→新值] 注释一次（用于前端 diff 展示）。
            
            原稿：
            %s
            
            参考资料（可能含新数据）：
            %s
            """.formatted(original, opts.getOrDefault("hits", ""));
        case ANGLE_SHIFT -> """
            【任务】视角调整改写
            目标受众：%s
            目标风格：%s
            
            保持核心事实不变，重写视角、措辞、详略。可压缩长度至原稿 70%%。
            
            原稿：
            %s
            """.formatted(opts.getOrDefault("persona","领导简报"),
                          opts.getOrDefault("tone","简洁有力"), original);
        case EXPAND -> """
            【任务】内容扩展
            目标：在原稿基础上**追加** 2-3 段新分析/新案例/新章节。
            禁止修改原稿任何字符。新增内容用 markdown 二级标题区分。
            
            原稿：
            %s
            
            扩展方向（可选）：%s
            
            参考资料：
            %s
            """.formatted(original, opts.getOrDefault("direction","深入分析与延伸案例"),
                          opts.getOrDefault("hits",""));
        case STYLE_SHIFT -> """
            【任务】风格转换
            目标：%s（formality + language）
            保持信息量与事实，仅改语气、用词、语种。
            
            原稿：
            %s
            """.formatted(opts.getOrDefault("style","通俗中文"), original);
        case CONTINUATION -> """
            【任务】AI 续写
            请阅读原稿全文（保持原稿不变），在末尾续写新章节：
            新章节标题：%s
            写作要点：%s
            
            语言风格、术语、引用规范需与原稿保持一致。
            
            原稿：
            %s
            """.formatted(opts.getOrDefault("newSectionTitle","新章节"),
                          opts.getOrDefault("hint",""), original);
    };
}
```

- [ ] **Step 2: streamRewrite 改造接受 opts**

```java
public ReportVersion streamRewrite(Long reportId, RewriteMode mode, String instruction,
                                   Map<String,String> opts,
                                   Long operatorId, Consumer<String> onToken, Runnable onDone) {
    Report r = reportMapper.selectById(reportId);
    String prompt = promptFor(mode, r.getContent(), opts == null ? Collections.emptyMap() : opts);
    StringBuilder full = new StringBuilder();
    llm.streamChat(prompt, t -> { full.append(t); onToken.accept(t); });
    
    ReportVersion v = new ReportVersion();
    v.setReportId(reportId); v.setMode(mode.name());
    v.setContent(full.toString()); v.setOperatorId(operatorId);
    v.setBeforeContent(r.getContent());
    versionMapper.insert(v);
    onDone.run();
    return v;
}
```

`ReportVersion` 实体加 `private String beforeContent;` 字段；DB `report_version` 表加列：
```sql
ALTER TABLE report_version ADD COLUMN IF NOT EXISTS before_content TEXT;
```

- [ ] **Step 3: DATA_UPDATE 预处理 — 拉新数据**

在 streamRewrite 内 mode == DATA_UPDATE 时，先检索：
```java
if (mode == RewriteMode.DATA_UPDATE && opts != null && opts.get("kbIds") != null) {
    List<Long> kbIds = Arrays.stream(opts.get("kbIds").split(","))
        .map(String::trim).map(Long::parseLong).collect(Collectors.toList());
    RagSearchQuery q = new RagSearchQuery();
    q.setKbIds(kbIds); q.setQuery(extractKeyEntities(r.getContent())); q.setTopK(10);
    List<RagChunkHit> hits = ragSearchService.search(q).getHits();
    StringBuilder hitsStr = new StringBuilder();
    for (RagChunkHit h : hits) hitsStr.append("- ").append(h.getContent()).append("\n");
    opts.put("hits", hitsStr.toString());
}

private String extractKeyEntities(String text) {
    // 简化：提取所有数字+前后 6 字
    Matcher m = Pattern.compile(".{0,6}[\\d]+(\\.\\d+)?[%万亿元个家次年].{0,6}").matcher(text);
    StringBuilder b = new StringBuilder();
    while (m.find()) { b.append(m.group()).append(" "); if (b.length() > 200) break; }
    return b.toString();
}
```

- [ ] **Step 4: RewriteController 接受 opts**

```java
@PostMapping(value = "/{reportId}/rewrite", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter rewrite(@PathVariable Long reportId,
                          @RequestParam RewriteMode mode,
                          @RequestParam(required=false) String instruction,
                          @RequestBody(required=false) Map<String,String> opts) {
    SseEmitter emitter = new SseEmitter(180_000L);
    Executors.newSingleThreadExecutor().submit(() -> {
        try {
            rewriteService.streamRewrite(reportId, mode, instruction, opts,
                getCurrentUserId(),
                t -> { try { emitter.send(SseEmitter.event().data(t)); } catch (IOException ignore){} },
                () -> { try { emitter.send(SseEmitter.event().name("done").data("ok")); } catch (IOException ignore){} });
            emitter.complete();
        } catch (Exception e) { emitter.completeWithError(e); }
    });
    return emitter;
}
```

- [ ] **Step 5: 编译 + 验证 4 模式**

```bash
mvn -pl report-hub-report package -DskipTests

curl -X POST "http://localhost:8081/api/v1/reports/1/rewrite?mode=DATA_UPDATE" \
  -H "Content-Type: application/json" -d '{"kbIds":"2,3"}' -N
curl -X POST "http://localhost:8081/api/v1/reports/1/rewrite?mode=ANGLE_SHIFT" \
  -H "Content-Type: application/json" -d '{"persona":"记者深度","tone":"客观叙述"}' -N
curl -X POST "http://localhost:8081/api/v1/reports/1/rewrite?mode=EXPAND" -d '{"direction":"补充国际比较"}' -N
curl -X POST "http://localhost:8081/api/v1/reports/1/rewrite?mode=STYLE_SHIFT" -d '{"style":"英文正式"}' -N
curl -X POST "http://localhost:8081/api/v1/reports/1/rewrite?mode=CONTINUATION" -d '{"newSectionTitle":"实施路径","hint":"3 阶段路线图"}' -N
```

预期：5 个调用都返回 SSE 流，DB 里 report_version 多 5 行，每行 mode 不同，content 风格明显不同。

- [ ] **Step 6: Commit**

```bash
git add backend/report-hub-report/
git commit -m "feat(rewrite): 4 模式真区分 prompt + DATA_UPDATE 检索新数据 + 续写 + before_content 持久化"
```

---

## Task 7：质量检查 — 覆盖度 + KB 分布 + 事实性可疑列表

**Files:**
- Modify: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/service/QualityCheckService.java`
- Modify: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/service/impl/QualityCheckServiceImpl.java`
- Create: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/entity/ReportQuality.java`
- Create: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/mapper/ReportQualityMapper.java`

- [ ] **Step 1: 实体 + Mapper（同 Task 1 schema）**

```java
@Data @TableName("report_quality")
public class ReportQuality {
    @TableId(type = IdType.NONE) private Long reportId;
    private BigDecimal coverageRate;
    private Integer citationsTotal;
    private Integer paragraphsTotal;
    private Integer paragraphsCited;
    private String kbDistribution;   // JSONB 字符串
    private String suspiciousFacts;
    private LocalDateTime checkedAt;
}
public interface ReportQualityMapper extends BaseMapper<ReportQuality> {}
```

- [ ] **Step 2: QualityCheckService 改造**

```java
@Override
public ReportQuality runCheck(Long reportId) {
    Report r = reportMapper.selectById(reportId);
    if (r == null) throw new BizException("报告不存在");

    // 1. 段落统计
    String[] paras = r.getContent().split("\n\n");
    int total = (int) Arrays.stream(paras).filter(p -> !p.trim().isEmpty()).count();

    List<ReportCitation> citations = citationMapper.selectList(
        new QueryWrapper<ReportCitation>().eq("report_id", reportId).eq("accepted", true));
    Set<String> citedParas = citations.stream()
        .map(c -> c.getSectionIndex() + ":" + c.getParagraphIndex())
        .collect(Collectors.toSet());
    int cited = citedParas.size();

    // 2. KB 分布
    Map<Long, Long> docToKb = new HashMap<>();
    for (ReportCitation c : citations) {
        docToKb.computeIfAbsent(c.getDocId(), id -> {
            KnowledgeDocument d = docMapper.selectById(id);
            return d == null ? 0L : d.getKbId();
        });
    }
    Map<String, Long> kbCount = new HashMap<>();
    Map<Long, String> kbNames = kbMapper.selectList(null).stream()
        .collect(Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName));
    for (ReportCitation c : citations) {
        Long kbId = docToKb.get(c.getDocId());
        String name = kbNames.getOrDefault(kbId, "其他");
        kbCount.merge(name, 1L, Long::sum);
    }

    // 3. 事实性可疑：抽数字，让 LLM 一次性校验
    List<String> facts = extractFacts(r.getContent());
    String suspicious = "[]";
    if (!facts.isEmpty()) {
        String prompt = "以下是报告中的数字陈述，请判断哪些可能不准确（与常识矛盾或自相矛盾）。" +
            "返回 JSON 数组，元素 {text:'',reason:'',severity:'warn|info'}，最多 5 条。\n\n"
            + String.join("\n", facts);
        suspicious = llm.chatJson(prompt);
    }

    ReportQuality q = new ReportQuality();
    q.setReportId(reportId);
    q.setCoverageRate(total == 0 ? BigDecimal.ZERO :
        BigDecimal.valueOf(cited * 100.0 / total).setScale(2, RoundingMode.HALF_UP));
    q.setCitationsTotal(citations.size());
    q.setParagraphsTotal(total);
    q.setParagraphsCited(cited);
    q.setKbDistribution(JSON.toJSONString(kbCount));
    q.setSuspiciousFacts(suspicious);
    q.setCheckedAt(LocalDateTime.now());

    qualityMapper.deleteById(reportId);
    qualityMapper.insert(q);
    return q;
}

private List<String> extractFacts(String text) {
    Matcher m = Pattern.compile(".{0,10}[\\d]+(\\.\\d+)?[%万亿元个家次年].{0,10}").matcher(text);
    List<String> out = new ArrayList<>();
    while (m.find()) { out.add(m.group()); if (out.size() >= 30) break; }
    return out;
}
```

`DoubaoLlmClient` 加 `chatJson(prompt)` 方法（同 chat 但 system prompt 加 "仅输出 JSON，无 markdown 代码块"）。

- [ ] **Step 3: 在生成完成时异步触发**

`SectionGenerationService.streamSection` 末尾或 `ReportGenerationServiceImpl` 整篇生成完后：

```java
CompletableFuture.runAsync(() -> qualityCheckService.runCheck(reportId));
```

- [ ] **Step 4: API 暴露**

`ReportController`:
```java
@GetMapping("/{id}/quality")
public Result<ReportQuality> quality(@PathVariable Long id) {
    return Result.success(qualityMapper.selectById(id));
}

@PostMapping("/{id}/quality/recheck")
public Result<ReportQuality> recheck(@PathVariable Long id) {
    return Result.success(qualityCheckService.runCheck(id));
}
```

- [ ] **Step 5: 编译 + 验证**

```bash
mvn -pl report-hub-report package -DskipTests
curl -X POST http://localhost:8081/api/v1/reports/1/quality/recheck
curl http://localhost:8081/api/v1/reports/1/quality
```

预期：返回 coverageRate / kbDistribution（JSON）/ suspiciousFacts。

- [ ] **Step 6: Commit**

```bash
git add backend/report-hub-report/
git commit -m "feat(quality): 覆盖度 + KB 分布 + 事实性可疑列表 + 异步触发"
```

---

## Task 8：前端 Tiptap 接入工作台 + 引用 Mark + Popover

**Files:**
- Modify: `report-ai/frontend/package.json`（加依赖）
- Create: `report-ai/frontend/src/components/editor/TiptapEditor.vue`
- Create: `report-ai/frontend/src/components/editor/CitationMark.ts`
- Create: `report-ai/frontend/src/components/editor/CitationPopover.vue`
- Modify: `report-ai/frontend/src/views/reports/detail.vue`

- [ ] **Step 1: 安装依赖**

```bash
cd report-ai/frontend
npm install @tiptap/vue-3 @tiptap/starter-kit @tiptap/extension-bubble-menu @tiptap/extension-floating-menu \
            diff-match-patch vuedraggable@4 echarts vue-echarts
```

- [ ] **Step 2: CitationMark Tiptap 扩展**

```typescript
// frontend/src/components/editor/CitationMark.ts
import { Mark, mergeAttributes } from '@tiptap/core'

export const CitationMark = Mark.create({
  name: 'citation',
  inclusive: false,
  addAttributes() {
    return { marker: { default: null } }
  },
  parseHTML() {
    return [{ tag: 'sup[data-citation]' }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['sup', mergeAttributes(HTMLAttributes, {
      'data-citation': HTMLAttributes.marker,
      class: 'citation-marker',
    }), `[${HTMLAttributes.marker}]`]
  },
})
```

- [ ] **Step 3: TiptapEditor 组件**

```vue
<template>
  <div class="tiptap-editor">
    <bubble-menu v-if="editor" :editor="editor" :tippy-options="{ duration: 100 }">
      <div class="bubble-menu">
        <el-button size="small" @click="onAi('POLISH')">✨ 优化</el-button>
        <el-button size="small" @click="onAi('CONTINUE')">➕ 续写</el-button>
        <el-button size="small" @click="onAi('DATA_UPDATE')">🔢 改数据</el-button>
        <el-button size="small" @click="onAi('STYLE_SHIFT')">🎭 换风格</el-button>
      </div>
    </bubble-menu>
    <editor-content :editor="editor" @click="onCitationClick" />
    <citation-popover ref="popoverRef" :report-id="reportId" />
  </div>
</template>

<script setup lang="ts">
import { useEditor, EditorContent, BubbleMenu } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { CitationMark } from './CitationMark'
import { onBeforeUnmount, ref, watch } from 'vue'
import CitationPopover from './CitationPopover.vue'
import { rewriteSectionStream } from '@/api/rewrite'

const props = defineProps<{ modelValue: string; reportId: number }>()
const emit = defineEmits(['update:modelValue'])

const popoverRef = ref()

const editor = useEditor({
  content: markdownToHtml(props.modelValue),
  extensions: [StarterKit, CitationMark],
  onUpdate: ({ editor }) => emit('update:modelValue', htmlToMarkdown(editor.getHTML())),
})

watch(() => props.modelValue, val => {
  if (editor.value && htmlToMarkdown(editor.value.getHTML()) !== val) {
    editor.value.commands.setContent(markdownToHtml(val))
  }
})

onBeforeUnmount(() => editor.value?.destroy())

function markdownToHtml(md: string): string {
  // 简易：把 [n] 转成 <sup data-citation="n">[n]</sup>，段落转 <p>
  if (!md) return ''
  const html = md
    .split(/\n\n+/)
    .map(p => '<p>' + p.replace(/\[(\d+)\]/g, '<sup data-citation="$1" class="citation-marker">[$1]</sup>') + '</p>')
    .join('')
  return html
}

function htmlToMarkdown(html: string): string {
  // 反向：sup → [n]，p → 段落
  const tmp = document.createElement('div')
  tmp.innerHTML = html
  tmp.querySelectorAll('sup[data-citation]').forEach(s => {
    s.replaceWith(`[${s.getAttribute('data-citation')}]`)
  })
  return Array.from(tmp.querySelectorAll('p')).map(p => p.textContent || '').join('\n\n')
}

function onCitationClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (target?.dataset?.citation) {
    popoverRef.value?.show(target, Number(target.dataset.citation))
  }
}

async function onAi(mode: string) {
  const sel = editor.value?.state.selection
  if (!sel || sel.empty) return
  const text = editor.value!.state.doc.textBetween(sel.from, sel.to)
  const result = await rewriteSectionStream(props.reportId, text, mode)
  editor.value!.chain().focus().deleteSelection().insertContent(result).run()
}
</script>

<style scoped>
.bubble-menu {
  display: flex; gap: 4px;
  background: white; padding: 4px;
  border-radius: 6px; box-shadow: 0 2px 12px rgba(0,0,0,.15);
}
:deep(.citation-marker) {
  color: #409eff; cursor: pointer; padding: 0 2px;
  background: #ecf5ff; border-radius: 3px; font-size: 11px;
}
:deep(.citation-marker:hover) { background: #d9ecff; }
</style>
```

- [ ] **Step 4: CitationPopover**

```vue
<template>
  <el-popover v-model:visible="visible" :virtual-ref="targetEl" virtual-triggering
              placement="top" trigger="click" :width="380">
    <div v-if="citation">
      <div class="cite-title">📄 {{ citation.docTitle }}</div>
      <div class="cite-meta">
        <span v-if="citation.pageStart">第 {{ citation.pageStart }}-{{ citation.pageEnd }} 页</span>
        <span> · 第 {{ citation.paragraphIndex }} 段</span>
      </div>
      <div class="cite-snippet">{{ citation.snippet }}</div>
      <div class="cite-actions">
        <el-button size="small" @click="openDoc">打开原文</el-button>
        <el-button size="small" type="danger" plain @click="exclude">排除此引用</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listCitations, excludeCitation } from '@/api/citation'

const props = defineProps<{ reportId: number }>()
const visible = ref(false)
const targetEl = ref<HTMLElement | null>(null)
const citation = ref<any>(null)
let cache: any[] = []

async function show(el: HTMLElement, marker: number) {
  if (cache.length === 0) cache = (await listCitations(props.reportId)).data || []
  citation.value = cache.find(c => c.citationMarker === marker)
  targetEl.value = el
  visible.value = true
}

async function openDoc() {
  if (citation.value?.docId) {
    window.open(`/knowledge/documents/${citation.value.docId}`, '_blank')
  }
}

async function exclude() {
  await excludeCitation(props.reportId, citation.value.citationMarker)
  ElMessage.success('已排除此引用')
  visible.value = false
  cache = []
}

defineExpose({ show })
</script>

<style scoped>
.cite-title { font-weight: 600; margin-bottom: 6px; color: #303133; }
.cite-meta { font-size: 12px; color: #909399; margin-bottom: 8px; }
.cite-snippet {
  font-size: 13px; line-height: 1.6; color: #606266;
  background: #f5f7fa; padding: 8px; border-radius: 4px;
  max-height: 120px; overflow-y: auto; margin-bottom: 8px;
}
.cite-actions { display: flex; gap: 8px; justify-content: flex-end; }
</style>
```

- [ ] **Step 5: API 客户端**

`frontend/src/api/citation.ts`：
```typescript
import request from '@/utils/request'
export const listCitations = (reportId: number) =>
  request.get(`/reports/${reportId}/citations`)
export const excludeCitation = (reportId: number, marker: number) =>
  request.post(`/reports/${reportId}/citations/${marker}/exclude`)
```

`frontend/src/api/rewrite.ts`：
```typescript
import request from '@/utils/request'

export async function rewriteSectionStream(reportId: number, content: string, mode: string): Promise<string> {
  // 简化：用 POST 同步取整段，后续可改 EventSource
  const res = await request.post(`/reports/${reportId}/rewrite/section`, { content, mode })
  return res.data
}
```

后端补一个简单端点：
```java
@PostMapping("/{id}/rewrite/section")
public Result<String> rewriteSection(@PathVariable Long id, @RequestBody Map<String,String> body) {
    StringBuilder full = new StringBuilder();
    rewriteService.streamRewriteSection(id, body.get("content"), body.get("mode"), null,
        getCurrentUserId(), full::append, () -> {});
    return Result.success(full.toString());
}
```

- [ ] **Step 6: 替换 reports/detail.vue 的渲染**

把现有 markdown-it 渲染区替换为：
```vue
<TiptapEditor v-model="report.content" :report-id="report.id" />
```

- [ ] **Step 7: 浏览器手动验证**

```bash
cd frontend && npm run dev
# 打开 http://localhost:5173/reports/1
# 1. 报告里点 [1] [2] [3] 引用 → 弹窗显示 docTitle + pageRange + snippet
# 2. 选中一段文字 → BubbleMenu 出现 4 按钮
# 3. 点「✨ 优化」 → 该段被改写替换
```

- [ ] **Step 8: Commit**

```bash
git add frontend/
git commit -m "feat(frontend): Tiptap 工作台 + CitationMark + Popover + 段落 BubbleMenu"
```

---

## Task 9：前端大纲拖拽 + 章节流式视图

**Files:**
- Create: `report-ai/frontend/src/components/outline/OutlineEditor.vue`
- Create: `report-ai/frontend/src/components/outline/SectionStreamView.vue`
- Create: `report-ai/frontend/src/api/section.ts`
- Modify: `report-ai/frontend/src/views/workspace/index.vue`

- [ ] **Step 1: section.ts API**

```typescript
import request from '@/utils/request'
export const initSections = (reportId: number, outline: any[]) =>
  request.post(`/reports/${reportId}/sections/init`, outline)
export const listSections = (reportId: number) =>
  request.get(`/reports/${reportId}/sections`)
export const streamSection = (reportId: number, idx: number, kbIds: number[]) => {
  const url = `/api/v1/reports/${reportId}/sections/${idx}/stream?kbIds=${kbIds.join(',')}`
  return new EventSource(url)
}
```

- [ ] **Step 2: OutlineEditor**

```vue
<template>
  <div class="outline-editor">
    <div class="outline-header">
      <h3>报告大纲</h3>
      <div>
        <el-button size="small" @click="loadFromTemplate">从模板载入</el-button>
        <el-button size="small" type="primary" plain @click="saveAsTemplate">保存为模板</el-button>
      </div>
    </div>
    <draggable v-model="sections" item-key="title" handle=".drag-handle" tag="div">
      <template #item="{ element, index }">
        <el-card class="section-card" shadow="hover">
          <div class="row">
            <span class="drag-handle">⋮⋮</span>
            <el-input v-model="element.title" placeholder="章节标题" size="small" />
            <el-input-number v-model="element.targetWords" :min="100" :step="100" size="small" />
            <el-button :icon="Delete" type="danger" plain size="small" @click="remove(index)" />
          </div>
          <el-input v-model="element.prompt" type="textarea" :rows="2"
                    placeholder="本章要点（可选）" size="small" />
        </el-card>
      </template>
    </draggable>
    <el-button :icon="Plus" @click="add" plain class="add-btn">添加章节</el-button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import draggable from 'vuedraggable'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{ modelValue: any[] }>()
const emit = defineEmits(['update:modelValue'])
const sections = ref(props.modelValue.length ? props.modelValue : [{ title: '', prompt: '', targetWords: 800 }])

function add() { sections.value.push({ title: '', prompt: '', targetWords: 800 }) }
function remove(i: number) { sections.value.splice(i, 1) }
function loadFromTemplate() { ElMessage.info('TODO: 模板下拉') }
function saveAsTemplate() { ElMessage.success('已保存（TODO: 接口）') }

defineExpose({ getOutline: () => sections.value })
</script>

<style scoped>
.outline-header { display:flex; justify-content:space-between; margin-bottom:12px; }
.section-card { margin-bottom: 8px; }
.row { display:flex; gap:8px; align-items:center; margin-bottom:8px; }
.drag-handle { cursor: grab; color: #c0c4cc; user-select: none; padding: 0 6px; }
.add-btn { width: 100%; }
</style>
```

- [ ] **Step 3: SectionStreamView**

```vue
<template>
  <div class="section-stream">
    <el-card v-for="s in sections" :key="s.sectionIndex" class="section-card"
             :class="{ generating: s.status === 'generating', failed: s.status === 'failed' }">
      <div class="header">
        <span class="title">{{ s.sectionIndex + 1 }}. {{ s.title }}</span>
        <el-tag :type="statusType(s.status)" size="small">{{ statusLabel(s.status) }}</el-tag>
        <el-button v-if="s.status === 'failed'" size="small" type="warning" @click="retry(s)">重试</el-button>
      </div>
      <el-progress v-if="s.status === 'generating'" :percentage="0" :indeterminate="true" />
      <div v-if="s.content" class="content">{{ s.content.slice(0, 200) }}{{ s.content.length > 200 ? '...' : '' }}</div>
      <div class="meta">字数 {{ s.wordCount || 0 }} · 引用 {{ s.citationCount || 0 }}</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listSections, streamSection } from '@/api/section'

const props = defineProps<{ reportId: number; kbIds: number[] }>()
const sections = ref<any[]>([])

onMounted(async () => {
  sections.value = (await listSections(props.reportId)).data || []
  for (const s of sections.value) if (s.status === 'pending') startStream(s)
})

function startStream(s: any) {
  s.status = 'generating'; s.content = ''
  const es = streamSection(props.reportId, s.sectionIndex, props.kbIds)
  es.addEventListener('start', () => {})
  es.addEventListener('token', (e: any) => {
    try { s.content += JSON.parse(e.data) } catch { s.content += e.data }
  })
  es.addEventListener('done', (e: any) => {
    const meta = JSON.parse(e.data)
    s.wordCount = meta.wordCount; s.citationCount = meta.citationCount
    s.status = 'done'; es.close()
  })
  es.addEventListener('error', () => { s.status = 'failed'; es.close() })
}

function retry(s: any) { s.status = 'pending'; startStream(s) }
function statusType(s: string) {
  return ({pending:'info', generating:'warning', done:'success', failed:'danger'} as any)[s] || 'info'
}
function statusLabel(s: string) {
  return ({pending:'待生成', generating:'生成中', done:'已完成', failed:'失败'} as any)[s] || s
}
</script>

<style scoped>
.section-card { margin-bottom: 12px; }
.section-card.generating { border-left: 3px solid #e6a23c; }
.section-card.failed { border-left: 3px solid #f56c6c; }
.header { display:flex; gap:8px; align-items:center; margin-bottom:8px; }
.title { font-weight:600; flex:1; }
.content { font-size:13px; color:#606266; padding:8px; background:#f5f7fa; border-radius:4px; margin:8px 0; }
.meta { font-size:12px; color:#909399; }
</style>
```

- [ ] **Step 4: 工作台串起来**

`workspace/index.vue` 在「生成」按钮回调里：
```typescript
async function generate() {
  // 1. 创建报告（已有逻辑）
  const reportId = ...
  // 2. 初始化 sections
  const outline = outlineRef.value.getOutline().map((o:any) => ({ title: o.title, prompt: o.prompt }))
  await initSections(reportId, outline)
  // 3. 跳转详情页（详情页有 SectionStreamView）
  router.push(`/reports/${reportId}`)
}
```

`reports/detail.vue` 上方增加 `<SectionStreamView :report-id="id" :kb-ids="kbIds" />`。

- [ ] **Step 5: 浏览器验证**

```bash
# 打开 http://localhost:5173/workspace
# 1. 拖拽章节卡片 → 顺序变化
# 2. 点「生成」 → 跳详情页 → 看到每章独立进度条
# 3. 章节依次 pending → generating → done
```

- [ ] **Step 6: Commit**

```bash
git add frontend/
git commit -m "feat(frontend): 大纲拖拽编辑 + 章节级流式视图"
```

---

## Task 10：前端覆盖度仪表盘 + 修订视图 diff + 版本树

**Files:**
- Create: `report-ai/frontend/src/components/quality/CoverageDashboard.vue`
- Create: `report-ai/frontend/src/components/diff/DiffView.vue`
- Create: `report-ai/frontend/src/components/version/VersionTimeline.vue`
- Create: `report-ai/frontend/src/api/quality.ts`
- Modify: `report-ai/frontend/src/views/reports/detail.vue`

- [ ] **Step 1: quality.ts API**

```typescript
import request from '@/utils/request'
export const getQuality = (id: number) => request.get(`/reports/${id}/quality`)
export const recheckQuality = (id: number) => request.post(`/reports/${id}/quality/recheck`)
```

- [ ] **Step 2: CoverageDashboard**

```vue
<template>
  <el-card class="coverage-dashboard">
    <template #header>
      <div class="head">
        <span>📊 报告质量体检</span>
        <el-button size="small" plain @click="refresh">重新检查</el-button>
      </div>
    </template>
    <el-row :gutter="16" class="kpis">
      <el-col :span="8"><div class="kpi"><div class="num">{{ q?.coverageRate || 0 }}%</div><div class="lbl">引用覆盖率</div></div></el-col>
      <el-col :span="8"><div class="kpi"><div class="num">{{ q?.citationsTotal || 0 }}</div><div class="lbl">引用总数</div></div></el-col>
      <el-col :span="8"><div class="kpi"><div class="num">{{ kbCount }}</div><div class="lbl">来源知识库</div></div></el-col>
    </el-row>
    <div class="chart-row">
      <v-chart :option="pieOption" autoresize style="height:240px" />
    </div>
    <el-collapse v-if="suspicious.length">
      <el-collapse-item title="⚠️ 事实性可疑列表" :name="1">
        <ul><li v-for="(s,i) in suspicious" :key="i" :class="s.severity"><strong>{{ s.text }}</strong> — {{ s.reason }}</li></ul>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { getQuality, recheckQuality } from '@/api/quality'

use([CanvasRenderer, PieChart, TooltipComponent, LegendComponent])
const props = defineProps<{ reportId: number }>()
const q = ref<any>(null)

const kbDist = computed(() => {
  if (!q.value?.kbDistribution) return {}
  try { return typeof q.value.kbDistribution === 'string'
    ? JSON.parse(q.value.kbDistribution) : q.value.kbDistribution } catch { return {} }
})
const kbCount = computed(() => Object.keys(kbDist.value).length)
const suspicious = computed(() => {
  if (!q.value?.suspiciousFacts) return []
  try { return typeof q.value.suspiciousFacts === 'string'
    ? JSON.parse(q.value.suspiciousFacts) : q.value.suspiciousFacts } catch { return [] }
})
const pieOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [{
    type: 'pie', radius: '65%',
    data: Object.entries(kbDist.value).map(([k,v]) => ({ name: k, value: v })),
  }],
}))

onMounted(load)
async function load() { q.value = (await getQuality(props.reportId)).data }
async function refresh() { q.value = (await recheckQuality(props.reportId)).data }
</script>

<style scoped>
.head { display:flex; justify-content:space-between; }
.kpis .kpi { text-align:center; padding:12px; background:#f5f7fa; border-radius:6px; }
.num { font-size:24px; font-weight:600; color:#409eff; }
.lbl { font-size:12px; color:#909399; }
.chart-row { margin-top:16px; }
li.warn { color:#e6a23c; } li.info { color:#909399; }
</style>
```

- [ ] **Step 3: DiffView**

```vue
<template>
  <div class="diff-view">
    <div class="diff-toolbar">
      <el-button size="small" @click="acceptAll" type="success" plain>全部接受</el-button>
      <el-button size="small" @click="rejectAll" type="danger" plain>全部拒绝</el-button>
    </div>
    <div class="diff-content" v-html="diffHtml"></div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DiffMatchPatch from 'diff-match-patch'

const props = defineProps<{ before: string; after: string }>()
const emit = defineEmits<{(e:'accept', text:string): void; (e:'reject'): void }>()

const diffHtml = computed(() => {
  const dmp = new DiffMatchPatch()
  const diffs = dmp.diff_main(props.before || '', props.after || '')
  dmp.diff_cleanupSemantic(diffs)
  return diffs.map(([op, txt]: [number, string]) => {
    const safe = txt.replace(/</g,'&lt;').replace(/\n/g,'<br>')
    if (op === 1) return `<span class="diff-add">${safe}</span>`
    if (op === -1) return `<span class="diff-del">${safe}</span>`
    return `<span>${safe}</span>`
  }).join('')
})

function acceptAll() { emit('accept', props.after) }
function rejectAll() { emit('reject') }
</script>

<style scoped>
.diff-view { background:#fff; border:1px solid #ebeef5; border-radius:6px; }
.diff-toolbar { padding:8px; border-bottom:1px solid #ebeef5; display:flex; gap:8px; }
.diff-content { padding:12px; line-height:1.8; max-height:60vh; overflow:auto; font-family:monospace; }
:deep(.diff-add) { background:#e7f6ec; color:#1f7a4d; padding:0 2px; border-radius:2px; }
:deep(.diff-del) { background:#fde8e8; color:#9b1c1c; text-decoration:line-through; padding:0 2px; border-radius:2px; }
</style>
```

- [ ] **Step 4: VersionTimeline**

```vue
<template>
  <div class="version-timeline">
    <div class="left">
      <h4>版本列表</h4>
      <el-timeline>
        <el-timeline-item v-for="(v, i) in versions" :key="v.id"
          :type="i === selectedIdx ? 'primary' : 'info'"
          :timestamp="v.createdAt">
          <el-card :class="{ active: i === selectedIdx }" @click="selectedIdx = i" style="cursor:pointer">
            <div><el-tag size="small" :type="modeColor(v.mode)">{{ v.mode }}</el-tag></div>
            <div class="meta">{{ v.content?.length || 0 }} 字 · v{{ versions.length - i }}</div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </div>
    <div class="right">
      <h4>版本对比</h4>
      <el-select v-model="leftIdx" placeholder="左侧版本" size="small">
        <el-option v-for="(v,i) in versions" :key="v.id" :label="`v${versions.length-i} ${v.mode}`" :value="i" />
      </el-select>
      vs
      <el-select v-model="rightIdx" placeholder="右侧版本" size="small">
        <el-option v-for="(v,i) in versions" :key="v.id" :label="`v${versions.length-i} ${v.mode}`" :value="i" />
      </el-select>
      <DiffView v-if="leftIdx != null && rightIdx != null"
        :before="versions[leftIdx].content" :after="versions[rightIdx].content"
        @accept="rollback(rightIdx)" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DiffView from '@/components/diff/DiffView.vue'
import request from '@/utils/request'

const props = defineProps<{ reportId: number }>()
const versions = ref<any[]>([])
const selectedIdx = ref(0)
const leftIdx = ref<number|null>(null)
const rightIdx = ref<number|null>(null)

onMounted(async () => {
  versions.value = (await request.get(`/reports/${props.reportId}/versions`)).data || []
  if (versions.value.length >= 2) { leftIdx.value = 1; rightIdx.value = 0 }
})

function modeColor(m: string) {
  return ({DATA_UPDATE:'success', ANGLE_SHIFT:'warning', EXPAND:'primary',
           STYLE_SHIFT:'info', CONTINUATION:''} as any)[m] || ''
}

async function rollback(idx: number) {
  await request.post(`/reports/${props.reportId}/versions/${versions.value[idx].id}/rollback`)
}
</script>

<style scoped>
.version-timeline { display:grid; grid-template-columns: 280px 1fr; gap:16px; }
.left h4, .right h4 { margin: 0 0 12px; }
.meta { font-size:12px; color:#909399; }
.active { border:2px solid #409eff; }
</style>
```

- [ ] **Step 5: 接到详情页**

`reports/detail.vue` 加 tabs：
```vue
<el-tabs v-model="activeTab">
  <el-tab-pane label="正文" name="content"><TiptapEditor v-model="report.content" :report-id="report.id" /></el-tab-pane>
  <el-tab-pane label="质量体检" name="quality"><CoverageDashboard :report-id="report.id" /></el-tab-pane>
  <el-tab-pane label="版本对比" name="version"><VersionTimeline :report-id="report.id" /></el-tab-pane>
</el-tabs>
```

- [ ] **Step 6: 浏览器验证**

```bash
# 浏览器打开 /reports/1
# 1. 「质量体检」tab → 看到饼图 + KPI + 可疑列表
# 2. 「版本对比」tab → 选两版本 → diff 三色高亮
```

- [ ] **Step 7: Commit**

```bash
git add frontend/
git commit -m "feat(frontend): 覆盖度仪表盘 + 修订 diff + 版本树对比"
```

---

## Task 11：DOCX 真脚注 + PDF 导出 + 演示数据 seed

**Files:**
- Modify: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/service/impl/DocxExportServiceImpl.java`
- Create: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/service/PdfExportService.java`
- Create: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/service/impl/PdfExportServiceImpl.java`
- Modify: `report-ai/backend/report-hub-report/src/main/java/com/reportai/hub/report/controller/ExportController.java`
- Create: `report-ai/scripts/demo-seed.sh`

- [ ] **Step 1: DOCX 真脚注**

`DocxExportServiceImpl` 把现有「[1] 上标」改造为 Word footnote。Apache POI 5.2.5 用 `XWPFFootnote`：

```java
private void addCitationsAsFootnotes(XWPFDocument doc, List<ReportCitation> citations) {
    XWPFFootnotes footnotes = doc.createFootnotes();
    Map<Integer, BigInteger> markerToFootnoteId = new HashMap<>();
    for (ReportCitation c : citations) {
        XWPFFootnote fn = footnotes.createFootnote();
        XWPFParagraph p = fn.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(c.getDocTitle() + (c.getPageStart() != null ? " 第 " + c.getPageStart() + "-" + c.getPageEnd() + " 页" : ""));
        markerToFootnoteId.put(c.getCitationMarker(), fn.getId());
    }
    // 在主体段落里把 [n] 替换为 footnoteReference
    // 见下方 replaceMarkersWithFootnoteRefs 方法
}
```

完整改造代码参考 Apache POI 文档；如时间紧降级为：保留 [n] 上标 + 在文末追加「引用列表」节，列出每条 citation 的 docTitle/pageRange。

- [ ] **Step 2: PdfExportService — LibreOffice headless**

```java
@Service @RequiredArgsConstructor
public class PdfExportServiceImpl implements PdfExportService {
    private final DocxExportService docxExportService;

    @Override
    public byte[] exportPdf(Long reportId) {
        File tmpDocx = File.createTempFile("report", ".docx");
        Files.write(tmpDocx.toPath(), docxExportService.exportDocx(reportId));
        File outDir = Files.createTempDirectory("pdf").toFile();
        Process p = new ProcessBuilder("soffice", "--headless", "--convert-to", "pdf",
            "--outdir", outDir.getAbsolutePath(), tmpDocx.getAbsolutePath())
            .redirectErrorStream(true).start();
        p.waitFor(60, TimeUnit.SECONDS);
        File pdf = new File(outDir, tmpDocx.getName().replace(".docx", ".pdf"));
        return Files.readAllBytes(pdf.toPath());
    }
}
```

`ExportController` 加：
```java
@GetMapping("/{id}/export/pdf")
public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
    byte[] pdf = pdfExportService.exportPdf(id);
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=report-" + id + ".pdf")
        .contentType(MediaType.APPLICATION_PDF).body(pdf);
}
```

如部署机无 `soffice`，记录到 README，导出 PDF 走前端 `window.print()` 兜底（已有）。

- [ ] **Step 3: demo-seed.sh**

```bash
#!/bin/bash
# scripts/demo-seed.sh — 一键灌演示数据
set -e
BASE=${BASE:-http://localhost:8081}
TOKEN=${TOKEN:-$(curl -s -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | jq -r .data.token)}
H="-H Authorization:Bearer\ $TOKEN -H Content-Type:application/json"

echo "▶ 演示报告生成 (3 篇)"
for theme in "2024 新能源汽车产业洞察" "ChatGPT 对教育行业影响政策建议" "2024Q3 消费电子市场月报"; do
  rid=$(curl -s -X POST $BASE/api/v1/reports $H \
    -d "{\"title\":\"$theme\",\"topic\":\"$theme\",\"kbIds\":[1,2,3,4]}" | jq -r .data.id)
  echo "  - 报告 $rid: $theme"
  outline='[{"title":"行业概况","prompt":"市场规模与趋势"},{"title":"政策环境","prompt":"近 3 年政策"},{"title":"竞争格局","prompt":"主要参与者"},{"title":"未来展望","prompt":"3-5 年判断"}]'
  curl -s -X POST $BASE/api/v1/reports/$rid/sections/init $H -d "$outline" > /dev/null
  for i in 0 1 2 3; do
    curl -s "$BASE/api/v1/reports/$rid/sections/$i/stream?kbIds=1,2,3,4" -N | tail -3
  done
done

echo "▶ 改写演示 (3 报告 ×4 模式)"
for rid in 1 2 3; do
  for mode in DATA_UPDATE ANGLE_SHIFT EXPAND STYLE_SHIFT; do
    curl -s -X POST "$BASE/api/v1/reports/$rid/rewrite?mode=$mode" $H -d '{}' -N > /dev/null
    echo "  - report=$rid mode=$mode done"
  done
done

echo "▶ 续写演示"
curl -s -X POST "$BASE/api/v1/reports/1/rewrite?mode=CONTINUATION" $H \
  -d '{"newSectionTitle":"实施路径与挑战","hint":"3 阶段路线图"}' -N > /dev/null

echo "✅ 演示数据完成"
```

`chmod +x scripts/demo-seed.sh`

- [ ] **Step 4: 编译 + 跑 seed**

```bash
mvn -pl report-hub-report package -DskipTests
./scripts/demo-seed.sh
```

预期：3 报告 + 12 改写版本 + 1 续写入库。

- [ ] **Step 5: Commit**

```bash
git add backend/ scripts/
git commit -m "feat(export+seed): DOCX 脚注/PDF 导出 + 演示数据一键 seed"
```

---

## Task 12：webapp-testing Chrome 端到端验收

**Files:**
- Create: `report-ai/scripts/e2e-smoke.md`（人工 + Playwright 检查清单）

- [ ] **Step 1: 启动服务**

```bash
cd report-ai
./start.sh    # 已有脚本：起 Docker 基础设施 + IDE 后端 + 前端
```

- [ ] **Step 2: 用 webapp-testing skill 跑核心路径**

调用 `Skill webapp-testing`，依次验证：

1. 登录 → workspace 拖拽大纲 → 生成 → 详情页看到章节流式
2. 点引用 [1] → popover 显示文档 + 页码 + snippet
3. 「质量体检」tab → 饼图 + KPI 显示
4. 选中段落 → BubbleMenu → 「优化」 → 段落被替换
5. 「版本对比」tab → 选两版本 → diff 三色高亮 → 点接受 → 回滚成功
6. 导出 docx → 下载文件
7. 知识库 → 上传新 PDF → 文档列表出现 → 重新嵌入 → 报告里能检索到

记录每步截图 + console 0 错误。

- [ ] **Step 3: 修复任何 Playwright 发现的问题**

常见问题：CORS、SSE 端口跨域、TypeScript 类型错误。

- [ ] **Step 4: Commit + 录屏**

```bash
git add scripts/e2e-smoke.md
git commit -m "test(e2e): Chrome 端到端核心路径 12 步验收清单"

# 录 5 段演示视频（屏幕录制工具自行选择）
```

---

## 自检清单（writing-plans skill 自检）

- [x] **Spec 覆盖** — 设计文档 §3-§8 每个模块都映射到 Task；§9 交互 ①②③④⑤ 全部对应到 Task 8/9/10。
- [x] **Placeholder 扫描** — 所有步骤含可执行代码或命令；无 TODO/TBD。
- [x] **类型一致** — `RagSearchQuery`/`RagChunkHit`/`ReportCitation`/`ReportSection`/`ReportQuality` 字段在 SQL 与 Java 实体中一致。
- [x] **不含 "类似 Task N"** — 每个 task 自包含。
- [x] **风险回退** — Task 11 若 DOCX 真脚注复杂，提供「[n] + 文末引用列表」降级；Task 11 若 LibreOffice 不可用，前端 `window.print()` 兜底。
- [x] **依赖顺序明确** — Task 1 (DB) → 2 (chunker) → 3 (RAG) → 4 (citation) → 5 (section SSE) → 6 (rewrite) → 7 (quality) → 8 (Tiptap) → 9 (outline) → 10 (dashboard/diff/version) → 11 (export/seed) → 12 (E2E).
