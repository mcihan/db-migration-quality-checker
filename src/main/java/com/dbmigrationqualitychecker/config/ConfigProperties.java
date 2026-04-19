package com.dbmigrationqualitychecker.config;

import com.dbmigrationqualitychecker.dialect.DatabaseType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ConfigProperties {

    @Value("${datasource.source.type}")
    private DatabaseType sourceType;

    @Value("${datasource.source.jdbc-url}")
    private String sourceJdbcUrl;

    @Value("${datasource.source.username}")
    private String sourceUsername;

    @Value("${datasource.target.type}")
    private DatabaseType targetType;

    @Value("${datasource.target.jdbc-url}")
    private String targetJdbcUrl;

    @Value("${datasource.target.username}")
    private String targetUsername;

    @PostConstruct
    void init() {
        log.info("**************************************");
        log.info("source type     = {}", sourceType);
        log.info("source jdbc-url = {}", sourceJdbcUrl);
        log.info("source username = {}", sourceUsername);
        log.info("target type     = {}", targetType);
        log.info("target jdbc-url = {}", targetJdbcUrl);
        log.info("target username = {}", targetUsername);
        log.info("**************************************");
    }
}
