package com.example.concurrentbalanceservice.service;

import java.math.BigDecimal;

public interface BalanceService {

    void credit(Long accountId, BigDecimal amount, Long TransactionId);

    void debit(Long accountId, BigDecimal amount, Long TransactionId);

    void transfer(Long sourceAccountId, Long destinationAccountId, BigDecimal amount, Long TransactionId);

}
