package com.example.concurrentbalanceservice.exception;

public class InsufficientBalanceException extends BusinessException {
    public InsufficientBalanceException() {
        super("Insufficient balance");
    }
}
