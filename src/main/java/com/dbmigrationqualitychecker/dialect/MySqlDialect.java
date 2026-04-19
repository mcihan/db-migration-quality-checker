package com.dbmigrationqualitychecker.dialect;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.repository.UUIDConverter;
import com.dbmigrationqualitychecker.repository.entity.ColumnDetails;
import com.dbmigrationqualitychecker.repository.entity.IndexDetails;
import com.dbmigrationqualitychecker.repository.entity.RecordData;
import com.dbmigrationqualitychecker.util.RowMapUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class MySqlDialect implements DatabaseDialect {

    private static final String DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";

    @Override
    public DatabaseType type() {
        return DatabaseType.MYSQL;
    }

    @Override
    public String driverClassName() {
        return DRIVER_CLASS_NAME;
    }

    @Override
    public QueryResult<String> getColumnNames(NamedParameterJdbcTemplate jdbc, String schema, String tableName) {
        String query = String.format(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s' ORDER BY COLUMN_NAME ASC",
                schema, tableName);
        List<String> columns = jdbc.query(
                query, new MapSqlParameterSource(), (rs, rowNum) -> rs.getString("COLUMN_NAME"));
        Collections.sort(columns);
        return QueryResult.<String>builder().result(columns).query(query).tableName(tableName).build();
    }

    @Override
    public List<ColumnDetails> getColumnDetails(NamedParameterJdbcTemplate jdbc, String schema, String tableName) {
        String sql = String.format(
                """
                SELECT
                    UPPER(COLUMN_NAME) AS COLUMN_NAME,
                    UPPER(SUBSTRING_INDEX(COLUMN_TYPE, '(', 1)) AS COLUMN_TYPE,
                    IS_NULLABLE AS NULLABLE,
                    UPPER(SUBSTRING_INDEX(COLUMN_DEFAULT, '(', 1)) AS COLUMN_DEFAULT,
                    CASE WHEN EXTRA LIKE '%%auto_increment%%' THEN 'YES' ELSE 'NO' END AS AUTO_INCREMENT
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'""",
                schema, tableName);
        return jdbc.query(sql, new MapSqlParameterSource(), RowMapUtils.columnDetailsRowMapper);
    }

    @Override
    public QueryResult<IndexDetails> getIndexDetails(NamedParameterJdbcTemplate jdbc, String schema, String tableName) {
        String sql = String.format(
                """
                SELECT
                    TABLE_NAME,
                    INDEX_NAME,
                    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS COLUMNS,
                    NON_UNIQUE AS 'UNIQUE'
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_NAME = '%s' AND TABLE_SCHEMA = '%s'
                GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE
                """,
                tableName, schema);
        List<IndexDetails> result = jdbc.query(sql, new MapSqlParameterSource(), RowMapUtils.indexDetailsRowMapper);
        return QueryResult.<IndexDetails>builder().query(sql).result(result).build();
    }

    @Override
    public int getRowCount(NamedParameterJdbcTemplate jdbc, String schema, String tableName, String whereClause) {
        String where = whereClause == null ? "" : whereClause;
        String query = String.format("SELECT count(1) AS COUNT FROM %s.%s %s", schema, tableName, where);
        Integer result = jdbc.queryForObject(query, new MapSqlParameterSource(), Integer.class);
        return result == null ? 0 : result;
    }

    @Override
    public QueryResult<RecordData> getRandomData(
            NamedParameterJdbcTemplate jdbc, String schema, String tableName, String idName, int limit) {
        String query = String.format("SELECT * FROM %s.%s LIMIT %d", schema, tableName, limit);
        List<RecordData> result = jdbc.query(query, new MapSqlParameterSource(), rowsWithIdMapper(idName));
        return QueryResult.<RecordData>builder().query(query).result(result).build();
    }

    @Override
    public QueryResult<RecordData> getDataFromTable(
            NamedParameterJdbcTemplate jdbc, String schema, String tableName, int limit) {
        String query = String.format("SELECT * FROM %s.%s LIMIT %d", schema, tableName, limit);
        List<RecordData> result = jdbc.query(query, new MapSqlParameterSource(), fullRowMapper());
        return QueryResult.<RecordData>builder().query(query).result(result).build();
    }

    @Override
    public QueryResult<RecordData> findAllByIds(
            NamedParameterJdbcTemplate jdbc,
            String schema,
            String tableName,
            String idName,
            List<String> ids,
            boolean isHexId) {
        String idList = ids.stream().map(id -> "'" + id + "'").collect(Collectors.joining(",", "(", ")"));
        String query = isHexId
                ? String.format("SELECT * FROM %s.%s WHERE lower(hex(%s)) IN %s", schema, tableName, idName, idList)
                : String.format("SELECT * FROM %s.%s WHERE %s IN %s", schema, tableName, idName, idList);
        List<RecordData> records = jdbc.query(query, new MapSqlParameterSource(), rowsWithIdMapper(idName));
        return QueryResult.<RecordData>builder().tableName(tableName).result(records).query(query).build();
    }

    @Override
    public QueryResult<RecordData> getDataReportFromTable(
            NamedParameterJdbcTemplate jdbc, String schema, String tableName, RecordData record) {
        StringBuilder sql = new StringBuilder(String.format("SELECT * FROM %s.%s WHERE ", schema, tableName));
        boolean first = true;
        for (var entry : record.getColumns().entrySet()) {
            if (!first) {
                sql.append(" AND ");
            }
            first = false;
            sql.append(entry.getKey());
            if (entry.getValue() == null) {
                sql.append(" IS NULL");
            } else {
                sql.append(" = ").append(String.format("'%s'", entry.getValue()));
            }
        }
        List<RecordData> result = jdbc.query(sql.toString(), new MapSqlParameterSource(), fullRowMapper());
        return QueryResult.<RecordData>builder().query(sql.toString()).result(result).build();
    }

    private RowMapper<RecordData> rowsWithIdMapper(String idName) {
        return (rs, rowNum) -> {
            HashMap<String, String> columnsWithValue = new HashMap<>();
            int columnCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rs.getMetaData().getColumnName(i);
                String columnTypeName = rs.getMetaData().getColumnTypeName(i);
                String columnValue = String.valueOf(rs.getString(i));
                try {
                    if (columnTypeName.contains("VARBINARY")) {
                        columnValue = UUIDConverter.fromBytes(rs.getBytes(i)).toString();
                    }
                } catch (Exception ignored) {
                    // fall back to the stringified value
                }
                columnsWithValue.put(columnName.toUpperCase(), columnValue);
            }
            return RecordData.builder()
                    .id(idName == null ? null : rs.getString(idName))
                    .columns(columnsWithValue)
                    .build();
        };
    }

    private RowMapper<RecordData> fullRowMapper() {
        return (rs, rowNum) -> {
            HashMap<String, String> columns = new HashMap<>();
            int columnCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                columns.put(rs.getMetaData().getColumnName(i), rs.getString(i));
            }
            return RecordData.builder().columns(columns).build();
        };
    }
}
