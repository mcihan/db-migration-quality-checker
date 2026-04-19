package com.dbmigrationqualitychecker.dialect;

import com.dbmigrationqualitychecker.model.ColumnDetails;
import com.dbmigrationqualitychecker.model.IndexDetails;
import com.dbmigrationqualitychecker.model.QueryResult;
import com.dbmigrationqualitychecker.model.RecordData;
import com.dbmigrationqualitychecker.repository.UUIDConverter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class MySqlDialect implements DatabaseDialect {

    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

    @Override
    public DatabaseType type() {
        return DatabaseType.MYSQL;
    }

    @Override
    public String driverClassName() {
        return DRIVER;
    }

    @Override
    public QueryResult<String> getColumnNames(NamedParameterJdbcTemplate jdbc, String schema, String tableName) {
        String sql = String.format(
                """
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'
                ORDER BY COLUMN_NAME ASC""",
                schema, tableName);
        List<String> columns =
                jdbc.query(sql, new MapSqlParameterSource(), (rs, i) -> rs.getString("COLUMN_NAME"));
        Collections.sort(columns);
        return QueryResult.of(sql, tableName, columns);
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
        return jdbc.query(sql, new MapSqlParameterSource(), COLUMN_DETAILS_MAPPER);
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
        List<IndexDetails> result = jdbc.query(sql, new MapSqlParameterSource(), INDEX_DETAILS_MAPPER);
        return QueryResult.of(sql, tableName, result);
    }

    @Override
    public int getRowCount(NamedParameterJdbcTemplate jdbc, String schema, String tableName, String whereClause) {
        String where = whereClause == null ? "" : whereClause;
        String sql = String.format("SELECT count(1) AS COUNT FROM %s.%s %s", schema, tableName, where);
        Integer result = jdbc.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
        return result == null ? 0 : result;
    }

    @Override
    public QueryResult<RecordData> getRandomData(
            NamedParameterJdbcTemplate jdbc, String schema, String tableName, String idName, int limit) {
        String sql = String.format("SELECT * FROM %s.%s LIMIT %d", schema, tableName, limit);
        List<RecordData> rows = jdbc.query(sql, new MapSqlParameterSource(), rowMapperWithId(idName));
        return QueryResult.of(sql, tableName, rows);
    }

    @Override
    public QueryResult<RecordData> getDataFromTable(
            NamedParameterJdbcTemplate jdbc, String schema, String tableName, int limit) {
        String sql = String.format("SELECT * FROM %s.%s LIMIT %d", schema, tableName, limit);
        List<RecordData> rows = jdbc.query(sql, new MapSqlParameterSource(), fullRowMapper());
        return QueryResult.of(sql, tableName, rows);
    }

    @Override
    public QueryResult<RecordData> findAllByIds(
            NamedParameterJdbcTemplate jdbc,
            String schema,
            String tableName,
            String idName,
            List<String> ids,
            boolean hexId) {
        String idList = ids.stream().map(id -> "'" + id + "'").collect(Collectors.joining(",", "(", ")"));
        String sql = hexId
                ? String.format("SELECT * FROM %s.%s WHERE lower(hex(%s)) IN %s", schema, tableName, idName, idList)
                : String.format("SELECT * FROM %s.%s WHERE %s IN %s", schema, tableName, idName, idList);
        List<RecordData> rows = jdbc.query(sql, new MapSqlParameterSource(), rowMapperWithId(idName));
        return QueryResult.of(sql, tableName, rows);
    }

    @Override
    public QueryResult<RecordData> findByColumns(
            NamedParameterJdbcTemplate jdbc, String schema, String tableName, RecordData record) {
        String sql = Db2Dialect.buildWhereByColumns(schema, tableName, record.columns());
        List<RecordData> rows = jdbc.query(sql, new MapSqlParameterSource(), fullRowMapper());
        return QueryResult.of(sql, tableName, rows);
    }

    private RowMapper<RecordData> rowMapperWithId(String idName) {
        return (rs, rowNum) -> {
            Map<String, String> cols = new HashMap<>();
            int count = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= count; i++) {
                String name = rs.getMetaData().getColumnName(i);
                String typeName = rs.getMetaData().getColumnTypeName(i);
                String value = String.valueOf(rs.getString(i));
                try {
                    if (typeName.contains("VARBINARY")) {
                        value = UUIDConverter.fromBytes(rs.getBytes(i)).toString();
                    }
                } catch (Exception ignored) {
                    // fall back to stringified value
                }
                cols.put(name.toUpperCase(), value);
            }
            return new RecordData(idName == null ? null : rs.getString(idName), cols);
        };
    }

    private RowMapper<RecordData> fullRowMapper() {
        return (rs, rowNum) -> {
            Map<String, String> cols = new HashMap<>();
            int count = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= count; i++) {
                cols.put(rs.getMetaData().getColumnName(i), rs.getString(i));
            }
            return RecordData.of(cols);
        };
    }

    private static final RowMapper<ColumnDetails> COLUMN_DETAILS_MAPPER = (rs, rowNum) -> {
        String rawDefault = rs.getString("COLUMN_DEFAULT");
        String colDefault = (rawDefault == null || rawDefault.isBlank()) ? rawDefault : rawDefault.replace("'", "");
        if (StringUtils.equalsAnyIgnoreCase(colDefault, "null")) {
            colDefault = null;
        }
        return new ColumnDetails(
                rs.getString("COLUMN_NAME"),
                rs.getString("COLUMN_TYPE"),
                colDefault,
                StringUtils.equalsAny(rs.getString("NULLABLE"), "YES", "Y"),
                "YES".equals(rs.getString("AUTO_INCREMENT")));
    };

    private static final RowMapper<IndexDetails> INDEX_DETAILS_MAPPER = (rs, rowNum) -> {
        String columns = rs.getString("COLUMNS");
        return new IndexDetails(
                rs.getString("TABLE_NAME"),
                rs.getString("INDEX_NAME"),
                columns == null ? null : columns.replace(" ", ""),
                rs.getString("UNIQUE"));
    };
}
