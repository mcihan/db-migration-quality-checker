package com.dbmigrationqualitychecker.repository;

import com.dbmigrationqualitychecker.dialect.DatabaseDialect;
import com.dbmigrationqualitychecker.model.ColumnDetails;
import com.dbmigrationqualitychecker.model.IndexDetails;
import com.dbmigrationqualitychecker.model.QueryResult;
import com.dbmigrationqualitychecker.model.RecordData;
import com.dbmigrationqualitychecker.model.Table;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Facade over a JDBC template + dialect. Exactly two instances exist at
 * runtime — one wired as the migration source and one as the target — each
 * knowing which schema field of {@link Table} to use.
 */
@RequiredArgsConstructor
public class DatabaseRepository {

    public enum Side {
        SOURCE,
        TARGET;

        public String schemaOf(Table table) {
            return this == SOURCE ? table.sourceSchema() : table.targetSchema();
        }
    }

    private final NamedParameterJdbcTemplate jdbc;

    @Getter
    private final DatabaseDialect dialect;

    @Getter
    private final Side side;

    private final int randomDataCount;

    private String schema(Table table) {
        return side.schemaOf(table);
    }

    public QueryResult<String> getColumnNames(Table table) {
        return dialect.getColumnNames(jdbc, schema(table), table.tableName());
    }

    public List<ColumnDetails> getColumnDetails(Table table) {
        return dialect.getColumnDetails(jdbc, schema(table), table.tableName());
    }

    public QueryResult<IndexDetails> getIndexDetails(Table table) {
        return dialect.getIndexDetails(jdbc, schema(table), table.tableName());
    }

    public int getRowCount(Table table) {
        return dialect.getRowCount(jdbc, schema(table), table.tableName(), table.queryCondition());
    }

    public QueryResult<RecordData> getRandomData(Table table) {
        return dialect.getRandomData(jdbc, schema(table), table.tableName(), table.idName(), randomDataCount);
    }

    public QueryResult<RecordData> getDataFromTable(Table table) {
        return dialect.getDataFromTable(jdbc, schema(table), table.tableName(), randomDataCount);
    }

    public QueryResult<RecordData> findAllByIds(Table table, List<String> ids) {
        return dialect.findAllByIds(jdbc, schema(table), table.tableName(), table.idName(), ids, table.hexId());
    }

    public QueryResult<RecordData> findByColumns(Table table, RecordData record) {
        return dialect.findByColumns(jdbc, schema(table), table.tableName(), record);
    }
}
