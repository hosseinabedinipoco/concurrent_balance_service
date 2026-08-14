package com.example.concurrentbalanceservice.dto;

import com.example.concurrentbalanceservice.model.Transaction;
import com.example.concurrentbalanceservice.model.TransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransactionRequestDto {
    private UUID transactionId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private Long sourceAccountId;
    private Long destinationAccountId;
}

