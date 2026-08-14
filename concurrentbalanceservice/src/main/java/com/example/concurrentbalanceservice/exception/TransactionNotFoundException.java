package com.example.concurrentbalanceservice.exception;

public class TransactionNotFoundException extends BusinessException {
    public TransactionNotFoundException() {
        super("Transaction Not Found");
    }
}
