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
}
