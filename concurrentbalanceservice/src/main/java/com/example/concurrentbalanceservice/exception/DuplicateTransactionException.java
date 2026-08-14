package com.example.concurrentbalanceservice.exception;

public class DuplicateTransactionException extends BusinessException {
    public DuplicateTransactionException() {
        super("Duplicate Transaction.");
    }
}
