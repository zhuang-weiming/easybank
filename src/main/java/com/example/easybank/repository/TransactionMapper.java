package com.example.easybank.repository;

import com.example.easybank.domain.Transaction;
import com.example.easybank.domain.TransactionExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TransactionMapper {
    long countByExample(TransactionExample example);

    int deleteByExample(TransactionExample example);

    int deleteByPrimaryKey(Long id);

    int insert(Transaction row);

    int insertSelective(Transaction row);

    List<Transaction> selectByExample(TransactionExample example);

    Transaction selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") Transaction row, @Param("example") TransactionExample example);

    int updateByExample(@Param("row") Transaction row, @Param("example") TransactionExample example);

    int updateByPrimaryKeySelective(Transaction row);

    int updateByPrimaryKey(Transaction row);
}