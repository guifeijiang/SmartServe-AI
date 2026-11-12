-- Incremental RAG embedding columns. Do not modify V1/V2/V3.

ALTER TABLE document_chunk
    ADD COLUMN embedding_json JSON NULL AFTER token_count,
    ADD COLUMN embedding_model VARCHAR(64) NULL AFTER embedding_json;
