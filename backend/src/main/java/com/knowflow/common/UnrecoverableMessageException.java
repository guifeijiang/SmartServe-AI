package com.knowflow.common;

public class UnrecoverableMessageException extends RuntimeException {

    public UnrecoverableMessageException(String message) {
        super(message);
    }

    public UnrecoverableMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
