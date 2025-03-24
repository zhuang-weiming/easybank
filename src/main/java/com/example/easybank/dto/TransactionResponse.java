package com.example.easybank.dto;

import com.example.easybank.domain.Transaction;
import com.example.easybank.domain.TransactionStatus;
import com.example.easybank.domain.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class TransactionResponse {
    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String sourceAccountNumber;
    private String sourceAccountHolder;
    private String destinationAccountNumber;
    private String destinationAccountHolder;
    private TransactionStatus status;
    private TransactionType transactionType;
    private OffsetDateTime timestamp;

    public static TransactionResponse fromTransaction(Transaction transaction) {
        return TransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .description(transaction.getDescription())
                .sourceAccountNumber(transaction.getSourceAccount() != null ? transaction.getSourceAccount().getAccountNumber() : null)
                .sourceAccountHolder(transaction.getSourceAccount() != null ? transaction.getSourceAccount().getAccountHolder() : null)
                .destinationAccountNumber(transaction.getDestinationAccount() != null ? transaction.getDestinationAccount().getAccountNumber() : null)
                .destinationAccountHolder(transaction.getDestinationAccount() != null ? transaction.getDestinationAccount().getAccountHolder() : null)
                .status(transaction.getStatus())
                .transactionType(transaction.getTransactionType())
                .timestamp(transaction.getUpdatedAt())
                .build();
    }
} 