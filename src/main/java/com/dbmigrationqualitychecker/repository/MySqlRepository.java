package com.dbmigrationqualitychecker.repository;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.entity.ColumnDetails;
import com.dbmigrationqualitychecker.repository.entity.IndexDetails;
import com.dbmigrationqualitychecker.repository.entity.RecordData;
import com.dbmigrationqualitychecker.util.ReportUtil;
import com.dbmigrationqualitychecker.util.RowMapUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class MySqlRepository {

    private final NamedParameterJdbcTemplate mySqlJdbcTemplate;

    public QueryResult getDataReportFromTable(Table table, RecordData record) {
        String tableName = table.getTableName();
        String schema = table.getTargetSchema();
        String query = String.format("SELECT * FROM %s.%s WHERE ", schema, tableName);

        StringBuilder sql = new StringBuilder(query);

        boolean firstCondition = true;
        Map<String, String> condition = record.getColumns();
        for (Map.Entry<String, String> entry : condition.entrySet()) {
            if (!firstCondition) {
                sql.append(" AND ");
            }
            firstCondition = false;

            String column = entry.getKey();
            String value = entry.getValue();
            sql.append(column);
            if (Objects.isNull(value)) {
                sql.append(" IS NULL");
            } else {
                sql.append(" = ").append(String.format("'%s'", value));
            }

            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(column, value);
        }
        List<RecordData> result = mySqlJdbcTemplate.query(sql.toString(),
                new MapSqlParameterSource(),
                getAllRecordMap());

        return QueryResult.<RecordData>builder()
                .result(result)
                .query(sql.toString())
                .build();
    }

    public QueryResult findAllByIds(Table table, List<String> ids) {
        String tableName = table.getTableName();
        String idName = table.getIdName();
        String schema = table.getTargetSchema();
        String idList = ids.stream()
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(",", "(", ")"));
        String query = String.format("SELECT * FROM %s.%s WHERE %s IN %s", schema, tableName, idName, idList);
        if (table.isHexId()) {
            query = String.format("SELECT * FROM %s.%s WHERE lower(hex(%s)) IN %s", schema, tableName, idName, idList);
        }
        List<RecordData> records = mySqlJdbcTemplate.query(query,
                new MapSqlParameterSource(),
                getRowsAsMap(idName));

        return QueryResult.<RecordData>builder()
                .tableName(tableName)
                .result(records)
                .query(query)
                .build();
    }

    public QueryResult getColumnNames(Table table) {
        String tableName = table.getTableName();
        String query = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s' ORDER BY COLUMN_NAME ASC";
        query = String.format(query, table.getTargetSchema(), tableName);

        List<String> columnNames = mySqlJdbcTemplate.query(query,
                new MapSqlParameterSource(),
                (rs, rowNum) -> rs.getString("COLUMN_NAME"));

        Collections.sort(columnNames);

        return QueryResult.<String>builder()
                .result(columnNames)
                .query(query)
                .tableName(tableName)
                .build();
    }

    public int getRowCount(Table table) {
        String tableName = table.getTableName();
        String schema = table.getTargetSchema();
        String where = table.getQueryCondition();
        String query = String.format("select count(1) AS COUNT from %s.%s %s", schema, tableName, where);
        Instant start = Instant.now();
        Integer result = mySqlJdbcTemplate.queryForObject(query, new MapSqlParameterSource(), Integer.class);
        String duration = ReportUtil.formatDuration(start, Instant.now());
        log.info("MYSQL row count query ended for: {}, duration: {}", tableName, duration);
        return result;
    }

    public List<ColumnDetails> getColumnDetails(Table table) {
        String sql = """
                SELECT 
                    UPPER(COLUMN_NAME) AS COLUMN_NAME, 
                    UPPER(SUBSTRING_INDEX(COLUMN_TYPE, '(', 1)) AS COLUMN_TYPE, 
                    IS_NULLABLE AS NULLABLE, 
                    UPPER(SUBSTRING_INDEX(COLUMN_DEFAULT, '(', 1)) AS COLUMN_DEFAULT, 
                    CASE WHEN EXTRA LIKE '%auto_increment%' THEN 'YES' ELSE 'NO' END AS AUTO_INCREMENT
                FROM INFORMATION_SCHEMA.COLUMNS 
                WHERE TABLE_SCHEMA = '%s' AND 
                TABLE_NAME = '%s' """;

        String sql2 = """
                SELECT 
                    UPPER(COLUMN_NAME) AS COLUMN_NAME, 
                    UPPER(SUBSTRING_INDEX(COLUMN_TYPE, '(', 1)) AS COLUMN_TYPE, 
                    IS_NULLABLE AS NULLABLE, 
                    UPPER(SUBSTRING_INDEX(COLUMN_DEFAULT, '(', 1)) AS COLUMN_DEFAULT, 
                    CASE WHEN EXTRA LIKE '%auto_increment%' THEN 'YES' ELSE 'NO' END AS AUTO_INCREMENT
                FROM INFORMATION_SCHEMA.COLUMNS """ +
                String.format(" WHERE TABLE_SCHEMA = '%s'", table.getTargetSchema()) +
                String.format(" AND TABLE_NAME = '%s'", table.getTableName());

        //  sql = String.format(sql, table.getTargetSchema(), table.getTableName());
        return mySqlJdbcTemplate.query(sql2, new MapSqlParameterSource(), RowMapUtils.columnDetailsRowMapper);
    }

    public QueryResult<IndexDetails> getIndexDetails(Table table) {
        String sql = """
                SELECT 
                    TABLE_NAME, 
                    INDEX_NAME, 
                    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS COLUMNS, 
                    NON_UNIQUE as 'UNIQUE'
                FROM INFORMATION_SCHEMA.STATISTICS 
                WHERE TABLE_NAME = '%s' AND TABLE_SCHEMA = '%s'
                GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE
                """;

        sql = String.format(sql, table.getTableName(), table.getTargetSchema());
        List<IndexDetails> result = mySqlJdbcTemplate.query(sql, new MapSqlParameterSource(), RowMapUtils.indexDetailsRowMapper);
        return QueryResult.<IndexDetails>builder()
                .query(sql)
                .result(result)
                .build();
    }

    private RowMapper<RecordData> getRowsAsMap(String idName) {
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
                } catch (Exception e) {
                }
                columnsWithValue.put(columnName.toUpperCase(), columnValue);
            }
            return RecordData.builder()
                    .id(rs.getString(idName))
                    .columns(columnsWithValue)
                    .build();
        };
    }

    private RowMapper<RecordData> getAllRecordMap() {
        return (rs, rowNum) -> {
            HashMap<String, String> idMap = new HashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                String columnName = rs.getMetaData().getColumnName(i);
                String columnValue = rs.getString(i);
                idMap.put(columnName, columnValue);
            }
            return RecordData.builder()
                    .columns(idMap)
                    .build();
        };

    }


}
