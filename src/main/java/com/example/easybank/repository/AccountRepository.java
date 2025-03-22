package com.example.easybank.repository;

import com.example.easybank.domain.Account;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AccountRepository {
    Optional<Account> findById(String accountNumber);
    Optional<Account> findByAccountNumber(String accountNumber);
    Optional<Account> findByAccountNumberWithLock(String accountNumber);
    
    @Insert("INSERT INTO accounts (account_number, account_holder, balance, version, account_type, currency, status) " +
            "VALUES (#{accountNumber}, #{accountHolder}, #{balance}, #{version}, #{accountType}, #{currency}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int save(Account account);
}