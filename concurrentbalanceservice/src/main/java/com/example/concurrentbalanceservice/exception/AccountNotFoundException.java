package com.example.concurrentbalanceservice.exception;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException() {
        super("Account Not Found");
    }
}
