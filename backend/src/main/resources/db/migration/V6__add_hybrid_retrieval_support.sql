-- V6: 支撑 Redis Stack 向量索引 + MySQL 全文检索降级 + 检索诊断留痕。
-- 只做增量变更，V1–V5 不允许修改。

-- 1) 中文全文检索索引：向量召回相似度过低或向量不可用时降级到此通道。
--    ngram 解析器把中文按 2 字切分，无需额外分词插件。
ALTER TABLE document_chunk
    ADD FULLTEXT INDEX ft_chunk_content (content) WITH PARSER ngram;

-- 2) 向量索引同步状态：MySQL 仍是分块元数据与引用的权威存储，Redis Stack 只做相似度检索。
ALTER TABLE document_chunk
    ADD COLUMN embedding_dim INT NULL AFTER embedding_model,
    ADD COLUMN vector_index_state VARCHAR(16) NOT NULL DEFAULT 'PENDING' AFTER embedding_dim,
    ADD COLUMN vector_indexed_at TIMESTAMP NULL DEFAULT NULL AFTER vector_index_state;

-- 3) 会话消息保留本轮检索诊断（召回方式、是否降级、精排策略），用于历史记录追溯。
ALTER TABLE chat_message
    ADD COLUMN retrieval_meta JSON NULL AFTER cited_chunk_ids;
