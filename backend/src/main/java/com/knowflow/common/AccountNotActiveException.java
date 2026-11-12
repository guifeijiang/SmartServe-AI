package com.knowflow.common;

public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException() {
        super("账号不可用");
    }
}
