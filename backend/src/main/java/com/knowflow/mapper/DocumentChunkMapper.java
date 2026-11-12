package com.knowflow.mapper;

import com.knowflow.entity.DocumentChunk;
import com.knowflow.entity.RetrievableChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentChunkMapper {

    int insert(DocumentChunk chunk);

    int deleteByDocumentId(@Param("documentId") Long documentId);

    List<RetrievableChunk> findReadyByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    List<RetrievableChunk> findReadyByDocumentId(@Param("documentId") Long documentId);

    List<RetrievableChunk> findByIds(@Param("ids") List<Long> ids);

    /**
     * MySQL 全文检索降级通道：document_chunk 上有 ngram 解析的 FULLTEXT 索引 ft_chunk_content。
     */
    List<RetrievableChunk> searchFulltext(
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("query") String query,
            @Param("embeddingModel") String embeddingModel,
            @Param("limit") int limit
    );

    int markVectorIndexState(@Param("documentId") Long documentId, @Param("state") String state);
}
