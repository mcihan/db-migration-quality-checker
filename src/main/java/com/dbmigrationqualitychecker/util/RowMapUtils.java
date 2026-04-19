package com.dbmigrationqualitychecker.util;

import com.dbmigrationqualitychecker.repository.entity.ColumnDetails;
import com.dbmigrationqualitychecker.repository.entity.IndexDetails;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class RowMapUtils {


    public static final RowMapper<ColumnDetails> columnDetailsRowMapper = (rs, rowNum) -> ColumnDetails.builder()
            .columnName(rs.getString("COLUMN_NAME"))
            .columnType(rs.getString("COLUMN_TYPE"))
            .columnDefault(getColumnDefault(rs))
            .isNullable(StringUtils.equalsAny(rs.getString("NULLABLE"), "YES", "Y"))
            .isAutoIncrement("YES".equals(rs.getString("AUTO_INCREMENT")))
            .build();

    public static final RowMapper<IndexDetails> indexDetailsRowMapper = (rs, rowNum) -> IndexDetails.builder()
            .tableName(rs.getString("TABLE_NAME"))
            .indexName(rs.getString("INDEX_NAME"))
            .columnNames(getColumns(rs))
            .unique(rs.getString("UNIQUE"))
            .build();

    private static String getColumns(ResultSet rs) throws SQLException {
        String columns = rs.getString("COLUMNS");
        if (Objects.nonNull(columns)) {
            return columns.replace(" ", "");
        }
        return columns;
    }


    private static String getColumnDefault(ResultSet rs) throws SQLException {
        String columnDefault = rs.getString("COLUMN_DEFAULT");
        if (StringUtils.isNotBlank(columnDefault)) {
            columnDefault = columnDefault.replace("'", "");
        }
        return StringUtils.equalsAnyIgnoreCase(columnDefault, "null") ? null : columnDefault;
    }

}
