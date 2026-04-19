package com.reportai.hub.knowledge.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块器：段落优先，带溢出合并与滑动窗口。
 *
 * <p>策略：
 * <ol>
 *   <li>先按双换行切段落（PDF / Word 抽文本后的自然段）；</li>
 *   <li>段落超过 maxChars 再按句号/句号/问号切；</li>
 *   <li>相邻块之间保留 overlap 字符，缓解 RAG 片段被切断。</li>
 * </ol>
 */
@Component
public class TextChunker {

    private static final int DEFAULT_MAX = 500;
    private static final int DEFAULT_OVERLAP = 50;

    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_MAX, DEFAULT_OVERLAP);
    }

    public List<String> chunk(String text, int maxChars, int overlap) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;

        String normalized = text.replace("\r\n", "\n").replaceAll("\n{3,}", "\n\n");
        String[] paragraphs = normalized.split("\n\n");

        StringBuilder buf = new StringBuilder();
        for (String para : paragraphs) {
            String p = para.trim();
            if (p.isEmpty()) continue;

            if (p.length() > maxChars) {
                // 段落超长，先 flush 现有 buffer
                if (buf.length() > 0) {
                    out.add(buf.toString());
                    buf.setLength(0);
                }
                out.addAll(splitLong(p, maxChars, overlap));
                continue;
            }

            if (buf.length() + p.length() + 1 > maxChars) {
                out.add(buf.toString());
                // 下一块带上一块末尾 overlap 字符
                String tail = buf.length() > overlap
                        ? buf.substring(buf.length() - overlap)
                        : buf.toString();
                buf.setLength(0);
                buf.append(tail);
            }
            if (buf.length() > 0) buf.append('\n');
            buf.append(p);
        }
        if (buf.length() > 0) out.add(buf.toString());
        return out;
    }

    /** 把单个超长段落按句号切，保留 overlap。 */
    private List<String> splitLong(String para, int maxChars, int overlap) {
        List<String> out = new ArrayList<>();
        String[] sentences = para.split("(?<=[。！？!?.])");
        StringBuilder buf = new StringBuilder();
        for (String s : sentences) {
            if (s.isEmpty()) continue;
            if (buf.length() + s.length() > maxChars && buf.length() > 0) {
                out.add(buf.toString());
                String tail = buf.length() > overlap
                        ? buf.substring(buf.length() - overlap)
                        : buf.toString();
                buf.setLength(0);
                buf.append(tail);
            }
            buf.append(s);
        }
        if (buf.length() > 0) out.add(buf.toString());
        return out;
    }

    /** 页码感知分块的返回体。pageStart/pageEnd 1-based，跨页时记全范围。 */
    public record PageAwareChunk(String text, int pageStart, int pageEnd) {}

    /**
     * 页码感知切块：输入是"每页正文"的列表（1-based 语义），输出每个 chunk 带起止页码。
     *
     * <p>算法：
     * <ol>
     *   <li>展平每页为段落，记录段落原始页号；</li>
     *   <li>复用既有 chunk() 的拼接规则，但每次 flush 时把"本 chunk 包含的段落最早/最晚页"写入。</li>
     * </ol>
     * 这样跨页合并的块会得到 pageStart=3, pageEnd=4 之类；单页块 pageStart=pageEnd。
     */
    public List<PageAwareChunk> chunkByPage(List<String> pages) {
        return chunkByPage(pages, DEFAULT_MAX, DEFAULT_OVERLAP);
    }

    public List<PageAwareChunk> chunkByPage(List<String> pages, int maxChars, int overlap) {
        List<PageAwareChunk> out = new ArrayList<>();
        if (pages == null || pages.isEmpty()) return out;

        // 展平成 (paragraph, pageNo) 序列
        record ParaWithPage(String text, int page) {}
        List<ParaWithPage> paras = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            String normalized = pages.get(i) == null ? "" :
                    pages.get(i).replace("\r\n", "\n").replaceAll("\n{3,}", "\n\n");
            for (String p : normalized.split("\n\n")) {
                String t = p.trim();
                if (!t.isEmpty()) paras.add(new ParaWithPage(t, i + 1));
            }
        }

        StringBuilder buf = new StringBuilder();
        int bufStart = -1, bufEnd = -1;

        for (ParaWithPage pw : paras) {
            String p = pw.text();
            int page = pw.page();

            if (p.length() > maxChars) {
                if (buf.length() > 0) {
                    out.add(new PageAwareChunk(buf.toString(), bufStart, bufEnd));
                    buf.setLength(0);
                    bufStart = bufEnd = -1;
                }
                // 超长段落本身落在单页内（PDF 段落通常不会跨页被抽出），同页拆多块
                for (String piece : splitLong(p, maxChars, overlap)) {
                    out.add(new PageAwareChunk(piece, page, page));
                }
                continue;
            }

            if (buf.length() + p.length() + 1 > maxChars) {
                out.add(new PageAwareChunk(buf.toString(), bufStart, bufEnd));
                String tail = buf.length() > overlap
                        ? buf.substring(buf.length() - overlap)
                        : buf.toString();
                buf.setLength(0);
                buf.append(tail);
                // overlap 带进下一个 chunk 时，pageStart 沿用上个 chunk 的 bufEnd（它就来自上一段）
                bufStart = bufEnd;
            }
            if (buf.length() > 0) buf.append('\n');
            buf.append(p);
            if (bufStart < 0) bufStart = page;
            bufEnd = page;
        }
        if (buf.length() > 0) out.add(new PageAwareChunk(buf.toString(), bufStart, bufEnd));
        return out;
    }
}
