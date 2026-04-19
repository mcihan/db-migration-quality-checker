package com.dbmigrationqualitychecker.repository;

import com.dbmigrationqualitychecker.dialect.DatabaseDialect;
import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.entity.ColumnDetails;
import com.dbmigrationqualitychecker.repository.entity.IndexDetails;
import com.dbmigrationqualitychecker.repository.entity.RecordData;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Generic JDBC repository whose behaviour is parameterised by a
 * {@link DatabaseDialect} and a {@link Side}. Exactly two instances exist at
 * runtime — one wired as the migration source and one as the migration target —
 * each with its own dialect/connection.
 */
@RequiredArgsConstructor
public class DatabaseRepository {

    public enum Side {
        SOURCE,
        TARGET;

        public String schemaOf(Table table) {
            return this == SOURCE ? table.getSourceSchema() : table.getTargetSchema();
        }
    }

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Getter
    private final DatabaseDialect dialect;

    @Getter
    private final Side side;

    private final int randomDataCount;

    private String schema(Table table) {
        return side.schemaOf(table);
    }

    public QueryResult<String> getColumnNames(Table table) {
        return dialect.getColumnNames(jdbcTemplate, schema(table), table.getTableName());
    }

    public List<ColumnDetails> getColumnDetails(Table table) {
        return dialect.getColumnDetails(jdbcTemplate, schema(table), table.getTableName());
    }

    public QueryResult<IndexDetails> getIndexDetails(Table table) {
        return dialect.getIndexDetails(jdbcTemplate, schema(table), table.getTableName());
    }

    public int getRowCount(Table table) {
        return dialect.getRowCount(jdbcTemplate, schema(table), table.getTableName(), table.getQueryCondition());
    }

    public QueryResult<RecordData> getRandomData(Table table) {
        return dialect.getRandomData(
                jdbcTemplate, schema(table), table.getTableName(), table.getIdName(), randomDataCount);
    }

    public QueryResult<RecordData> getDataFromTable(Table table) {
        return dialect.getDataFromTable(jdbcTemplate, schema(table), table.getTableName(), randomDataCount);
    }

    public QueryResult<RecordData> findAllByIds(Table table, List<String> ids) {
        return dialect.findAllByIds(
                jdbcTemplate, schema(table), table.getTableName(), table.getIdName(), ids, table.isHexId());
    }

    public QueryResult<RecordData> getDataReportFromTable(Table table, RecordData record) {
        return dialect.getDataReportFromTable(jdbcTemplate, schema(table), table.getTableName(), record);
    }
}
