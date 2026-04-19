package com.dbmigrationqualitychecker.config;

import com.dbmigrationqualitychecker.dialect.DatabaseDialect;
import com.dbmigrationqualitychecker.dialect.DatabaseType;
import com.dbmigrationqualitychecker.dialect.Db2Dialect;
import com.dbmigrationqualitychecker.dialect.MySqlDialect;
import com.dbmigrationqualitychecker.dialect.PostgresDialect;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Wires two sides (source + target) of the migration, each with its own
 * {@link DataSource}, {@link NamedParameterJdbcTemplate}, and
 * {@link DatabaseRepository} configured for the chosen {@link DatabaseType}.
 */
@Configuration
public class DataSourceConfig {

    static DatabaseDialect dialectFor(DatabaseType type) {
        return switch (type) {
            case DB2 -> new Db2Dialect();
            case MYSQL -> new MySqlDialect();
            case POSTGRES -> new PostgresDialect();
        };
    }

    private static DataSource buildDataSource(DatabaseType type, String url, String username, String password) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(dialectFor(type).driverClassName())
                .build();
    }

    @Bean("sourceDataSource")
    public DataSource sourceDataSource(
            @Value("${datasource.source.type}") DatabaseType type,
            @Value("${datasource.source.jdbc-url}") String url,
            @Value("${datasource.source.username}") String username,
            @Value("${datasource.source.password}") String password) {
        return buildDataSource(type, url, username, password);
    }

    @Bean("targetDataSource")
    public DataSource targetDataSource(
            @Value("${datasource.target.type}") DatabaseType type,
            @Value("${datasource.target.jdbc-url}") String url,
            @Value("${datasource.target.username}") String username,
            @Value("${datasource.target.password}") String password) {
        return buildDataSource(type, url, username, password);
    }

    @Bean("sourceJdbcTemplate")
    public NamedParameterJdbcTemplate sourceJdbcTemplate(@Qualifier("sourceDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean("targetJdbcTemplate")
    public NamedParameterJdbcTemplate targetJdbcTemplate(@Qualifier("targetDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean("sourceRepository")
    public DatabaseRepository sourceRepository(
            @Qualifier("sourceJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            @Value("${datasource.source.type}") DatabaseType type,
            @Value("${config.random-data-count}") int randomDataCount) {
        return new DatabaseRepository(jdbc, dialectFor(type), DatabaseRepository.Side.SOURCE, randomDataCount);
    }

    @Bean("targetRepository")
    public DatabaseRepository targetRepository(
            @Qualifier("targetJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            @Value("${datasource.target.type}") DatabaseType type,
            @Value("${config.random-data-count}") int randomDataCount) {
        return new DatabaseRepository(jdbc, dialectFor(type), DatabaseRepository.Side.TARGET, randomDataCount);
    }
}
