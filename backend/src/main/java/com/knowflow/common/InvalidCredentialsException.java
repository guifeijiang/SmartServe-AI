package com.knowflow.common;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("用户名或密码错误");
    }
}
