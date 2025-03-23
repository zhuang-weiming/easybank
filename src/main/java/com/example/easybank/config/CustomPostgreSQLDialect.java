package com.example.easybank.config;

import org.hibernate.dialect.PostgreSQL95Dialect;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;
import org.hibernate.type.descriptor.sql.TimestampTypeDescriptor;
import java.sql.Types;

public class CustomPostgreSQLDialect extends PostgreSQL95Dialect {

    public CustomPostgreSQLDialect() {
        super();
        registerColumnType(Types.TIMESTAMP, "timestamp");
        registerColumnType(Types.TIMESTAMP_WITH_TIMEZONE, "timestamp");
    }

    @Override
    public SqlTypeDescriptor remapSqlTypeDescriptor(SqlTypeDescriptor sqlTypeDescriptor) {
        if (sqlTypeDescriptor.getSqlType() == Types.TIMESTAMP || 
            sqlTypeDescriptor.getSqlType() == Types.TIMESTAMP_WITH_TIMEZONE) {
            return TimestampTypeDescriptor.INSTANCE;
        }
        return super.remapSqlTypeDescriptor(sqlTypeDescriptor);
    }
} 