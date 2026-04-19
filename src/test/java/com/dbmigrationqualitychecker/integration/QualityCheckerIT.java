package com.dbmigrationqualitychecker.integration;

import com.dbmigrationqualitychecker.report.ReportTestSupport;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.Db2Repository;
import com.dbmigrationqualitychecker.repository.MySqlRepository;
import com.dbmigrationqualitychecker.service.ColumnMetadataComparisonService;
import com.dbmigrationqualitychecker.service.ColumnNameComparisonService;
import com.dbmigrationqualitychecker.service.IndexComparisonService;
import com.dbmigrationqualitychecker.service.RandomDataComparisonService;
import com.dbmigrationqualitychecker.service.TableProvider;
import com.dbmigrationqualitychecker.service.TableRowCountComparisonService;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.Db2Container;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end checks for the quality checker against real DB2 + MySQL containers.
 *
 * <p>DB2's first boot is expensive (the image runs a full instance-create). The
 * containers are therefore marked reusable: the very first run provisions them,
 * later runs reattach in seconds. To unlock reuse for your machine (one-time):
 * <pre>
 *   echo "testcontainers.reuse.enable=true" &gt;&gt; ~/.testcontainers.properties
 * </pre>
 * Run via {@code ./mvnw verify} — Surefire skips the {@code integration} tag so
 * plain {@code mvn test} stays fast.
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
            .withDatabaseName("paymentdb")
            .withUsername("tester")
            .withPassword("testerpw")
            .withReuse(true);

    private static NamedParameterJdbcTemplate db2Tpl;
    private static NamedParameterJdbcTemplate mysqlTpl;
    private static Db2Repository db2Repo;
    private static MySqlRepository mysqlRepo;

    private static String db2Schema;
    private static String mysqlSchema;

    private static List<Table> tables;
    private static TableProvider tableProvider;

    @BeforeAll
    static void setUp() {
        db2Tpl = new NamedParameterJdbcTemplate(dataSource(DB2.getJdbcUrl(), DB2.getUsername(), DB2.getPassword(),
                "com.ibm.db2.jcc.DB2Driver"));
        mysqlTpl = new NamedParameterJdbcTemplate(dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword(),
                "com.mysql.cj.jdbc.Driver"));

        db2Schema = DB2.getUsername().toUpperCase();
        mysqlSchema = MYSQL.getDatabaseName();

        db2Repo = new Db2Repository(db2Tpl);
        ReflectionTestUtils.setField(db2Repo, "randomDataCount", 100);
        mysqlRepo = new MySqlRepository(mysqlTpl);

        seedSchemas();

        tables = buildTableList();
        tableProvider = mock(TableProvider.class);
        when(tableProvider.getTables()).thenReturn(tables);
    }

    @BeforeEach
    @AfterEach
    void cleanReports() throws IOException {
        ReportTestSupport.cleanReportDir();
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

    private static List<Table> buildTableList() {
        return List.of(
                Table.builder().sourceSchema(db2Schema).targetSchema(mysqlSchema)
                        .tableName("ACCOUNT_OK").idName("ID").queryCondition("").build(),
                Table.builder().sourceSchema(db2Schema).targetSchema(mysqlSchema)
                        .tableName("ACCOUNT_COUNT_DIFF").idName("ID").queryCondition("").build(),
                Table.builder().sourceSchema(db2Schema).targetSchema(mysqlSchema)
                        .tableName("ACCOUNT_DATA_DIFF").idName("ID").queryCondition("").build(),
                Table.builder().sourceSchema(db2Schema).targetSchema(mysqlSchema)
                        .tableName("ACCOUNT_COLS_DIFF").queryCondition("").build(),
                Table.builder().sourceSchema(db2Schema).targetSchema(mysqlSchema)
                        .tableName("ACCOUNT_TYPE_DIFF").queryCondition("").build(),
                Table.builder().sourceSchema(db2Schema).targetSchema(mysqlSchema)
                        .tableName("ACCOUNT_INDEX_DIFF").queryCondition("").build()
        );
    }

    private static void seedSchemas() {
        JdbcTemplate db2 = db2Tpl.getJdbcTemplate();
        JdbcTemplate mysql = mysqlTpl.getJdbcTemplate();

        List<String> tableNames = List.of("ACCOUNT_OK", "ACCOUNT_COUNT_DIFF", "ACCOUNT_DATA_DIFF",
                "ACCOUNT_COLS_DIFF", "ACCOUNT_TYPE_DIFF", "ACCOUNT_INDEX_DIFF");
        for (String t : tableNames) {
            tryExec(db2, "DROP TABLE " + t);
            tryExec(mysql, "DROP TABLE " + t);
        }

        // Matching table - all checks should pass
        db2.execute("CREATE TABLE ACCOUNT_OK (ID INTEGER NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        db2.execute("INSERT INTO ACCOUNT_OK VALUES (1, 'Alice'), (2, 'Bob')");
        mysql.execute("CREATE TABLE ACCOUNT_OK (ID INT NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        mysql.execute("INSERT INTO ACCOUNT_OK VALUES (1, 'Alice'), (2, 'Bob')");

        // Row count mismatch
        db2.execute("CREATE TABLE ACCOUNT_COUNT_DIFF (ID INTEGER NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        db2.execute("INSERT INTO ACCOUNT_COUNT_DIFF VALUES (1, 'A'), (2, 'B'), (3, 'C')");
        mysql.execute("CREATE TABLE ACCOUNT_COUNT_DIFF (ID INT NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        mysql.execute("INSERT INTO ACCOUNT_COUNT_DIFF VALUES (1, 'A')");

        // Row data mismatch - same count, different NAME for ID=2
        db2.execute("CREATE TABLE ACCOUNT_DATA_DIFF (ID INTEGER NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        db2.execute("INSERT INTO ACCOUNT_DATA_DIFF VALUES (1, 'same'), (2, 'db2value')");
        mysql.execute("CREATE TABLE ACCOUNT_DATA_DIFF (ID INT NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        mysql.execute("INSERT INTO ACCOUNT_DATA_DIFF VALUES (1, 'same'), (2, 'mysqlvalue')");

        // Column name mismatch - mysql is missing a column
        db2.execute("CREATE TABLE ACCOUNT_COLS_DIFF (ID INTEGER NOT NULL, NAME VARCHAR(50), EMAIL VARCHAR(100))");
        mysql.execute("CREATE TABLE ACCOUNT_COLS_DIFF (ID INT NOT NULL, NAME VARCHAR(50))");

        // Type mismatch
        db2.execute("CREATE TABLE ACCOUNT_TYPE_DIFF (ID INTEGER NOT NULL, AMOUNT DECIMAL(10,2))");
        mysql.execute("CREATE TABLE ACCOUNT_TYPE_DIFF (ID INT NOT NULL, AMOUNT VARCHAR(20))");

        // Index mismatch - DB2 has an index MySQL lacks
        db2.execute("CREATE TABLE ACCOUNT_INDEX_DIFF (ID INTEGER NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
        db2.execute("CREATE INDEX IDX_ACC_INDEX_NAME ON ACCOUNT_INDEX_DIFF (NAME)");
        mysql.execute("CREATE TABLE ACCOUNT_INDEX_DIFF (ID INT NOT NULL, NAME VARCHAR(50), PRIMARY KEY (ID))");
    }

    private static void tryExec(JdbcTemplate jdbc, String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception ignored) {
            // Table may not exist on a fresh container; drops are best-effort.
        }
    }

    @Test
    void rowCountCheckReportsMatchesAndMismatches() throws IOException {
        new TableRowCountComparisonService(db2Repo, mysqlRepo, tableProvider).findDiff();

        String report = ReportTestSupport.readReport(ReportType.TABLE_ROW_COUNT_COMPARISON);
        assertThat(report)
                .contains("[Test PASSED] : ACCOUNT_OK")
                .contains("[Test FAILED] : ACCOUNT_COUNT_DIFF")
                .contains("DB2 has more data");
    }

    @Test
    void columnNameCheckDetectsMissingColumn() throws IOException {
        new ColumnNameComparisonService(db2Repo, mysqlRepo, tableProvider).findDiff();

        String report = ReportTestSupport.readReport(ReportType.COLUMN_NAME_COMPARISON);
        assertThat(report)
                .contains("[Test PASSED] : ACCOUNT_OK")
                .contains("[Test FAILED] : ACCOUNT_COLS_DIFF");
    }

    @Test
    void columnMetadataCheckDetectsTypeMismatch() throws IOException {
        new ColumnMetadataComparisonService(db2Repo, mysqlRepo, tableProvider).findDiff();

        String report = ReportTestSupport.readReport(ReportType.COLUMN_METADATA_COMPARISON);
        assertThat(report)
                .contains("[Test FAILED] : ACCOUNT_TYPE_DIFF")
                .contains("Column Type Mismatch");
    }

    @Test
    void indexCheckDetectsMissingIndex() throws IOException {
        new IndexComparisonService(db2Repo, mysqlRepo, tableProvider).findDiff();

        String report = ReportTestSupport.readReport(ReportType.INDEX_COMPARISON);
        assertThat(report)
                .contains("[Test FAILED] : ACCOUNT_INDEX_DIFF")
                .contains("There is no index on mysql");
    }

    @Test
    void randomDataCheckDetectsValueMismatch() throws IOException {
        TableProvider pkOnly = mock(TableProvider.class);
        when(pkOnly.getTables()).thenReturn(List.of(
                Table.builder().sourceSchema(db2Schema).targetSchema(mysqlSchema)
                        .tableName("ACCOUNT_OK").idName("ID").queryCondition("").build(),
                Table.builder().sourceSchema(db2Schema).targetSchema(mysqlSchema)
                        .tableName("ACCOUNT_DATA_DIFF").idName("ID").queryCondition("").build()
        ));

        new RandomDataComparisonService(db2Repo, mysqlRepo, pkOnly).findDiff();

        String report = ReportTestSupport.readReport(ReportType.RANDOM_DATA_COMPARISON);
        assertThat(report)
                .contains("[Test PASSED] : ACCOUNT_OK")
                .contains("[Test FAILED] : ACCOUNT_DATA_DIFF")
                .contains("Failed column: NAME");
    }

}
