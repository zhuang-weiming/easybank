package com.example.easybank.repository;

import com.example.easybank.domain.Transaction;
import com.example.easybank.domain.TransactionStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TransactionRepository {
    List<Transaction> findByStatus(@Param("status") TransactionStatus status);
    List<Transaction> findRetryableTransactions(@Param("status") TransactionStatus status, @Param("maxRetries") int maxRetries);
    List<Transaction> findBySourceAccountAccountNumberOrDestinationAccountAccountNumber(@Param("accountNumber") String sourceAccountNumber, @Param("accountNumber") String destinationAccountNumber);
    Transaction save(@Param("transaction") Transaction transaction);
}
