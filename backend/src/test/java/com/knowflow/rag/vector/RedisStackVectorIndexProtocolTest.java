package com.knowflow.rag.vector;

import com.knowflow.config.RagProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用本地假 Redis 服务端校验 RedisStackVectorIndex 的协议层：
 * 索引结构、HSET 字段、KNN 查询参数、应答解析、按文档/知识库删除的 key 解析。
 *
 * <p>这里不连接真实 Redis，覆盖的是「客户端发出去的命令长什么样」与「回来怎么解析」；
 * RediSearch 服务端的真实行为仍需 Redis Stack 环境验证。</p>
 */
class RedisStackVectorIndexProtocolTest {

    private FakeRedisServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new FakeRedisServer();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void createsIndexWithHnswCosineSchemaOnFirstUse() {
        server.respondWith(command -> switch (commandName(command)) {
            case "FT.INFO" -> "-Unknown index name\r\n";
            case "FT.CREATE" -> "+OK\r\n";
            default -> "+OK\r\n";
        });

        RedisStackVectorIndex index = index();
        index.upsert(List.of(new ChunkVector(101L, 6L, 4L, 0, "bge-m3", "退款政策正文", new float[1024])));

        List<List<byte[]>> commands = server.commands();
        assertThat(commands).hasSize(3);
        List<String> create = texts(commands.get(1));
        assertThat(create).containsSubsequence("FT.CREATE", "knowflow:chunk:idx", "ON", "HASH", "PREFIX", "1",
                "knowflow:chunk:", "SCHEMA");
        assertThat(create).containsSubsequence("embedding", "VECTOR", "HNSW");
        assertThat(create).containsSubsequence("TYPE", "FLOAT32", "DIM", "1024", "DISTANCE_METRIC", "COSINE");
        assertThat(create).containsSubsequence("knowledge_base_id", "TAG");
        assertThat(create).containsSubsequence("document_id", "NUMERIC");
    }

    @Test
    void upsertWritesHashFieldsWithBinaryVector() {
        server.respondWith(command -> switch (commandName(command)) {
            case "FT.INFO" -> "-Unknown index name\r\n";
            case "FT.CREATE" -> "+OK\r\n";
            case "HSET" -> ":1\r\n";
            default -> "+OK\r\n";
        });

        float[] vector = new float[1024];
        vector[0] = 0.1f;
        index().upsert(List.of(new ChunkVector(101L, 6L, 4L, 0, "bge-m3", "退款政策正文", vector)));

        List<List<byte[]>> commands = server.commands();
        List<byte[]> hset = commands.get(commands.size() - 1);
        assertThat(commandName(hset)).isEqualTo("HSET");
        assertThat(text(hset.get(1))).isEqualTo("knowflow:chunk:101");

        List<String> fields = texts(hset.subList(2, hset.size()));
        assertThat(fields).contains("chunk_id", "101", "knowledge_base_id", "6", "document_id", "4",
                "chunk_index", "0", "embedding_model", "bge-m3", "content", "退款政策正文");
        assertThat(fields).contains("embedding");
        // 向量的二进制必须原样传输：1024 维 FLOAT32 = 4096 字节。
        byte[] embedding = hset.get(hset.size() - 1);
        assertThat(embedding).hasSize(4096);
    }

    @Test
    void searchParsesHitsAndConvertsCosineDistanceToSimilarity() {
        String fields = arr(bulk("chunk_id"), bulk("101"), bulk("vector_score"), bulk("0.2"),
                bulk("document_id"), bulk("4"));
        String reply = arr(bulk("1"), bulk("knowflow:chunk:101"), fields);
        server.respondWith(command -> switch (commandName(command)) {
            case "FT.INFO" -> "+OK\r\n";
            case "FT.SEARCH" -> reply;
            default -> "+OK\r\n";
        });

        float[] query = new float[1024];
        query[0] = 1.0f;
        List<VectorHit> hits = index().search(6L, "bge-m3", query, 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getChunkId()).isEqualTo(101L);
        assertThat(hits.get(0).getSimilarity()).isCloseTo(0.8d, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(hits.get(0).getSource()).isEqualTo("redis-stack");

        List<byte[]> search = server.commands().get(server.commands().size() - 1);
        List<String> args = texts(search);
        assertThat(args.get(0)).isEqualTo("FT.SEARCH");
        assertThat(args.get(1)).isEqualTo("knowflow:chunk:idx");
        assertThat(args.get(2))
                .contains("@knowledge_base_id:{6}")
                // 模型名里的 '-' 必须转义，否则 RediSearch 会把 bge-m3 当成两个词。
                .contains("@embedding_model:{bge\\-m3}")
                .contains("KNN 5")
                .contains("AS vector_score");
        assertThat(args).containsSubsequence("PARAMS", "2", "vec");
        assertThat(args).containsSubsequence("SORTBY", "vector_score");
        assertThat(args).containsSubsequence("DIALECT", "2", "LIMIT", "0", "5");
    }

    @Test
    void deleteByKnowledgeBaseDeletesEveryKeyAcrossBatches() {
        KeyStore store = new KeyStore(3);
        server.respondWith(store);
        RedisStackVectorIndex index = index();

        index.deleteByKnowledgeBase(6L);

        List<byte[]> del = server.commands().stream()
                .filter(command -> commandName(command).equals("DEL"))
                .findFirst()
                .orElseThrow();
        // 回归点：RETURN 0 的回复是 [总数, key1, key2, key3]，三个 key 都要删掉。
        assertThat(texts(del.subList(1, del.size())))
                .containsExactly("knowflow:chunk:1000", "knowflow:chunk:1001", "knowflow:chunk:1002");
        assertThat(store.size()).isZero();
        assertThat(index.countByKnowledgeBase(6L)).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {999, 1000, 1001, 2500})
    void deleteByKnowledgeBaseRemovesArbitraryNumberOfVectors(int total) {
        KeyStore store = new KeyStore(total);
        server.respondWith(store);
        RedisStackVectorIndex index = index();

        index.deleteByKnowledgeBase(6L);

        assertThat(store.size()).isZero();
        // 删除完成后再查一次必须是空的，避免「删了一批但还有残留」。
        assertThat(index.countByKnowledgeBase(6L)).isZero();

        // 每轮都必须从 0 开始取，不能靠递增 OFFSET —— 否则删一批、数据前移，会静默漏删。
        List<String> limitArgs = texts(server.commands().stream()
                .filter(command -> commandName(command).equals("FT.SEARCH"))
                .findFirst()
                .orElseThrow());
        assertThat(limitArgs).containsSubsequence("LIMIT", "0", "500");
    }

    @Test
    void deleteReportsFailureWithDeletedCountAndLeavesRemainingVectors() {
        KeyStore store = new KeyStore(1001).failOnSearchCall(2);
        server.respondWith(store);

        assertThatThrownBy(() -> index().deleteByKnowledgeBase(6L))
                .isInstanceOf(com.knowflow.rag.redis.RedisCommandException.class)
                .hasMessageContaining("已删除 500 条");

        // 第一批 500 条已删，第二批失败后不再继续，剩下的 501 条保持原样。
        assertThat(store.size()).isEqualTo(501);
    }

    @Test
    void deleteByDocumentFiltersNumericField() {
        KeyStore store = new KeyStore(1);
        server.respondWith(store);

        index().deleteByDocument(4L);

        List<byte[]> search = server.commands().get(1);
        assertThat(texts(search)).contains("@document_id:[4 4]");
        assertThat(store.size()).isZero();
    }

    @Test
    void availableReturnsFalseWhenRedisHasNoSearchModule() {
        server.respondWith(command -> switch (commandName(command)) {
            case "PING" -> "+PONG\r\n";
            case "MODULE" -> arr();
            case "FT._LIST" -> "-ERR unknown command 'FT._LIST'\r\n";
            default -> "+OK\r\n";
        });

        assertThat(index().available()).isFalse();
    }

    @Test
    void availableReturnsTrueWhenSearchModulePresent() {
        server.respondWith(command -> switch (commandName(command)) {
            case "PING" -> "+PONG\r\n";
            case "MODULE" -> arr(arr(bulk("name"), bulk("search"), bulk("ver"), bulk("20802")));
            default -> "+OK\r\n";
        });

        assertThat(index().available()).isTrue();
    }

    @Test
    void rejectsQueryVectorWithWrongDimensions() {
        float[] wrong = new float[512];
        assertThatThrownBy(() -> index().search(6L, "bge-m3", wrong, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1024");
    }

    private RedisStackVectorIndex index() {
        RagProperties.Redis config = new RagProperties.Redis();
        config.setHost(InetAddress.getLoopbackAddress().getHostAddress());
        config.setPort(server.port());
        config.setTimeoutMs(2000);
        config.setIndexName("knowflow:chunk:idx");
        config.setKeyPrefix("knowflow:chunk:");
        return new RedisStackVectorIndex(config, 1024);
    }

    private static String commandName(List<byte[]> command) {
        return command.isEmpty() ? "" : text(command.get(0)).toUpperCase();
    }

    private static List<String> texts(List<byte[]> values) {
        List<String> result = new ArrayList<>(values.size());
        for (byte[] value : values) {
            result.add(text(value));
        }
        return result;
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    private static String bulk(String value) {
        return "$" + value.length() + "\r\n" + value + "\r\n";
    }

    private static String arr(String... items) {
        return "*" + items.length + "\r\n" + String.join("", items);
    }

    /**
     * 有状态的假 RediSearch：维护一组 key，FT.SEARCH 按 LIMIT 返回前 N 条，DEL 真删。
     * 这样才能验证「删一批 → 再查」的循环确实把数据删干净，而不是靠一次性大 LIMIT 蒙对。
     */
    static final class KeyStore implements Function<List<byte[]>, String> {

        private final Set<String> keys = new LinkedHashSet<>();
        private int searchCalls;
        private int failOnSearchCall = -1;

        KeyStore(int count) {
            for (int i = 0; i < count; i++) {
                keys.add("knowflow:chunk:" + (1000 + i));
            }
        }

        KeyStore failOnSearchCall(int call) {
            this.failOnSearchCall = call;
            return this;
        }

        int size() {
            return keys.size();
        }

        @Override
        public String apply(List<byte[]> command) {
            String name = commandName(command);
            if ("FT.INFO".equals(name)) {
                return "+OK\r\n";
            }
            if ("FT.SEARCH".equals(name)) {
                searchCalls++;
                if (failOnSearchCall > 0 && searchCalls == failOnSearchCall) {
                    return "-ERR backend timeout\r\n";
                }
                int limit = parseLimit(command);
                List<String> items = new ArrayList<>();
                items.add(bulk(String.valueOf(keys.size())));
                int taken = 0;
                for (String key : keys) {
                    if (taken++ >= limit) {
                        break;
                    }
                    items.add(bulk(key));
                }
                return arr(items.toArray(new String[0]));
            }
            if ("DEL".equals(name)) {
                int removed = 0;
                for (int i = 1; i < command.size(); i++) {
                    if (keys.remove(text(command.get(i)))) {
                        removed++;
                    }
                }
                return ":" + removed + "\r\n";
            }
            return "+OK\r\n";
        }

        private static int parseLimit(List<byte[]> command) {
            List<String> args = texts(command);
            for (int i = 0; i + 2 < args.size(); i++) {
                if ("LIMIT".equalsIgnoreCase(args.get(i))) {
                    try {
                        return Integer.parseInt(args.get(i + 2));
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                }
            }
            return 10;
        }
    }

    /**
     * 只支持 RESP2 数组命令的最小假服务端：记录收到的命令，按 handler 返回预设应答。
     */
    static final class FakeRedisServer implements Closeable {

        private final ServerSocket serverSocket;
        private final List<List<byte[]>> commands = Collections.synchronizedList(new ArrayList<>());
        private final Thread acceptor;
        private volatile Function<List<byte[]>, String> handler = command -> "+OK\r\n";
        private volatile boolean running = true;

        FakeRedisServer() throws IOException {
            this.serverSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
            this.acceptor = new Thread(this::acceptLoop, "fake-redis-acceptor");
            this.acceptor.setDaemon(true);
            this.acceptor.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void respondWith(Function<List<byte[]>, String> handler) {
            this.handler = handler;
        }

        List<List<byte[]>> commands() {
            synchronized (commands) {
                return new ArrayList<>(commands);
            }
        }

        private void acceptLoop() {
            while (running) {
                try (Socket socket = serverSocket.accept()) {
                    handle(socket);
                } catch (IOException ex) {
                    return;
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            while (running) {
                List<byte[]> command = readCommand(in);
                if (command == null) {
                    return;
                }
                commands.add(command);
                out.write(handler.apply(command).getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }

        private static List<byte[]> readCommand(InputStream in) throws IOException {
            String header = readLine(in);
            if (header == null || !header.startsWith("*")) {
                return null;
            }
            int count = Integer.parseInt(header.substring(1));
            List<byte[]> parts = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String lengthLine = readLine(in);
                if (lengthLine == null || !lengthLine.startsWith("$")) {
                    return null;
                }
                int length = Integer.parseInt(lengthLine.substring(1));
                byte[] data = new byte[length];
                int offset = 0;
                while (offset < length) {
                    int read = in.read(data, offset, length - offset);
                    if (read < 0) {
                        return null;
                    }
                    offset += read;
                }
                readLine(in);
                parts.add(data);
            }
            return parts;
        }

        private static String readLine(InputStream in) throws IOException {
            StringBuilder line = new StringBuilder();
            int b;
            while ((b = in.read()) >= 0) {
                if (b == '\r') {
                    int next = in.read();
                    if (next == '\n') {
                        return line.toString();
                    }
                    line.append('\r').append((char) next);
                    continue;
                }
                line.append((char) b);
            }
            return line.isEmpty() ? null : line.toString();
        }

        @Override
        public void close() {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // 关闭失败不影响测试
            }
        }
    }
}
