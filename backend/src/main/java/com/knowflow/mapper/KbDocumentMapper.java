package com.knowflow.mapper;

import com.knowflow.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbDocumentMapper {

    int insert(KbDocument document);

    KbDocument findActiveById(@Param("id") Long id);

    List<KbDocument> findActiveByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    int softDeleteById(@Param("id") Long id);

    int updateProcessResult(
            @Param("id") Long id,
            @Param("processStatus") String processStatus,
            @Param("processError") String processError,
            @Param("chunkCount") int chunkCount
    );
}
