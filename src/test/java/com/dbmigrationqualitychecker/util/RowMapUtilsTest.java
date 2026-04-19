package com.dbmigrationqualitychecker.util;

import com.dbmigrationqualitychecker.repository.entity.ColumnDetails;
import com.dbmigrationqualitychecker.repository.entity.IndexDetails;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RowMapUtilsTest {

    @Test
    void columnDetailsMapsAllFields() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("COLUMN_NAME")).thenReturn("ID");
        when(rs.getString("COLUMN_TYPE")).thenReturn("INT");
        when(rs.getString("NULLABLE")).thenReturn("YES");
        when(rs.getString("COLUMN_DEFAULT")).thenReturn("'5'");
        when(rs.getString("AUTO_INCREMENT")).thenReturn("YES");

        ColumnDetails mapped = RowMapUtils.columnDetailsRowMapper.mapRow(rs, 1);

        assertThat(mapped.getColumnName()).isEqualTo("ID");
        assertThat(mapped.getColumnType()).isEqualTo("INT");
        assertThat(mapped.isNullable()).isTrue();
        assertThat(mapped.getColumnDefault()).isEqualTo("5");
        assertThat(mapped.isAutoIncrement()).isTrue();
    }

    @Test
    void columnDetailsTreatsStringNullAsJavaNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("COLUMN_NAME")).thenReturn("C");
        when(rs.getString("COLUMN_TYPE")).thenReturn("VARCHAR");
        when(rs.getString("NULLABLE")).thenReturn("NO");
        when(rs.getString("COLUMN_DEFAULT")).thenReturn("null");
        when(rs.getString("AUTO_INCREMENT")).thenReturn("NO");

        ColumnDetails mapped = RowMapUtils.columnDetailsRowMapper.mapRow(rs, 0);

        assertThat(mapped.getColumnDefault()).isNull();
        assertThat(mapped.isNullable()).isFalse();
        assertThat(mapped.isAutoIncrement()).isFalse();
    }

    @Test
    void columnDetailsTreatsYShortcutAsNullable() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("COLUMN_NAME")).thenReturn("C");
        when(rs.getString("COLUMN_TYPE")).thenReturn("VARCHAR");
        when(rs.getString("NULLABLE")).thenReturn("Y");
        when(rs.getString("COLUMN_DEFAULT")).thenReturn(null);
        when(rs.getString("AUTO_INCREMENT")).thenReturn("NO");

        ColumnDetails mapped = RowMapUtils.columnDetailsRowMapper.mapRow(rs, 0);
        assertThat(mapped.isNullable()).isTrue();
    }

    @Test
    void indexDetailsStripsSpacesFromColumnList() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("TABLE_NAME")).thenReturn("T");
        when(rs.getString("INDEX_NAME")).thenReturn("IDX");
        when(rs.getString("COLUMNS")).thenReturn("A, B, C");
        when(rs.getString("UNIQUE")).thenReturn("0");

        IndexDetails mapped = RowMapUtils.indexDetailsRowMapper.mapRow(rs, 0);

        assertThat(mapped.getColumnNames()).isEqualTo("A,B,C");
        assertThat(mapped.getIndexName()).isEqualTo("IDX");
        assertThat(mapped.getTableName()).isEqualTo("T");
        assertThat(mapped.getUnique()).isEqualTo("0");
    }

    @Test
    void indexDetailsHandlesNullColumns() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("TABLE_NAME")).thenReturn("T");
        when(rs.getString("INDEX_NAME")).thenReturn("IDX");
        when(rs.getString("COLUMNS")).thenReturn(null);
        when(rs.getString("UNIQUE")).thenReturn("1");

        IndexDetails mapped = RowMapUtils.indexDetailsRowMapper.mapRow(rs, 0);
        assertThat(mapped.getColumnNames()).isNull();
    }
}
