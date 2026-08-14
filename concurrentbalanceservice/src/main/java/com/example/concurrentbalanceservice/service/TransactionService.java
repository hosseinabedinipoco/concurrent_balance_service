package com.example.concurrentbalanceservice.service;

import com.example.concurrentbalanceservice.dto.TransactionRequestDto;
import com.example.concurrentbalanceservice.exception.DuplicateTransactionException;
import com.example.concurrentbalanceservice.exception.TransactionBadRequestException;
import com.example.concurrentbalanceservice.model.Transaction;
import com.example.concurrentbalanceservice.model.TransactionStatus;
import com.example.concurrentbalanceservice.model.TransactionType;
import com.example.concurrentbalanceservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final BalanceServiceImp balanceServiceImp;

    public void createTransaction(TransactionRequestDto transactionRequestDto) {
        Transaction transaction = Transaction.builder()
                .transactionUid(transactionRequestDto.getTransactionId())
                .type(transactionRequestDto.getTransactionType())
                .amount(transactionRequestDto.getAmount())
                .status(TransactionStatus.WAITING)
                .sourceAccountId(transactionRequestDto.getSourceAccountId())
                .destinationAccountId(transactionRequestDto.getDestinationAccountId())
                .build();

        validate(transaction);
        try {
            transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateTransactionException();
        }
        processTransaction(transaction);
    }

    private void processTransaction(Transaction transaction) {
        TransactionType transactionType = transaction.getType();
        switch (transactionType) {
            case CREDIT -> balanceServiceImp.credit(
                    transaction.getDestinationAccountId(), transaction.getAmount(), transaction.getId()
            );
            case DEBIT -> balanceServiceImp.debit(
                    transaction.getSourceAccountId(), transaction.getAmount(), transaction.getId()
            );
            case TRANSFER -> balanceServiceImp.transfer(
                    transaction.getSourceAccountId(), transaction.getDestinationAccountId(), transaction.getAmount(),
                    transaction.getId()
            );
        }
    }

    private void validate(Transaction transaction) {
        boolean hasFault = false;

        if(transaction.getTransactionUid() == null){
            hasFault = true;
        }

        if(transaction.getType() == null) {
            hasFault = true;
        } else {
            if(transaction.getType() == TransactionType.CREDIT) {
                if(transaction.getDestinationAccountId() == null) {
                    hasFault=true;
                }
            } else if (transaction.getType() == TransactionType.DEBIT) {
                if(transaction.getSourceAccountId() == null) {
                    hasFault = true;
                }
            } else if (transaction.getType() == TransactionType.TRANSFER) {
                if(transaction.getSourceAccountId() == null ||
                        transaction.getDestinationAccountId() == null ||
                        transaction.getDestinationAccountId().equals(transaction.getSourceAccountId())) {
                    hasFault = true;
                }
            } else {
                hasFault = true;
            }
        }

        if(transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            hasFault = true;
        }

        if(hasFault) {
            throw new TransactionBadRequestException();
        }

        Optional<Transaction> optionalTransaction = transactionRepository.
                findByTransactionUid(transaction.getTransactionUid());
        if(optionalTransaction.isPresent()) {
            throw new DuplicateTransactionException();
        }

    }
}
