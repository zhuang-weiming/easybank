package com.example.easybank.repository;

import com.example.easybank.domain.Transaction;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TransactionRepository {
    @Select("SELECT t.id, t.amount, t.currency, t.transaction_type, " +
           "t.status, t.description, t.created_at, t.updated_at, " +
           "t.source_account_id, t.destination_account_id, " +
           "sa.account_number as source_account_number, " +
           "sa.account_holder as source_account_holder, " +
           "da.account_number as destination_account_number, " +
           "da.account_holder as destination_account_holder " +
           "FROM transactions t " +
           "JOIN accounts sa ON t.source_account_id = sa.id " +
           "JOIN accounts da ON t.destination_account_id = da.id " +
           "WHERE sa.account_number = #{accountNumber} OR da.account_number = #{accountNumber} " +
           "AND t.status = 'COMPLETED' " +
           "ORDER BY t.created_at DESC")
    List<Transaction> findLatestTransactionsByAccountNumber(
        @Param("accountNumber") String accountNumber);
    
    @Insert("INSERT INTO transactions(source_account_id, destination_account_id, amount, currency, transaction_type, status, description) " +
           "VALUES(#{sourceAccount.id}, #{destinationAccount.id}, #{amount}, #{currency}, #{transactionType}, #{status}, #{description})")
    int save(Transaction transaction);
}
