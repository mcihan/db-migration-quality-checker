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
import com.dbmigrationqualitychecker.dialect.Db2Dialect;
import com.dbmigrationqualitychecker.dialect.MySqlDialect;
import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.report.ReportWriter;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.Db2Container;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end check against real DB2 + MySQL containers. Tagged {@code slow}
 * so it is skipped on every {@code ./mvnw verify}; run explicitly with
 * {@code ./mvnw verify -Dgroups=integration}.
 */
@Tag("integration")
@Tag("slow")
@Testcontainers
class QualityCheckerIT {

    @Container
    static final Db2Container DB2 = new Db2Container(DockerImageName.parse("icr.io/db2_community/db2:11.5.0.0a"))
            .acceptLicense()
            .withReuse(true)
            .withStartupTimeout(Duration.ofMinutes(10));

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("testdb")
            .withUsername("tester")
            .withPassword("testerpw")
            .withReuse(true);

    private static DatabaseRepository source;
    private static DatabaseRepository target;
    private static String sourceSchema;
    private static String targetSchema;
    private static TableProvider tableProvider;
    private static CapturingReportWriter reportWriter;
    private static CheckRunner runner;
    private static Map<ReportType, MigrationCheck> checks;

    @BeforeAll
    static void setUp() {
        NamedParameterJdbcTemplate sourceTpl = new NamedParameterJdbcTemplate(
                dataSource(DB2.getJdbcUrl(), DB2.getUsername(), DB2.getPassword(), "com.ibm.db2.jcc.DB2Driver"));
        NamedParameterJdbcTemplate targetTpl = new NamedParameterJdbcTemplate(
                dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword(), "com.mysql.cj.jdbc.Driver"));

        sourceSchema = DB2.getUsername().toUpperCase();
        targetSchema = MYSQL.getDatabaseName();

        source = new DatabaseRepository(sourceTpl, new Db2Dialect(), DatabaseRepository.Side.SOURCE, 100);
        target = new DatabaseRepository(targetTpl, new MySqlDialect(), DatabaseRepository.Side.TARGET, 100);

        seedSchemas(sourceTpl.getJdbcTemplate(), targetTpl.getJdbcTemplate());

        tableProvider = mock(TableProvider.class);
        when(tableProvider.getTables()).thenReturn(buildTables());

        checks = new EnumMap<>(ReportType.class);
        checks.put(ReportType.COLUMN_NAME_COMPARISON, new ColumnNamesCheck(source, target));
        checks.put(ReportType.COLUMN_METADATA_COMPARISON, new ColumnMetadataCheck(source, target));
        checks.put(ReportType.INDEX_COMPARISON, new IndexCheck(source, target));
        checks.put(ReportType.TABLE_ROW_COUNT_COMPARISON, new RowCountCheck(source, target));
        checks.put(ReportType.RANDOM_DATA_COMPARISON, new RandomDataCheck(source, target));

        reportWriter = new CapturingReportWriter();
        CheckSelection selection = new CheckSelection(false, false, false);
        runner = new CheckRunner(List.copyOf(checks.values()), tableProvider, selection, reportWriter);
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

    private static List<Table> buildTables() {
        return List.of(
                new Table("ACCOUNT_OK", sourceSchema, targetSchema, "ID", "", false),
                new Table("ACCOUNT_COUNT_DIFF", sourceSchema, targetSchema, "ID", "", false),
                new Table("ACCOUNT_DATA_DIFF", sourceSchema, targetSchema, "ID", "", false),
                new Table("ACCOUNT_COLS_DIFF", sourceSchema, targetSchema, null, "", false),
                new Table("ACCOUNT_TYPE_DIFF", sourceSchema, targetSchema, null, "", false),
                new Table("ACCOUNT_INDEX_DIFF", sourceSchema, targetSchema, null, "", false));
    }

    private static void seedSchemas(JdbcTemplate src, JdbcTemplate tgt) {
        List<String> tables = List.of(
                "ACCOUNT_OK",
                "ACCOUNT_COUNT_DIFF",
                "ACCOUNT_DATA_DIFF",
                "ACCOUNT_COLS_DIFF",
                "ACCOUNT_TYPE_DIFF",
                "ACCOUNT_INDEX_DIFF");
        for (String t : tables) {
            dropSilently(src, t);
            dropSilently(tgt, t);
        }

        src.execute("CREATE TABLE ACCOUNT_OK (ID INTEGER NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        src.execute("INSERT INTO ACCOUNT_OK VALUES (1, 'Alice'), (2, 'Bob')");
        tgt.execute("CREATE TABLE ACCOUNT_OK (ID INT NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        tgt.execute("INSERT INTO ACCOUNT_OK VALUES (1, 'Alice'), (2, 'Bob')");

        src.execute("CREATE TABLE ACCOUNT_COUNT_DIFF (ID INTEGER NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        src.execute("INSERT INTO ACCOUNT_COUNT_DIFF VALUES (1, 'A'), (2, 'B'), (3, 'C')");
        tgt.execute("CREATE TABLE ACCOUNT_COUNT_DIFF (ID INT NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        tgt.execute("INSERT INTO ACCOUNT_COUNT_DIFF VALUES (1, 'A')");

        src.execute("CREATE TABLE ACCOUNT_DATA_DIFF (ID INTEGER NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        src.execute("INSERT INTO ACCOUNT_DATA_DIFF VALUES (1, 'same'), (2, 'sourceValue')");
        tgt.execute("CREATE TABLE ACCOUNT_DATA_DIFF (ID INT NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        tgt.execute("INSERT INTO ACCOUNT_DATA_DIFF VALUES (1, 'same'), (2, 'targetValue')");

        src.execute("CREATE TABLE ACCOUNT_COLS_DIFF (ID INTEGER NOT NULL, NAME VARCHAR(50), EMAIL VARCHAR(100))");
        tgt.execute("CREATE TABLE ACCOUNT_COLS_DIFF (ID INT NOT NULL, NAME VARCHAR(50))");

        src.execute("CREATE TABLE ACCOUNT_TYPE_DIFF (ID INTEGER NOT NULL, AMOUNT DECIMAL(10,2))");
        tgt.execute("CREATE TABLE ACCOUNT_TYPE_DIFF (ID INT NOT NULL, AMOUNT VARCHAR(20))");

        src.execute("CREATE TABLE ACCOUNT_INDEX_DIFF (ID INTEGER NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        src.execute("CREATE INDEX IDX_ACC_INDEX_NAME ON ACCOUNT_INDEX_DIFF (NAME)");
        tgt.execute("CREATE TABLE ACCOUNT_INDEX_DIFF (ID INT NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
    }

    private static void dropSilently(JdbcTemplate jdbc, String table) {
        try {
            jdbc.execute("DROP TABLE " + table);
        } catch (Exception ignored) {
            // fresh container, table didn't exist — fine
        }
    }

    @Test
    void runnerProducesExpectedOutcomesForEveryCheck() {
        reportWriter.clear();
        runner.runAll();

        assertThat(outcomes(ReportType.TABLE_ROW_COUNT_COMPARISON))
                .anyMatch(o -> o.tableName().equals("ACCOUNT_OK") && o.passed())
                .anyMatch(o -> o.tableName().equals("ACCOUNT_COUNT_DIFF")
                        && o.failed()
                        && o.description().contains("source has more data"));

        assertThat(outcomes(ReportType.COLUMN_NAME_COMPARISON))
                .anyMatch(o -> o.tableName().equals("ACCOUNT_OK") && o.passed())
                .anyMatch(o -> o.tableName().equals("ACCOUNT_COLS_DIFF") && o.failed());

        assertThat(outcomes(ReportType.COLUMN_METADATA_COMPARISON))
                .anyMatch(o -> o.tableName().equals("ACCOUNT_TYPE_DIFF")
                        && o.failed()
                        && o.description().contains("Column Type Mismatch"));

        assertThat(outcomes(ReportType.INDEX_COMPARISON))
                .anyMatch(o -> o.tableName().equals("ACCOUNT_INDEX_DIFF")
                        && o.failed()
                        && o.description().contains("There is no index on target"));

        assertThat(outcomes(ReportType.RANDOM_DATA_COMPARISON))
                .anyMatch(o -> o.tableName().equals("ACCOUNT_DATA_DIFF") && o.failed());
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

    /** Keeps the compiler happy about the unused import if the test flow changes. */
    @SuppressWarnings("unused")
    private static void referenceAllReportTypesToAvoidStaleImports() {
        EnumSet.allOf(ReportType.class);
    }
}
