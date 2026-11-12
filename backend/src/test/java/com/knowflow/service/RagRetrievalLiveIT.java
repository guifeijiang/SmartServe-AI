package com.knowflow.service;

import com.knowflow.rag.RagRetrievalService;
import com.knowflow.rag.RetrievalOutcome;
import com.knowflow.rag.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实环境检索集成测试（不在默认 Surefire 中执行，类名以 IT 结尾）。
 *
 * 依赖：
 * - MySQL knowflow_ai（含已处理的知识库 5，正文里有专属编号 SSAI-REFUND-SAMPLE）
 * - Ollama bge-m3（向量化）与 deepseek-r1:1.5b（LLM 精排）
 * - 环境变量 KNOWFLOW_DB_USERNAME / KNOWFLOW_DB_PASSWORD
 *
 * 执行：mvn -Dtest=RagRetrievalLiveIT test
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "knowflow.rocketmq.enabled=false",
                "knowflow.rocketmq.consumer-enabled=false",
                "knowflow.rocketmq.outbox-dispatch-enabled=false"
        }
)
@ActiveProfiles("local")
class RagRetrievalLiveIT {

    @Autowired
    private RagRetrievalService ragRetrievalService;

    @Test
    void retrievesPolicyChunkWithRealPipeline() {
        RetrievalOutcome outcome = ragRetrievalService.retrieve(5L, "这项售后政策的处理时限、退款材料和专属测试编号是什么？");

        assertNotNull(outcome);
        System.out.println("LIVE_MODE=" + outcome.getRetrievalMode()
                + " degraded=" + outcome.isDegraded()
                + " reason=" + outcome.getDegradeReason()
                + " index=" + outcome.getIndexType()
                + " recall=" + outcome.getRecalledCount()
                + " rerank=" + outcome.getRerankStrategy());
        for (RetrievedChunk chunk : outcome.getChunks()) {
            System.out.println("LIVE_CITE chunk=" + chunk.getChunkId()
                    + " doc=" + chunk.getDocumentName()
                    + " vectorScore=" + chunk.getVectorScore()
                    + " rerankScore=" + chunk.getRerankScore());
            System.out.println(chunk.getContent());
        }
        assertFalse(outcome.getChunks().isEmpty(), "知识库 5 应检索到分块");
        assertTrue(
                outcome.getChunks().stream().anyMatch(c -> c.getContent().contains("SSAI-REFUND-SAMPLE")),
                "精排后的 Top 片段应包含专属测试编号"
        );
    }
}
