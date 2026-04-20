-- 把 knowledge_chunk 的 FULLTEXT 索引换成 NGRAM parser（ngram_token_size=2）
-- 默认 InnoDB parser 对 CJK 分词几乎失效：LIKE '%网络安全%' 能匹 80 条，而
-- MATCH '+网络安全' BOOLEAN MODE 只匹 2 条，因为默认 parser 不会把连续中文
-- 文本切成独立 token。
--
-- NGRAM parser 会把 "网络安全" 自动切成 网络/络安/安全 三个 bigram，
-- 所以 BOOLEAN MODE 不加 `+` 也能按 bigram 模糊打分召回，长 topic 不再丢。
--
-- 配套 Java 改动见 RagSearchServiceImpl.toBooleanModeExpression：
-- 不再逐字拆并加 `+`，改成空白分词后整体交给 FULLTEXT NGRAM 内部切分。
ALTER TABLE knowledge_chunk DROP INDEX ft_content;
ALTER TABLE knowledge_chunk ADD FULLTEXT INDEX ft_content (content) WITH PARSER ngram;
