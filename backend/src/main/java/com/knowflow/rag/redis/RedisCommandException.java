package com.knowflow.rag.redis;

/**
 * Redis 命令执行失败。保留原始错误信息，便于区分「索引已存在」「未知命令」等可恢复错误。
 */
public class RedisCommandException extends RuntimeException {

    public RedisCommandException(String message) {
        super(message);
    }

    public RedisCommandException(String message, Throwable cause) {
        super(message, cause);
    }

    public boolean isUnknownCommand() {
        String message = getMessage();
        return message != null && message.toLowerCase().contains("unknown command");
    }

    public boolean isIndexAlreadyExists() {
        String message = getMessage();
        return message != null && message.toLowerCase().contains("already exists");
    }

    public boolean isUnknownIndex() {
        String message = getMessage();
        return message != null && message.toLowerCase().contains("unknown index");
    }
}
