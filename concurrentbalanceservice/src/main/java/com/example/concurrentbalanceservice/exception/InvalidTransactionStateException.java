package com.example.concurrentbalanceservice.exception;

public class InvalidTransactionStateException extends BusinessException {
    public InvalidTransactionStateException() {
        super("Invalid Transaction State");
    }
}
