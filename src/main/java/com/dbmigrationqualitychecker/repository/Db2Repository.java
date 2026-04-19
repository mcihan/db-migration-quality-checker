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
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Slf4j
public class Db2Repository {

    private final NamedParameterJdbcTemplate db2JdbcTemplate;

    @Value("${config.random-data-count}")
    private int randomDataCount;

    private static RowMapper<RecordData> getRowsAsMap(String idName) {
        return (rs, rowNum) -> {
            HashMap<String, String> columnsWithValue = new HashMap<>();
            int columnCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rs.getMetaData().getColumnName(i);
                String columnTypeName = rs.getMetaData().getColumnTypeName(i);
                String columnValue = String.valueOf(rs.getString(i));
                if (columnTypeName.equals("CHAR FOR BIT DATA") && StringUtils.isNotBlank(rs.getString(i))) {
                    try {
                        columnValue = UUIDConverter.fromBytes(rs.getBytes(i)).toString();
                    } catch (Exception exception) {
                        log.info("CHAR conversion error for table:{}, column:{}",  rs.getMetaData().getTableName(i), columnName );
                    }
                }

                columnsWithValue.put(columnName.toUpperCase(), columnValue);

            }
            return RecordData.builder()
                    .id(rs.getString(idName))
                    .columns(columnsWithValue)
                    .build();
        };
    }

    private static RowMapper<RecordData> getAllRecordMap() {
        return (rs, rowNum) -> {
            int columnCount = rs.getMetaData().getColumnCount();
            HashMap<String, String> idMap = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rs.getMetaData().getColumnName(i);
                String columnValue = rs.getString(i);
                idMap.put(columnName, columnValue);
            }

            return RecordData.builder()
                    .columns(idMap)
                    .build();
        };
    }


    public QueryResult getDataFromTable(Table table) {
        String tableName = table.getTableName();
        String query = String.format("select * from %s.%s FETCH FIRST %d ROWS ONLY WITH UR", table.getSourceSchema(), tableName, randomDataCount);
        Instant start = Instant.now();
        List<RecordData> result = db2JdbcTemplate.query(query,
                new MapSqlParameterSource(),
                getAllRecordMap());
        String duration = ReportUtil.formatDuration(start, Instant.now());
        log.info("DB2 random data query ended for: {}, duration: {}", tableName, duration);
        return QueryResult.<RecordData>builder()
                .query(query)
                .result(result)
                .build();
    }


    public QueryResult getRandomData(Table table) {
        String tableName = table.getTableName();
        String query = String.format("select * from %s.%s FETCH FIRST %d ROWS ONLY", table.getSourceSchema(), tableName, randomDataCount);
        Instant start = Instant.now();
        List<RecordData> result = db2JdbcTemplate.query(query,
                new MapSqlParameterSource(),
                getRowsAsMap(table.getIdName()));
        String duration = ReportUtil.formatDuration(start, Instant.now());
        log.info("DB2 random data query ended for: {}, duration: {}", tableName, duration);

        return QueryResult.<RecordData>builder()
                .query(query)
                .result(result)
                .build();
    }

    public QueryResult getColumnNames(Table table) {
        String tableName = table.getTableName();
        String queryText = "SELECT COLNAME AS COLUMN_NAME FROM SYSCAT.COLUMNS WHERE TABSCHEMA = '%s' AND TABNAME ='%s' ORDER BY COLNAME ASC";
        String query = String.format(queryText, table.getSourceSchema(), tableName);

        List<String> columnNames = db2JdbcTemplate.query(query,
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
        String where = table.getQueryCondition();
        String query = String.format("select count_big(1) AS COUNT from %s.%s %s with ur", table.getSourceSchema(), tableName, where);
        Instant start = Instant.now();
//        log.info("DB2 row count query started for: {}", tableName);
        Integer result = db2JdbcTemplate.queryForObject(query, new MapSqlParameterSource(), Integer.class);
        String duration = ReportUtil.formatDuration(start, Instant.now());
        log.info("DB2 row count query ended for: {}, duration: {}", tableName, duration);

        return result;
    }

    public List<ColumnDetails> getColumnDetails(Table table) {

        String sql = """
                SELECT
                    COLNAME AS COLUMN_NAME,
                    TYPENAME AS COLUMN_TYPE,
                    CASE
                        WHEN NULLS = 'Y' THEN 'YES'
                        ELSE 'NO'
                    END AS NULLABLE,
                    DEFAULT AS COLUMN_DEFAULT,
                    CASE
                        WHEN IDENTITY = 'Y' THEN 'YES'
                        ELSE 'NO'
                    END AS AUTO_INCREMENT
                FROM SYSCAT.COLUMNS
                WHERE TABSCHEMA = '%s' 
                AND TABNAME = '%s'""";

        sql = String.format(sql, table.getSourceSchema(), table.getTableName());

        return db2JdbcTemplate.query(sql, new MapSqlParameterSource(), RowMapUtils.columnDetailsRowMapper);
    }

    public QueryResult<IndexDetails> getIndexDetails(Table table) {
        String sql = """
                SELECT TABNAME AS TABLE_NAME,
                    INDNAME AS INDEX_NAME, 
                    LTRIM( REGEXP_REPLACE( REPLACE(COLNAMES, '-', ''), '[+]', ', ' ), ', ' )  AS COLUMNS, 
                    CASE WHEN UNIQUERULE = 'D' THEN 1 ELSE 0 END AS UNIQUE
                FROM SYSCAT.INDEXES
                WHERE INDSCHEMA = '%s' AND TABNAME = '%s'
                """;
        sql = String.format(sql, table.getSourceSchema(), table.getTableName());

        List<IndexDetails> result = db2JdbcTemplate.query(sql, new MapSqlParameterSource(), RowMapUtils.indexDetailsRowMapper);

        return QueryResult.<IndexDetails>builder()
                .query(sql)
                .result(result)
                .build();
    }


}
