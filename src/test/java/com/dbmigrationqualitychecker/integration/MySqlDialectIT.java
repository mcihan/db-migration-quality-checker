package com.dbmigrationqualitychecker.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbmigrationqualitychecker.dialect.MySqlDialect;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@Testcontainers
class MySqlDialectIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("testdb")
            .withUsername("tester")
            .withPassword("testerpw")
            .withReuse(true);

    private static DatabaseRepository repo;
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
        repo = new DatabaseRepository(tpl, new MySqlDialect(), DatabaseRepository.Side.TARGET, 100);
        schema = MYSQL.getDatabaseName();

        JdbcTemplate jdbc = tpl.getJdbcTemplate();
        jdbc.execute("DROP TABLE IF EXISTS CUSTOMER");
        jdbc.execute("CREATE TABLE CUSTOMER ("
                + "ID INT NOT NULL AUTO_INCREMENT,"
                + "NAME VARCHAR(50) NOT NULL,"
                + "EMAIL VARCHAR(100),"
                + "PRIMARY KEY (ID),"
                + "UNIQUE KEY UX_CUSTOMER_EMAIL (EMAIL),"
                + "KEY IX_CUSTOMER_NAME (NAME))");
        jdbc.execute("INSERT INTO CUSTOMER (NAME, EMAIL) "
                + "VALUES ('Alice', 'a@example.com'), ('Bob', 'b@example.com')");
    }

    private Table customer() {
        return new Table("CUSTOMER", null, schema, "ID", "", false);
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
    void returnsColumnMetadataIncludingAutoIncrementAndNullability() {
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
        assertThat(email.nullable()).isTrue();
        assertThat(email.autoIncrement()).isFalse();
    }

    @Test
    void returnsIndexDetails() {
        QueryResult<IndexDetails> result = repo.getIndexDetails(customer());
        assertThat(result.result())
                .extracting(IndexDetails::indexName)
                .contains("PRIMARY", "UX_CUSTOMER_EMAIL", "IX_CUSTOMER_NAME");
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
        cols.put("NAME", "Alice");
        cols.put("EMAIL", "a@example.com");
        RecordData probe = RecordData.of(cols);

        QueryResult<RecordData> result = repo.findByColumns(customer(), probe);
        assertThat(result.result()).hasSize(1);
        assertThat(result.query())
                .contains("WHERE")
                .contains("NAME = 'Alice'")
                .contains("EMAIL = 'a@example.com'");
    }
}
