package com.knowflow.rag.vector;

import com.knowflow.config.RagProperties;
import com.knowflow.rag.redis.RedisCommandException;
import com.knowflow.rag.redis.RedisRespClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis Stack（RediSearch）向量索引。
 *
 * 索引结构：HASH + HNSW，向量字段 FLOAT32，距离度量 COSINE。
 * 检索使用 KNN 查询：FT.SEARCH idx "(@knowledge_base_id:{..})=>[KNN k @embedding $vec AS vector_score]"
 * Redis 返回的是余弦距离，这里换算成相似度 1 - distance，保证与 MySQL 降级索引口径一致。
 */
public class RedisStackVectorIndex implements VectorIndex {

    private static final Logger log = LoggerFactory.getLogger(RedisStackVectorIndex.class);

    /**
     * 单批删除条数。取值只影响往返次数，不影响正确性：循环会一直删到查不到为止。
     */
    private static final int DELETE_BATCH_SIZE = 500;

    private final RagProperties.Redis config;
    private final int dimensions;
    private volatile boolean indexReady;

    public RedisStackVectorIndex(RagProperties.Redis config, int dimensions) {
        this.config = config;
        this.dimensions = dimensions;
    }

    @Override
    public String type() {
        return "redis-stack";
    }

    @Override
    public boolean available() {
        try (RedisRespClient client = connect()) {
            Object pong = client.command("PING");
            if (pong == null) {
                return false;
            }
            if (hasSearchModule(client)) {
                return true;
            }
            // Redis 8 把向量检索内置，不再以模块形式暴露：用 FT._LIST 探测。
            try {
                client.command("FT._LIST");
                return true;
            } catch (RedisCommandException ex) {
                return false;
            }
        } catch (RuntimeException ex) {
            log.debug("Redis Stack 不可用: {}", ex.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasSearchModule(RedisRespClient client) {
        Object modules = client.command("MODULE", "LIST");
        if (!(modules instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (!(item instanceof List<?> entries)) {
                continue;
            }
            for (int i = 0; i + 1 < entries.size(); i += 2) {
                if ("name".equals(asText(entries.get(i))) && "search".equalsIgnoreCase(asText(entries.get(i + 1)))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<VectorHit> search(Long knowledgeBaseId, String embeddingModel, float[] queryVector, int topK) {
        if (queryVector == null || queryVector.length != dimensions) {
            throw new IllegalArgumentException("查询向量维度必须为 " + dimensions);
        }
        try (RedisRespClient client = connect()) {
            ensureIndex(client);
            byte[] vectorBytes = toBytes(queryVector);
            String filter = "(@knowledge_base_id:{" + knowledgeBaseId + "} @embedding_model:{"
                    + escapeTag(embeddingModel) + "})=>[KNN " + topK + " @embedding $vec AS vector_score]";
            Object replyObject = client.commandRaw(
                    bytes("FT.SEARCH"),
                    bytes(config.getIndexName()),
                    bytes(filter),
                    bytes("PARAMS"), bytes("2"), bytes("vec"), vectorBytes,
                    bytes("SORTBY"), bytes("vector_score"),
                    bytes("RETURN"), bytes("3"), bytes("chunk_id"), bytes("vector_score"), bytes("document_id"),
                    bytes("DIALECT"), bytes("2"),
                    bytes("LIMIT"), bytes("0"), bytes(String.valueOf(topK))
            );
            return parseHits(replyObject);
        }
    }

    @SuppressWarnings("unchecked")
    private List<VectorHit> parseHits(Object replyObject) {
        List<VectorHit> hits = new ArrayList<>();
        if (!(replyObject instanceof List<?> reply) || reply.isEmpty()) {
            return hits;
        }
        // 应答结构：[总数, key1, [field, value, ...], key2, [field, value, ...], ...]
        for (int i = 1; i + 1 < reply.size(); i += 2) {
            Object fieldsObject = reply.get(i + 1);
            if (!(fieldsObject instanceof List<?> fields)) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int f = 0; f + 1 < fields.size(); f += 2) {
                values.put(asText(fields.get(f)), asText(fields.get(f + 1)));
            }
            String chunkIdText = values.get("chunk_id");
            if (chunkIdText == null) {
                continue;
            }
            Long chunkId = Long.parseLong(chunkIdText);
            double distance = parseDouble(values.get("vector_score"));
            double similarity = 1.0d - distance;
            hits.add(new VectorHit(chunkId, similarity, type()));
        }
        return hits;
    }

    @Override
    public void upsert(List<ChunkVector> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        try (RedisRespClient client = connect()) {
            ensureIndex(client);
            for (ChunkVector chunk : chunks) {
                if (chunk.getVector() == null || chunk.getVector().length != dimensions) {
                    throw new IllegalArgumentException("分块 " + chunk.getChunkId() + " 向量维度不匹配");
                }
                Map<String, byte[]> fields = new LinkedHashMap<>();
                fields.put("chunk_id", bytes(String.valueOf(chunk.getChunkId())));
                fields.put("knowledge_base_id", bytes(String.valueOf(chunk.getKnowledgeBaseId())));
                fields.put("document_id", bytes(String.valueOf(chunk.getDocumentId())));
                fields.put("chunk_index", bytes(String.valueOf(chunk.getChunkIndex())));
                fields.put("embedding_model", bytes(chunk.getEmbeddingModel()));
                fields.put("content", bytes(chunk.getContent() == null ? "" : chunk.getContent()));
                fields.put("embedding", toBytes(chunk.getVector()));

                List<byte[]> command = new ArrayList<>();
                command.add(bytes("HSET"));
                command.add(bytes(config.getKeyPrefix() + chunk.getChunkId()));
                for (Map.Entry<String, byte[]> entry : fields.entrySet()) {
                    command.add(bytes(entry.getKey()));
                    command.add(entry.getValue());
                }
                client.commandRaw(command.toArray(new byte[0][]));
            }
            log.debug("已写入 Redis Stack 向量 {} 条", chunks.size());
        }
    }

    @Override
    public void deleteByDocument(Long documentId) {
        deleteByFilter("@document_id:[" + documentId + " " + documentId + "]", "documentId=" + documentId);
    }

    @Override
    public void deleteByKnowledgeBase(Long knowledgeBaseId) {
        deleteByFilter("@knowledge_base_id:{" + knowledgeBaseId + "}", "knowledgeBaseId=" + knowledgeBaseId);
    }

    @Override
    public int countByKnowledgeBase(Long knowledgeBaseId) {
        try (RedisRespClient client = connect()) {
            ensureIndex(client);
            Object replyObject = client.command(
                    "FT.SEARCH",
                    config.getIndexName(),
                    "@knowledge_base_id:{" + knowledgeBaseId + "}",
                    "RETURN", "0",
                    "LIMIT", "0", "0"
            );
            return parseTotal(replyObject);
        } catch (RedisCommandException ex) {
            throw new RedisCommandException("统计 Redis 向量条数失败（knowledgeBaseId=" + knowledgeBaseId + "）："
                    + ex.getMessage(), ex);
        }
    }

    /**
     * 分批删除：每轮只取一批 key 再 DEL，然后重新从 0 开始查。
     *
     * <p>删除会让剩余条数变少，所以这里刻意不用递增 OFFSET —— 否则每删一批，后面的数据整体前移，
     * 用 OFFSET 分页会跳过一批，删到 1000 条以上时静默漏删。用「LIMIT 0、反复删到查不到」的方式，
     * 无论多少条都能删干净，并且天然幂等（重复执行第二次直接返回 0 条）。</p>
     *
     * <p>任何一次 FT.SEARCH 或 DEL 失败都会抛出 {@link RedisCommandException} 并带上已删条数，
     * 不再只写一条 warn 日志让调用方误以为清理成功。</p>
     */
    private void deleteByFilter(String filter, String target) {
        int deleted = 0;
        try (RedisRespClient client = connect()) {
            if (!indexExists(client)) {
                // 索引还没建过，说明没有任何向量可删。
                return;
            }
            while (true) {
                List<byte[]> keys = searchKeys(client, filter, DELETE_BATCH_SIZE);
                if (keys.isEmpty()) {
                    break;
                }
                long removed = deleteKeys(client, keys);
                deleted += keys.size();
                if (removed <= 0 && keys.isEmpty()) {
                    break;
                }
            }
        } catch (RedisCommandException ex) {
            throw new RedisCommandException("删除 Redis 向量失败（" + target + "，已删除 " + deleted + " 条）："
                    + ex.getMessage(), ex);
        }
        if (deleted > 0) {
            log.info("已清理 Redis 向量 {} 条（{}）", deleted, target);
        }
    }

    private boolean indexExists(RedisRespClient client) {
        try {
            client.command("FT.INFO", config.getIndexName());
            indexReady = true;
            return true;
        } catch (RedisCommandException ex) {
            if (ex.isUnknownIndex()) {
                return false;
            }
            throw ex;
        }
    }

    private List<byte[]> searchKeys(RedisRespClient client, String filter, int limit) {
        Object replyObject = client.command(
                "FT.SEARCH",
                config.getIndexName(),
                filter,
                "RETURN", "0",
                "LIMIT", "0", String.valueOf(limit)
        );
        return parseKeys(replyObject);
    }

    private long deleteKeys(RedisRespClient client, List<byte[]> keys) {
        List<byte[]> command = new ArrayList<>(keys.size() + 1);
        command.add(bytes("DEL"));
        command.addAll(keys);
        Object replyObject = client.commandRaw(command.toArray(new byte[0][]));
        if (replyObject instanceof Number number) {
            return number.longValue();
        }
        return keys.size();
    }

    private static int parseTotal(Object replyObject) {
        if (replyObject instanceof List<?> reply && !reply.isEmpty()) {
            Object total = reply.get(0);
            if (total instanceof Number number) {
                return number.intValue();
            }
            if (total instanceof byte[] bytes) {
                try {
                    return Integer.parseInt(asText(bytes));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private List<byte[]> parseKeys(Object replyObject) {
        List<byte[]> keys = new ArrayList<>();
        if (!(replyObject instanceof List<?> reply)) {
            return keys;
        }
        // FT.SEARCH 的回复有两种形状：RETURN 0 时是 [总数, key1, key2, ...]，
        // 带字段时是 [总数, key1, [字段, 值, ...], key2, [...]]。逐项取字节串，跳过字段数组，
        // 不能按固定步长跳，否则会把一半 key 漏掉。
        for (int i = 1; i < reply.size(); i++) {
            Object key = reply.get(i);
            if (key instanceof byte[] bytes) {
                keys.add(bytes);
            } else if (key instanceof List<?>) {
                continue;
            } else if (key != null) {
                keys.add(bytes(asText(key)));
            }
        }
        return keys;
    }

    private void ensureIndex(RedisRespClient client) {
        if (indexReady) {
            return;
        }
        synchronized (this) {
            if (indexReady) {
                return;
            }
            try {
                client.command("FT.INFO", config.getIndexName());
                indexReady = true;
                return;
            } catch (RedisCommandException ex) {
                if (!ex.isUnknownIndex()) {
                    throw ex;
                }
            }
            try {
                client.command(
                        "FT.CREATE", config.getIndexName(),
                        "ON", "HASH",
                        "PREFIX", "1", config.getKeyPrefix(),
                        "SCHEMA",
                        "chunk_id", "NUMERIC",
                        "knowledge_base_id", "TAG",
                        "document_id", "NUMERIC",
                        "chunk_index", "NUMERIC",
                        "embedding_model", "TAG",
                        "content", "TEXT",
                        "embedding", "VECTOR", "HNSW", "6",
                        "TYPE", "FLOAT32",
                        "DIM", String.valueOf(dimensions),
                        "DISTANCE_METRIC", "COSINE"
                );
                log.info("已创建 Redis Stack 向量索引 {}，维度 {}", config.getIndexName(), dimensions);
            } catch (RedisCommandException ex) {
                if (!ex.isIndexAlreadyExists()) {
                    throw ex;
                }
            }
            indexReady = true;
        }
    }

    private RedisRespClient connect() {
        return RedisRespClient.connect(
                config.getHost(),
                config.getPort(),
                config.getPassword(),
                config.getDatabase(),
                config.getTimeoutMs()
        );
    }

    private static String escapeTag(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    private static byte[] toBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String asText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private static double parseDouble(String value) {
        if (value == null) {
            return 1.0d;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 1.0d;
        }
    }
}
