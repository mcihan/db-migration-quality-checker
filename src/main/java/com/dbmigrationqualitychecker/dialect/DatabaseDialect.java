package com.dbmigrationqualitychecker.dialect;

import com.dbmigrationqualitychecker.model.ColumnDetails;
import com.dbmigrationqualitychecker.model.IndexDetails;
import com.dbmigrationqualitychecker.model.QueryResult;
import com.dbmigrationqualitychecker.model.RecordData;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Encapsulates every SQL-dialect difference between the supported databases.
 *
 * <p>Adding a third engine (Postgres, Oracle, …) is just a new implementation
 * plus a case in {@link DatabaseType}.
 */
public interface DatabaseDialect {

    DatabaseType type();

    String driverClassName();

    QueryResult<String> getColumnNames(NamedParameterJdbcTemplate jdbc, String schema, String tableName);

    List<ColumnDetails> getColumnDetails(NamedParameterJdbcTemplate jdbc, String schema, String tableName);

    QueryResult<IndexDetails> getIndexDetails(NamedParameterJdbcTemplate jdbc, String schema, String tableName);

    int getRowCount(NamedParameterJdbcTemplate jdbc, String schema, String tableName, String whereClause);

    QueryResult<RecordData> getRandomData(
            NamedParameterJdbcTemplate jdbc, String schema, String tableName, String idName, int limit);

    QueryResult<RecordData> getDataFromTable(
            NamedParameterJdbcTemplate jdbc, String schema, String tableName, int limit);

    QueryResult<RecordData> findAllByIds(
            NamedParameterJdbcTemplate jdbc,
            String schema,
            String tableName,
            String idName,
            List<String> ids,
            boolean hexId);

    QueryResult<RecordData> findByColumns(
            NamedParameterJdbcTemplate jdbc, String schema, String tableName, RecordData record);
}
