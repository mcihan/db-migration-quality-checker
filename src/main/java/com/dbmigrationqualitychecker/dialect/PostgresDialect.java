package com.dbmigrationqualitychecker.dialect;

import com.dbmigrationqualitychecker.model.ColumnDetails;
import com.dbmigrationqualitychecker.model.IndexDetails;
import com.dbmigrationqualitychecker.model.QueryResult;
import com.dbmigrationqualitychecker.model.RecordData;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * PostgreSQL dialect.
 *
 * <p>Notes:
 * <ul>
 *   <li>Schema/table names are compared case-insensitively ({@code lower(…)})
 *       because PG folds unquoted identifiers to lowercase.</li>
 *   <li>PG native types (via {@code udt_name}) are normalised to canonical
 *       {@code INT / BIGINT / CHAR / BOOLEAN / TIMESTAMP / …} so existing
 *       {@code ColumnTypeCompatibility} rules work unchanged.</li>
 *   <li>Auto-increment detection uses the {@code nextval(…)} convention
 *       emitted by {@code SERIAL}/{@code IDENTITY} columns.</li>
 *   <li>{@code hexId} uses {@code encode(col, 'hex')} — PG's equivalent of
 *       MySQL's {@code hex(col)}.</li>
 * </ul>
 */
public class PostgresDialect implements DatabaseDialect {

    private static final String DRIVER = "org.postgresql.Driver";

    @Override
    public DatabaseType type() {
        return DatabaseType.POSTGRES;
    }

    @Override
    public String driverClassName() {
        return DRIVER;
    }

    @Override
    public QueryResult<String> getColumnNames(NamedParameterJdbcTemplate jdbc, String schema, String tableName) {
        String sql = String.format(
                """
                SELECT upper(column_name) AS "COLUMN_NAME"
                FROM information_schema.columns
                WHERE lower(table_schema) = lower('%s')
                  AND lower(table_name) = lower('%s')
                ORDER BY column_name ASC""",
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
                    upper(column_name) AS "COLUMN_NAME",
                    upper(CASE udt_name
                        WHEN 'int2' THEN 'SMALLINT'
                        WHEN 'int4' THEN 'INT'
                        WHEN 'int8' THEN 'BIGINT'
                        WHEN 'bpchar' THEN 'CHAR'
                        WHEN 'bool' THEN 'BOOLEAN'
                        WHEN 'timestamptz' THEN 'TIMESTAMP'
                        ELSE udt_name
                    END) AS "COLUMN_TYPE",
                    CASE WHEN is_nullable = 'YES' THEN 'YES' ELSE 'NO' END AS "NULLABLE",
                    upper(split_part(column_default, '(', 1)) AS "COLUMN_DEFAULT",
                    CASE WHEN column_default LIKE '%%nextval%%' THEN 'YES' ELSE 'NO' END AS "AUTO_INCREMENT"
                FROM information_schema.columns
                WHERE lower(table_schema) = lower('%s')
                  AND lower(table_name) = lower('%s')""",
                schema, tableName);
        return jdbc.query(sql, new MapSqlParameterSource(), COLUMN_DETAILS_MAPPER);
    }

    @Override
    public QueryResult<IndexDetails> getIndexDetails(NamedParameterJdbcTemplate jdbc, String schema, String tableName) {
        String sql = String.format(
                """
                SELECT
                    t.relname AS "TABLE_NAME",
                    i.relname AS "INDEX_NAME",
                    string_agg(a.attname, ',' ORDER BY array_position(ix.indkey, a.attnum)) AS "COLUMNS",
                    CASE WHEN ix.indisunique THEN '0' ELSE '1' END AS "UNIQUE"
                FROM pg_class t
                JOIN pg_index ix ON t.oid = ix.indrelid
                JOIN pg_class i ON i.oid = ix.indexrelid
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(ix.indkey)
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE lower(t.relname) = lower('%s')
                  AND lower(n.nspname) = lower('%s')
                GROUP BY t.relname, i.relname, ix.indisunique""",
                tableName, schema);
        List<IndexDetails> result = jdbc.query(sql, new MapSqlParameterSource(), INDEX_DETAILS_MAPPER);
        return QueryResult.of(sql, tableName, result);
    }

    @Override
    public int getRowCount(NamedParameterJdbcTemplate jdbc, String schema, String tableName, String whereClause) {
        String where = whereClause == null ? "" : whereClause;
        String sql = String.format("SELECT count(1) AS \"COUNT\" FROM %s.%s %s", schema, tableName, where);
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
                ? String.format(
                        "SELECT * FROM %s.%s WHERE lower(encode(%s, 'hex')) IN %s",
                        schema, tableName, idName, idList)
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
                cols.put(name.toUpperCase(), String.valueOf(rs.getString(i)));
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
