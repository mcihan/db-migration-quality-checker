package com.dbmigrationqualitychecker.dialect;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.repository.entity.ColumnDetails;
import com.dbmigrationqualitychecker.repository.entity.IndexDetails;
import com.dbmigrationqualitychecker.repository.entity.RecordData;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Encapsulates every SQL dialect difference between the supported databases.
 *
 * <p>Implementations own the DB-specific system-catalog queries, pagination
 * syntax, and any value conversions (e.g. UUIDs stored as binary). The generic
 * {@link com.dbmigrationqualitychecker.repository.DatabaseRepository} delegates
 * every call to a dialect so services stay DB-agnostic.
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
            boolean isHexId);

    QueryResult<RecordData> getDataReportFromTable(
            NamedParameterJdbcTemplate jdbc, String schema, String tableName, RecordData record);
}
