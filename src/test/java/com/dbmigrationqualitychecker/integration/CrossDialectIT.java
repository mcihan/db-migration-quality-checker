package com.dbmigrationqualitychecker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dbmigrationqualitychecker.check.CheckOutcome;
import com.dbmigrationqualitychecker.check.CheckRunner;
import com.dbmigrationqualitychecker.check.CheckSelection;
import com.dbmigrationqualitychecker.check.ColumnMetadataCheck;
import com.dbmigrationqualitychecker.check.ColumnNamesCheck;
import com.dbmigrationqualitychecker.check.IndexCheck;
import com.dbmigrationqualitychecker.check.MigrationCheck;
import com.dbmigrationqualitychecker.check.RandomDataCheck;
import com.dbmigrationqualitychecker.check.RowCountCheck;
import com.dbmigrationqualitychecker.check.TableProvider;
import com.dbmigrationqualitychecker.dialect.MySqlDialect;
import com.dbmigrationqualitychecker.dialect.PostgresDialect;
import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.report.ReportWriter;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the dialect-agnostic design: MySQL source + Postgres target driven by
 * the same {@link CheckRunner}. Fast enough to run on every {@code ./mvnw verify}.
 */
@Tag("integration")
@Testcontainers
class CrossDialectIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("sourcedb")
            .withUsername("tester")
            .withPassword("testerpw")
            .withReuse(true);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("targetdb")
            .withUsername("tester")
            .withPassword("testerpw")
            .withReuse(true);

    private static CheckRunner runner;
    private static CapturingReportWriter reportWriter;

    @BeforeAll
    static void setUp() {
        NamedParameterJdbcTemplate mysqlTpl = new NamedParameterJdbcTemplate(dataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword(), "com.mysql.cj.jdbc.Driver"));
        NamedParameterJdbcTemplate postgresTpl = new NamedParameterJdbcTemplate(dataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), "org.postgresql.Driver"));

        DatabaseRepository source =
                new DatabaseRepository(mysqlTpl, new MySqlDialect(), DatabaseRepository.Side.SOURCE, 100);
        DatabaseRepository target =
                new DatabaseRepository(postgresTpl, new PostgresDialect(), DatabaseRepository.Side.TARGET, 100);

        seedMysql(mysqlTpl.getJdbcTemplate());
        seedPostgres(postgresTpl.getJdbcTemplate());

        TableProvider tableProvider = mock(TableProvider.class);
        when(tableProvider.getTables())
                .thenReturn(List.of(
                        new Table("account_ok", MYSQL.getDatabaseName(), "public", "id", "", false),
                        new Table("account_count_diff", MYSQL.getDatabaseName(), "public", "id", "", false),
                        new Table("account_data_diff", MYSQL.getDatabaseName(), "public", "id", "", false)));

        List<MigrationCheck> checks = List.of(
                new ColumnNamesCheck(source, target),
                new ColumnMetadataCheck(source, target),
                new IndexCheck(source, target),
                new RowCountCheck(source, target),
                new RandomDataCheck(source, target));

        reportWriter = new CapturingReportWriter();
        runner = new CheckRunner(checks, tableProvider, new CheckSelection(false, false, false), reportWriter);
    }

    private static DataSource dataSource(String url, String user, String pass, String driver) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(user);
        ds.setPassword(pass);
        ds.setDriverClassName(driver);
        ds.setMaximumPoolSize(4);
        return ds;
    }

    private static void seedMysql(JdbcTemplate jdbc) {
        for (String t : List.of("account_ok", "account_count_diff", "account_data_diff")) {
            jdbc.execute("DROP TABLE IF EXISTS " + t);
        }
        jdbc.execute("CREATE TABLE account_ok (id INT NOT NULL PRIMARY KEY, name VARCHAR(50))");
        jdbc.execute("INSERT INTO account_ok VALUES (1, 'Alice'), (2, 'Bob')");

        jdbc.execute("CREATE TABLE account_count_diff (id INT NOT NULL PRIMARY KEY, name VARCHAR(50))");
        jdbc.execute("INSERT INTO account_count_diff VALUES (1, 'A'), (2, 'B'), (3, 'C')");

        jdbc.execute("CREATE TABLE account_data_diff (id INT NOT NULL PRIMARY KEY, name VARCHAR(50))");
        jdbc.execute("INSERT INTO account_data_diff VALUES (1, 'same'), (2, 'mysqlValue')");
    }

    private static void seedPostgres(JdbcTemplate jdbc) {
        for (String t : List.of("account_ok", "account_count_diff", "account_data_diff")) {
            jdbc.execute("DROP TABLE IF EXISTS " + t);
        }
        jdbc.execute("CREATE TABLE account_ok (id INT NOT NULL PRIMARY KEY, name VARCHAR(50))");
        jdbc.execute("INSERT INTO account_ok VALUES (1, 'Alice'), (2, 'Bob')");

        jdbc.execute("CREATE TABLE account_count_diff (id INT NOT NULL PRIMARY KEY, name VARCHAR(50))");
        jdbc.execute("INSERT INTO account_count_diff VALUES (1, 'A')");

        jdbc.execute("CREATE TABLE account_data_diff (id INT NOT NULL PRIMARY KEY, name VARCHAR(50))");
        jdbc.execute("INSERT INTO account_data_diff VALUES (1, 'same'), (2, 'postgresValue')");
    }

    @Test
    void runnerComparesMysqlSourceAgainstPostgresTarget() {
        reportWriter.clear();
        runner.runAll();

        assertThat(outcomes(ReportType.TABLE_ROW_COUNT_COMPARISON))
                .anyMatch(o -> o.tableName().equals("account_ok") && o.passed())
                .anyMatch(o -> o.tableName().equals("account_count_diff")
                        && o.failed()
                        && o.description().contains("source has more data"));

        assertThat(outcomes(ReportType.COLUMN_NAME_COMPARISON)).allMatch(CheckOutcome::passed);

        assertThat(outcomes(ReportType.RANDOM_DATA_COMPARISON))
                .anyMatch(o -> o.tableName().equals("account_ok") && o.passed())
                .anyMatch(o -> o.tableName().equals("account_data_diff") && o.failed());
    }

    private List<CheckOutcome> outcomes(ReportType type) {
        return reportWriter.byType.get(type);
    }

    /** Captures outcomes in-memory instead of writing to disk. */
    static class CapturingReportWriter implements ReportWriter {
        final Map<ReportType, List<CheckOutcome>> byType = new EnumMap<>(ReportType.class);

        @Override
        public synchronized void write(
                ReportType type, Instant startedAt, Instant endedAt, List<CheckOutcome> outcomes) {
            byType.put(type, outcomes);
        }

        void clear() {
            byType.clear();
        }
    }
}
