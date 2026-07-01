package com.hanwha.ai.global.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;

@Configuration
public class TransactionConfig implements TransactionManagementConfigurer {
    private final PlatformTransactionManager jdbcTransactionManager;

    public TransactionConfig(DataSource dataSource) {
        this.jdbcTransactionManager = new JdbcTransactionManager(dataSource);
    }

    @Bean
    @Primary
    public PlatformTransactionManager jdbcTransactionManager() {
        return jdbcTransactionManager;
    }

    @Override
    public PlatformTransactionManager annotationDrivenTransactionManager() {
        return jdbcTransactionManager;
    }
}