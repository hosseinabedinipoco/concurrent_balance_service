package com.example.concurrentbalanceservice.service;

import com.example.concurrentbalanceservice.exception.AccountNotFoundException;
import com.example.concurrentbalanceservice.exception.InsufficientBalanceException;
import com.example.concurrentbalanceservice.exception.InvalidTransactionStateException;
import com.example.concurrentbalanceservice.exception.TransactionNotFoundException;
import com.example.concurrentbalanceservice.model.Account;
import com.example.concurrentbalanceservice.model.Transaction;
import com.example.concurrentbalanceservice.model.TransactionStatus;
import com.example.concurrentbalanceservice.repository.AccountRepository;
import com.example.concurrentbalanceservice.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BalanceServiceImp implements BalanceService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionStatusService transactionStatusService;

    @Override
    @Transactional
    public void credit(Long accountId, BigDecimal amount, Long transactionId) {
        try {
            Transaction transaction = getAndValidateTransaction(transactionId);

            Account account = accountRepository.findByIdForUpdate(accountId)
                    .orElseThrow(AccountNotFoundException::new);

            account.setBalance(account.getBalance().add(amount));

            transaction.setStatus(TransactionStatus.SUCCESS);

        } catch (RuntimeException ex) {
            transactionStatusService.markAsFailed(transactionId);
            throw ex;
        }
    }

    @Override
    @Transactional
    public void debit(Long accountId, BigDecimal amount, Long transactionId) {
        try {
            Transaction transaction = getAndValidateTransaction(transactionId);

            Account account = accountRepository.findByIdForUpdate(accountId)
                    .orElseThrow(AccountNotFoundException::new);

            if (account.getBalance().compareTo(amount) < 0) {
                throw new InsufficientBalanceException();
            }

            account.setBalance(
                    account.getBalance().subtract(amount)
            );

            transaction.setStatus(TransactionStatus.SUCCESS);

        } catch (RuntimeException ex) {
            transactionStatusService.markAsFailed(transactionId);
            throw ex;
        }
    }

    @Override
    @Transactional
    public void transfer(
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            Long transactionId
    ) {
        try {
            Transaction transaction = getAndValidateTransaction(transactionId);

            Long firstId = Math.min(sourceAccountId, destinationAccountId);
            Long secondId = Math.max(sourceAccountId, destinationAccountId);

            Account firstAccount = accountRepository.findByIdForUpdate(firstId)
                    .orElseThrow(AccountNotFoundException::new);

            Account secondAccount = accountRepository.findByIdForUpdate(secondId)
                    .orElseThrow(AccountNotFoundException::new);

            Account sourceAccount =
                    sourceAccountId.equals(firstAccount.getId())
                            ? firstAccount
                            : secondAccount;

            Account destinationAccount =
                    destinationAccountId.equals(firstAccount.getId())
                            ? firstAccount
                            : secondAccount;

            if (sourceAccount.getBalance().compareTo(amount) < 0) {
                throw new InsufficientBalanceException();
            }

            sourceAccount.setBalance(
                    sourceAccount.getBalance().subtract(amount)
            );

            destinationAccount.setBalance(
                    destinationAccount.getBalance().add(amount)
            );

            transaction.setStatus(TransactionStatus.SUCCESS);

        } catch (RuntimeException ex) {
            transactionStatusService.markAsFailed(transactionId);
            throw ex;
        }
    }

    private Transaction getAndValidateTransaction(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(TransactionNotFoundException::new);

        if (transaction.getStatus() != TransactionStatus.WAITING) {
            throw new InvalidTransactionStateException();
        }

        return transaction;
    }
}
