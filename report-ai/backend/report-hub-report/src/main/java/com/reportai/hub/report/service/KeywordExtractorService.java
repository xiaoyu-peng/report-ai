package com.reportai.hub.report.service;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KeywordExtractorService {

    private static final Set<String> STOP_WORDS;

    static {
        Set<String> words = new HashSet<>();
        words.addAll(Arrays.asList(
            "的", "了", "和", "是", "就", "都", "而", "及", "与", "着",
            "或", "一个", "没有", "我们", "你们", "他们", "它们", "这个",
            "那个", "这些", "那些", "什么", "怎么", "如何", "为什么",
            "关于", "对于", "通过", "进行", "分析", "研究", "报告",
            "年度", "季度", "月度", "周", "日", "年", "月",
            "第一", "第二", "第三", "最后", "最新", "近期",
            "中国", "全国", "全省", "全市", "地区",
            "一", "二", "三", "四", "五", "六", "七", "八", "九", "十",
            "个", "条", "件", "项", "次", "回", "遍", "番",
            "这", "那", "此", "彼", "每", "各", "某", "本", "该",
            "之", "以", "为", "于", "在", "从", "到", "向", "往",
            "上", "下", "前", "后", "左", "右", "内", "外", "中",
            "要", "将", "把", "被", "让", "给", "跟", "比", "等",
            "可以", "可能", "应该", "必须", "需要", "能够", "已经",
            "但", "却", "又", "也", "还", "再", "才",
            "很", "太", "更", "最", "非常", "十分", "比较", "相当"
        ));
        STOP_WORDS = Collections.unmodifiableSet(words);
    }

    private static final Set<String> VALID_POS_TAGS = Set.of(
        "n", "nr", "ns", "nt", "nz", "ng", "nl", "nm",
        "v", "vd", "vn", "vg", "vi", "vq",
        "a", "ad", "an", "ag", "al",
        "j", "l", "i", "g", "h", "k"
    );

    public List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<Term> terms = HanLP.segment(text);

        Set<String> keywords = terms.stream()
            .filter(this::isValidKeyword)
            .map(term -> term.word.trim())
            .filter(word -> word.length() >= 2 && word.length() <= 10)
            .filter(word -> !isStopWord(word))
            .filter(word -> !isNumber(word))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> result = new ArrayList<>(keywords);
        if (result.size() > 6) {
            result = result.subList(0, 6);
        }

        log.debug("HanLP extracted keywords from '{}': {}", text, result);
        return result;
    }

    public List<String> extractKeywordsForSearch(String title, String topic) {
        Set<String> allKeywords = new LinkedHashSet<>();

        if (title != null && !title.isBlank()) {
            allKeywords.addAll(extractKeywords(title));
        }

        if (topic != null && !topic.isBlank()) {
            allKeywords.addAll(extractKeywords(topic));
        }

        List<String> result = new ArrayList<>(allKeywords);
        if (result.size() > 8) {
            result = result.subList(0, 8);
        }

        return result;
    }

    public String buildSearchQuery(String title, String topic) {
        List<String> keywords = extractKeywordsForSearch(title, topic);
        if (keywords.isEmpty()) {
            return topic != null ? topic : "";
        }
        return String.join(" ", keywords);
    }

    private boolean isValidKeyword(Term term) {
        if (term == null || term.word == null || term.word.isBlank()) {
            return false;
        }
        String pos = term.nature != null ? term.nature.toString() : "";
        return VALID_POS_TAGS.stream().anyMatch(pos::startsWith);
    }

    private boolean isStopWord(String word) {
        return STOP_WORDS.contains(word.toLowerCase());
    }

    private boolean isNumber(String word) {
        return word.matches("^\\d+(\\.\\d+)?%?$");
    }
}
