package com.example.easybank.repository;

import com.example.easybank.domain.Account;
import com.example.easybank.domain.AccountExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AccountMapper {
    long countByExample(AccountExample example);

    int deleteByExample(AccountExample example);

    int deleteByPrimaryKey(Long id);

    int insert(Account row);

    int insertSelective(Account row);

    List<Account> selectByExample(AccountExample example);

    Account selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") Account row, @Param("example") AccountExample example);

    int updateByExample(@Param("row") Account row, @Param("example") AccountExample example);

    int updateByPrimaryKeySelective(Account row);

    int updateByPrimaryKey(Account row);
}