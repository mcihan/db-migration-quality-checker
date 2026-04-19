package com.dbmigrationqualitychecker.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbmigrationqualitychecker.dialect.PostgresDialect;
import com.dbmigrationqualitychecker.model.ColumnDetails;
import com.dbmigrationqualitychecker.model.IndexDetails;
import com.dbmigrationqualitychecker.model.QueryResult;
import com.dbmigrationqualitychecker.model.RecordData;
import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@Testcontainers
class PostgresDialectIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("testdb")
            .withUsername("tester")
            .withPassword("testerpw")
            .withReuse(true);

    private static DatabaseRepository repo;
    private static final String SCHEMA = "public";

    @BeforeAll
    static void setUp() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(4);

        NamedParameterJdbcTemplate tpl = new NamedParameterJdbcTemplate((DataSource) ds);
        repo = new DatabaseRepository(tpl, new PostgresDialect(), DatabaseRepository.Side.TARGET, 100);

        JdbcTemplate jdbc = tpl.getJdbcTemplate();
        jdbc.execute("DROP TABLE IF EXISTS customer");
        jdbc.execute(
                """
                CREATE TABLE customer (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(50) NOT NULL,
                    email VARCHAR(100) UNIQUE
                )""");
        jdbc.execute("CREATE INDEX ix_customer_name ON customer (name)");
        jdbc.execute(
                "INSERT INTO customer (name, email) VALUES ('Alice', 'a@example.com'), ('Bob', 'b@example.com')");
    }

    private Table customer() {
        return new Table("customer", null, SCHEMA, "id", "", false);
    }

    @Test
    void returnsSortedColumnNames() {
        QueryResult<String> result = repo.getColumnNames(customer());
        assertThat(result.result()).containsExactly("EMAIL", "ID", "NAME");
    }

    @Test
    void returnsRowCount() {
        assertThat(repo.getRowCount(customer())).isEqualTo(2);
    }

    @Test
    void columnMetadataUsesCanonicalTypeNames() {
        List<ColumnDetails> cols = repo.getColumnDetails(customer());
        ColumnDetails id = cols.stream()
                .filter(c -> c.columnName().equals("ID"))
                .findFirst()
                .orElseThrow();
        ColumnDetails email = cols.stream()
                .filter(c -> c.columnName().equals("EMAIL"))
                .findFirst()
                .orElseThrow();

        assertThat(id.columnType()).isEqualTo("INT");
        assertThat(id.autoIncrement()).isTrue();
        assertThat(id.nullable()).isFalse();
        assertThat(email.columnType()).isEqualTo("VARCHAR");
        assertThat(email.nullable()).isTrue();
        assertThat(email.autoIncrement()).isFalse();
    }

    @Test
    void indexDetailsCoversPrimaryAndSecondary() {
        QueryResult<IndexDetails> result = repo.getIndexDetails(customer());
        assertThat(result.result())
                .extracting(IndexDetails::columnNames)
                .contains("id", "name", "email");
    }

    @Test
    void findsRowsByIds() {
        QueryResult<RecordData> found = repo.findAllByIds(customer(), List.of("1", "2"));
        assertThat(found.result()).hasSize(2);
        assertThat(found.query()).contains("IN ('1','2')");
    }

    @Test
    void findsByFullColumnMatch() {
        Map<String, String> cols = new HashMap<>();
        cols.put("name", "Alice");
        cols.put("email", "a@example.com");
        RecordData probe = RecordData.of(cols);

        QueryResult<RecordData> result = repo.findByColumns(customer(), probe);
        assertThat(result.result()).hasSize(1);
        assertThat(result.query()).contains("WHERE").contains("name = 'Alice'");
    }
}
