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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Slf4j
public class Db2Dialect implements DatabaseDialect {

    private static final String DRIVER = "com.ibm.db2.jcc.DB2Driver";
    private static final String CHAR_FOR_BIT_DATA = "CHAR FOR BIT DATA";

    @Override
    public DatabaseType type() {
        return DatabaseType.DB2;
    }

    @Override
    public String driverClassName() {
        return DRIVER;
    }

    @Override
    public QueryResult<String> getColumnNames(NamedParameterJdbcTemplate jdbc, String schema, String tableName) {
        String sql = String.format(
                """
                SELECT COLNAME AS COLUMN_NAME FROM SYSCAT.COLUMNS
                WHERE TABSCHEMA = '%s' AND TABNAME = '%s'
                ORDER BY COLNAME ASC""",
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
                    COLNAME AS COLUMN_NAME,
                    TYPENAME AS COLUMN_TYPE,
                    CASE WHEN NULLS = 'Y' THEN 'YES' ELSE 'NO' END AS NULLABLE,
                    DEFAULT AS COLUMN_DEFAULT,
                    CASE WHEN IDENTITY = 'Y' THEN 'YES' ELSE 'NO' END AS AUTO_INCREMENT
                FROM SYSCAT.COLUMNS
                WHERE TABSCHEMA = '%s' AND TABNAME = '%s'""",
                schema, tableName);
        return jdbc.query(sql, new MapSqlParameterSource(), COLUMN_DETAILS_MAPPER);
    }

    @Override
    public QueryResult<IndexDetails> getIndexDetails(NamedParameterJdbcTemplate jdbc, String schema, String tableName) {
        String sql = String.format(
                """
                SELECT TABNAME AS TABLE_NAME,
                    INDNAME AS INDEX_NAME,
                    LTRIM( REGEXP_REPLACE( REPLACE(COLNAMES, '-', ''), '[+]', ', ' ), ', ' ) AS COLUMNS,
                    CASE WHEN UNIQUERULE = 'D' THEN 1 ELSE 0 END AS "UNIQUE"
                FROM SYSCAT.INDEXES
                WHERE INDSCHEMA = '%s' AND TABNAME = '%s'
                """,
                schema, tableName);
        List<IndexDetails> result = jdbc.query(sql, new MapSqlParameterSource(), INDEX_DETAILS_MAPPER);
        return QueryResult.of(sql, tableName, result);
    }

    @Override
    public int getRowCount(NamedParameterJdbcTemplate jdbc, String schema, String tableName, String whereClause) {
        String where = whereClause == null ? "" : whereClause;
        String sql = String.format("SELECT count_big(1) AS COUNT FROM %s.%s %s WITH UR", schema, tableName, where);
        Integer result = jdbc.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
        return result == null ? 0 : result;
    }

    @Override
    public QueryResult<RecordData> getRandomData(
            NamedParameterJdbcTemplate jdbc, String schema, String tableName, String idName, int limit) {
        String sql = String.format("SELECT * FROM %s.%s FETCH FIRST %d ROWS ONLY", schema, tableName, limit);
        List<RecordData> rows = jdbc.query(sql, new MapSqlParameterSource(), rowMapperWithId(idName));
        return QueryResult.of(sql, tableName, rows);
    }

    @Override
    public QueryResult<RecordData> getDataFromTable(
            NamedParameterJdbcTemplate jdbc, String schema, String tableName, int limit) {
        String sql = String.format(
                "SELECT * FROM %s.%s FETCH FIRST %d ROWS ONLY WITH UR", schema, tableName, limit);
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
        String sql = String.format("SELECT * FROM %s.%s WHERE %s IN %s", schema, tableName, idName, idList);
        List<RecordData> rows = jdbc.query(sql, new MapSqlParameterSource(), rowMapperWithId(idName));
        return QueryResult.of(sql, tableName, rows);
    }

    @Override
    public QueryResult<RecordData> findByColumns(
            NamedParameterJdbcTemplate jdbc, String schema, String tableName, RecordData record) {
        String sql = buildWhereByColumns(schema, tableName, record.columns());
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
                if (CHAR_FOR_BIT_DATA.equals(typeName) && StringUtils.isNotBlank(rs.getString(i))) {
                    try {
                        value = UUIDConverter.fromBytes(rs.getBytes(i)).toString();
                    } catch (Exception e) {
                        log.info("CHAR→UUID conversion failed for {}.{}", rs.getMetaData().getTableName(i), name);
                    }
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

    static String buildWhereByColumns(String schema, String tableName, Map<String, String> columns) {
        StringBuilder sql = new StringBuilder(String.format("SELECT * FROM %s.%s WHERE ", schema, tableName));
        boolean first = true;
        for (Map.Entry<String, String> entry : columns.entrySet()) {
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
        return sql.toString();
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
