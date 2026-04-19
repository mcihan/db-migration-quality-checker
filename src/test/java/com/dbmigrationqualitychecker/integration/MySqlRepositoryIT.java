package com.dbmigrationqualitychecker.integration;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.MySqlRepository;
import com.dbmigrationqualitychecker.repository.entity.ColumnDetails;
import com.dbmigrationqualitychecker.repository.entity.IndexDetails;
import com.dbmigrationqualitychecker.repository.entity.RecordData;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fast integration test that verifies the MySQL side of the checker against a
 * real MySQL container. Tagged only {@code integration} (not {@code slow}) so
 * it runs on every {@code ./mvnw verify}.
 */
@Tag("integration")
@Testcontainers
class MySqlRepositoryIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("paymentdb")
            .withUsername("tester")
            .withPassword("testerpw")
            .withReuse(true);

    private static MySqlRepository repo;
    private static String schema;

    @BeforeAll
    static void setUp() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(MYSQL.getJdbcUrl());
        ds.setUsername(MYSQL.getUsername());
        ds.setPassword(MYSQL.getPassword());
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setMaximumPoolSize(4);

        NamedParameterJdbcTemplate tpl = new NamedParameterJdbcTemplate((DataSource) ds);
        repo = new MySqlRepository(tpl);
        schema = MYSQL.getDatabaseName();

        JdbcTemplate jdbc = tpl.getJdbcTemplate();
        jdbc.execute("DROP TABLE IF EXISTS CUSTOMER");
        jdbc.execute("CREATE TABLE CUSTOMER (" +
                "ID INT NOT NULL AUTO_INCREMENT," +
                "NAME VARCHAR(50) NOT NULL," +
                "EMAIL VARCHAR(100)," +
                "PRIMARY KEY (ID)," +
                "UNIQUE KEY UX_CUSTOMER_EMAIL (EMAIL)," +
                "KEY IX_CUSTOMER_NAME (NAME))");
        jdbc.execute("INSERT INTO CUSTOMER (NAME, EMAIL) VALUES ('Alice', 'a@example.com'), ('Bob', 'b@example.com')");
    }

    private Table table() {
        return Table.builder().tableName("CUSTOMER").targetSchema(schema).idName("ID").queryCondition("").build();
    }

    @Test
    void returnsSortedColumnNames() {
        QueryResult<String> result = repo.getColumnNames(table());
        assertThat(result.getResult()).containsExactly("EMAIL", "ID", "NAME");
        assertThat(result.getQuery()).contains("INFORMATION_SCHEMA.COLUMNS");
    }

    @Test
    void returnsRowCount() {
        assertThat(repo.getRowCount(table())).isEqualTo(2);
    }

    @Test
    void returnsColumnMetadataIncludingAutoIncrementAndNullability() {
        List<ColumnDetails> cols = repo.getColumnDetails(table());
        ColumnDetails id = cols.stream().filter(c -> c.getColumnName().equals("ID")).findFirst().orElseThrow();
        ColumnDetails email = cols.stream().filter(c -> c.getColumnName().equals("EMAIL")).findFirst().orElseThrow();

        assertThat(id.getColumnType()).isEqualTo("INT");
        assertThat(id.isAutoIncrement()).isTrue();
        assertThat(id.isNullable()).isFalse();
        assertThat(email.isNullable()).isTrue();
        assertThat(email.isAutoIncrement()).isFalse();
    }

    @Test
    void returnsIndexDetails() {
        QueryResult<IndexDetails> result = repo.getIndexDetails(table());
        assertThat(result.getResult()).extracting(IndexDetails::getIndexName)
                .contains("PRIMARY", "UX_CUSTOMER_EMAIL", "IX_CUSTOMER_NAME");
    }

    @Test
    void findsRowsByIds() {
        QueryResult<RecordData> found = repo.findAllByIds(table(), List.of("1", "2"));
        assertThat(found.getResult()).hasSize(2);
        assertThat(found.getQuery()).contains("IN ('1','2')");
    }

    @Test
    void findsByFullColumnMatchWhenIdAbsent() {
        RecordData probe = RecordData.builder().columns(new java.util.HashMap<>(java.util.Map.of(
                "NAME", "Alice",
                "EMAIL", "a@example.com"
        ))).build();

        QueryResult<RecordData> result = repo.getDataReportFromTable(table(), probe);
        assertThat(result.getResult()).hasSize(1);
        assertThat(result.getQuery()).contains("WHERE").contains("NAME = 'Alice'").contains("EMAIL = 'a@example.com'");
    }
}
