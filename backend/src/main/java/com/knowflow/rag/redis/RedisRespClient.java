package com.knowflow.rag.redis;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 极简 RESP2 客户端：只为调用 Redis Stack 的 FT.CREATE / FT.SEARCH 等模块命令。
 * 不引入额外客户端依赖，命令与应答都在这里显式编解码，便于单元测试用假服务端校验协议。
 *
 * 应答类型映射：简单字符串 -> String，整数 -> Long，批量字符串 -> byte[]，数组 -> List&lt;Object&gt;，空值 -> null。
 */
public final class RedisRespClient implements Closeable {

    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;

    private RedisRespClient(Socket socket) throws IOException {
        this.socket = socket;
        this.input = new BufferedInputStream(socket.getInputStream());
        this.output = new BufferedOutputStream(socket.getOutputStream());
    }

    public static RedisRespClient connect(
            String host,
            int port,
            String password,
            int database,
            int timeoutMs
    ) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            socket.setTcpNoDelay(true);
            RedisRespClient client = new RedisRespClient(socket);
            if (password != null && !password.isBlank()) {
                client.command("AUTH", password);
            }
            if (database > 0) {
                client.command("SELECT", String.valueOf(database));
            }
            return client;
        } catch (IOException ex) {
            closeQuietly(socket);
            throw new RedisCommandException("无法连接 Redis " + host + ":" + port + "：" + ex.getMessage(), ex);
        }
    }

    public Object command(String... parts) {
        byte[][] encoded = new byte[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            encoded[i] = (parts[i] == null ? "" : parts[i]).getBytes(StandardCharsets.UTF_8);
        }
        return command(encoded);
    }

    /**
     * 二进制安全的命令入口：向量等字段必须原样传输，不能先转成字符串。
     */
    public Object commandRaw(byte[]... parts) {
        return command(parts);
    }

    public Object command(byte[]... parts) {
        try {
            writeCommand(parts);
            output.flush();
            return readReply();
        } catch (IOException ex) {
            throw new RedisCommandException("Redis 命令执行失败：" + ex.getMessage(), ex);
        } catch (NumberFormatException ex) {
            throw new RedisCommandException("Redis 应答格式异常：" + ex.getMessage(), ex);
        }
    }

    private void writeCommand(byte[][] parts) throws IOException {
        output.write(('*' + String.valueOf(parts.length) + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (byte[] part : parts) {
            output.write(('$' + String.valueOf(part.length) + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(part);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private Object readReply() throws IOException {
        int prefix = input.read();
        if (prefix < 0) {
            throw new IOException("Redis 连接已被关闭");
        }
        switch (prefix) {
            case '+':
                return readLine();
            case '-':
                throw new RedisCommandException(readLine());
            case ':':
                return Long.parseLong(readLine());
            case '$':
                return readBulkString();
            case '*':
                return readArray();
            default:
                throw new IOException("无法识别的 Redis 应答前缀: " + (char) prefix);
        }
    }

    private Object readBulkString() throws IOException {
        int length = Integer.parseInt(readLine());
        if (length < 0) {
            return null;
        }
        byte[] data = readFully(length);
        readLine();
        return data;
    }

    private List<Object> readArray() throws IOException {
        int count = Integer.parseInt(readLine());
        if (count < 0) {
            return null;
        }
        List<Object> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(readReply());
        }
        return items;
    }

    private byte[] readFully(int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(data, offset, length - offset);
            if (read < 0) {
                throw new IOException("Redis 应答提前结束");
            }
            offset += read;
        }
        return data;
    }

    private String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        while (true) {
            int b = input.read();
            if (b < 0) {
                throw new IOException("Redis 应答提前结束");
            }
            if (b == '\r') {
                int next = input.read();
                if (next == '\n') {
                    return line.toString();
                }
                line.append('\r');
                if (next < 0) {
                    throw new IOException("Redis 应答提前结束");
                }
                line.append((char) next);
                continue;
            }
            line.append((char) b);
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 关闭失败不影响主流程
        }
    }

    @Override
    public void close() {
        closeQuietly(socket);
    }
}
