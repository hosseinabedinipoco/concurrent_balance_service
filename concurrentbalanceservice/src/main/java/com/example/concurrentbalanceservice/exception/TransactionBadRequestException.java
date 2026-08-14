package com.example.concurrentbalanceservice.exception;

public class TransactionBadRequestException extends BusinessException {
    public TransactionBadRequestException() {
        super("Invalid Transaction");
    }
}
