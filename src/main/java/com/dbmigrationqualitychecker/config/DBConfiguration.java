package com.dbmigrationqualitychecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DBConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "datasource.db2")
    public DataSource db2DataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @ConfigurationProperties(prefix = "datasource.mysql")
    public DataSource mySqlDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public NamedParameterJdbcTemplate db2JdbcTemplate(DataSource db2DataSource) {
        return new NamedParameterJdbcTemplate(db2DataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate mySqlJdbcTemplate(DataSource mySqlDataSource) {
        return new NamedParameterJdbcTemplate(mySqlDataSource);
    }


}
