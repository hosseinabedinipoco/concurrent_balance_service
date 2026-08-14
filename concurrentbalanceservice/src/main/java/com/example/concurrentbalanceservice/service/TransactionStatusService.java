package com.example.concurrentbalanceservice.service;

import com.example.concurrentbalanceservice.exception.TransactionNotFoundException;
import com.example.concurrentbalanceservice.model.Transaction;
import com.example.concurrentbalanceservice.model.TransactionStatus;
import com.example.concurrentbalanceservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionStatusService {

    private final TransactionRepository transactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(TransactionNotFoundException::new);

        transaction.setStatus(TransactionStatus.FAILED);
    }
}