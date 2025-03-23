package com.example.easybank.repository;

import com.example.easybank.domain.Account;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AccountRepository {
    @Select("SELECT * FROM accounts WHERE account_number = #{accountNumber}")
    Optional<Account> findById(String accountNumber);
    
    @Select("SELECT * FROM accounts WHERE account_number = #{accountNumber}")
    Optional<Account> findByAccountNumber(String accountNumber);
    
    @Select("SELECT * FROM accounts WHERE account_number = #{accountNumber} FOR UPDATE")
    Optional<Account> findByAccountNumberWithLock(String accountNumber);
    
    @Insert("INSERT INTO accounts (account_number, account_holder, balance, version, account_type, currency, status) " +
            "VALUES (#{accountNumber}, #{accountHolder}, #{balance}, #{version}, #{accountType}, #{currency}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Account account);
    
    @Update("UPDATE accounts SET " +
            "balance = #{balance}, " + 
            "version = #{version} + 1, " +
            "account_holder = CASE WHEN #{accountHolder} IS NULL THEN account_holder ELSE #{accountHolder} END, " +
            "account_type = CASE WHEN #{accountType} IS NULL THEN account_type ELSE #{accountType} END, " +
            "currency = CASE WHEN #{currency} IS NULL THEN currency ELSE #{currency} END, " +
            "status = CASE WHEN #{status} IS NULL THEN status ELSE #{status} END, " +
            "updated_at = now() " +
            "WHERE id = #{id}")
    int update(Account account);
    
    default int save(Account account) {
        if (account.getId() != null) {
            return update(account);
        } else {
            return insert(account);
        }
    }
}