package com.example.concurrentbalanceservice.controller;

import com.example.concurrentbalanceservice.dto.TransactionRequestDto;
import com.example.concurrentbalanceservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("/api/transaction")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("")
    public ResponseEntity<?> createTransaction(@RequestBody TransactionRequestDto transactionRequestDto) {
           transactionService.createTransaction(transactionRequestDto);
           return ResponseEntity.ok().build();
    }

}
