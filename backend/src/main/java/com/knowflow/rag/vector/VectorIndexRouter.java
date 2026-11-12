package com.knowflow.rag.vector;

import com.knowflow.config.RagProperties;
import com.knowflow.mapper.DocumentChunkMapper;
import com.knowflow.service.EmbeddingService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 向量索引选择器：启动时探测 Redis Stack（需要 RediSearch 模块）是否可用。
 *
 * auto：Redis Stack 可用则用真正的向量检索，否则回退到 MySQL 余弦索引；
 * redis：强制 Redis Stack，探测失败也会回退，但会在日志和诊断信息中说明原因；
 * mysql：强制走降级索引。
 */
@Component
public class VectorIndexRouter {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexRouter.class);

    private final RagProperties ragProperties;
    private final DocumentChunkMapper documentChunkMapper;
    private final EmbeddingService embeddingService;

    private volatile VectorIndex active;
    private volatile String selectionReason = "";

    public VectorIndexRouter(
            RagProperties ragProperties,
            DocumentChunkMapper documentChunkMapper,
            EmbeddingService embeddingService
    ) {
        this.ragProperties = ragProperties;
        this.documentChunkMapper = documentChunkMapper;
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    void init() {
        VectorIndex mysqlIndex = new MysqlCosineVectorIndex(documentChunkMapper, embeddingService);
        VectorIndex redisIndex = new RedisStackVectorIndex(ragProperties.getRedis(), embeddingService.dimensions());
        String mode = ragProperties.getVectorStore() == null ? "auto" : ragProperties.getVectorStore().trim();
        if ("mysql".equalsIgnoreCase(mode)) {
            active = mysqlIndex;
            selectionReason = "配置强制使用 MySQL 余弦索引";
        } else {
            boolean redisAvailable = redisIndex.available();
            if (redisAvailable) {
                active = redisIndex;
                selectionReason = "Redis Stack 可用";
            } else {
                active = mysqlIndex;
                selectionReason = "Redis Stack 不可用（未安装 RediSearch 或连接失败），已回退 MySQL 余弦索引";
            }
            if ("redis".equalsIgnoreCase(mode) && !redisAvailable) {
                log.error("knowflow.rag.vector-store=redis 但 Redis Stack 不可用，本次运行回退 MySQL 余弦索引");
            }
        }
        log.info("向量索引实现: {}，原因: {}", active.type(), selectionReason);
    }

    public VectorIndex active() {
        if (active == null) {
            init();
        }
        return active;
    }

    public String type() {
        return active().type();
    }

    public String selectionReason() {
        return selectionReason;
    }
}
