package com.example.easybank.repository;

import com.example.easybank.domain.Transaction;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TransactionRepository {
    @Select("SELECT t.*, " +
           "sa.id as source_id, sa.account_number as source_account_number, sa.account_holder as source_account_holder, " +
           "sa.balance as source_balance, sa.currency as source_currency, sa.account_type as source_account_type, sa.status as source_status, " +
           "da.id as destination_id, da.account_number as destination_account_number, da.account_holder as destination_account_holder, " +
           "da.balance as destination_balance, da.currency as destination_currency, da.account_type as destination_account_type, da.status as destination_status " +
           "FROM transactions t " +
           "LEFT JOIN accounts sa ON t.source_account_id = sa.id " +
           "LEFT JOIN accounts da ON t.destination_account_id = da.id " +
           "WHERE sa.account_number = #{sourceAccountNumber} OR da.account_number = #{destinationAccountNumber}")
    List<Transaction> findBySourceAccountAccountNumberOrDestinationAccountAccountNumber(
        @Param("sourceAccountNumber") String sourceAccountNumber, 
        @Param("destinationAccountNumber") String destinationAccountNumber);
    
    @Insert("INSERT INTO transactions(source_account_id, destination_account_id, amount, currency, transaction_type, status, description) " +
           "VALUES(#{sourceAccount.id}, #{destinationAccount.id}, #{amount}, #{currency}, #{transactionType}, #{status}, #{description})")
    int save(Transaction transaction);
}
