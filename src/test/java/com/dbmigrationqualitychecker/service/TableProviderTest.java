package com.dbmigrationqualitychecker.service;

import com.dbmigrationqualitychecker.report.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TableProviderTest {

    private final TableProvider tableProvider = new TableProvider();

    @Test
    void returnsEmptyWhenCsvMissing() {
        Optional<List<Table>> result = tableProvider.readCsv("does/not/exist.csv");
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyListWhenGetTablesCantFindCsv() {
        List<Table> tables = tableProvider.getTables();
        assertThat(tables).isNotNull();
    }

    @Test
    void readsMinimumColumns(@TempDir Path tmp) throws IOException {
        Path csv = tmp.resolve("tables.csv");
        Files.writeString(csv, """
                SourceSchema,TargetSchema,TableName
                SRC_SCHEMA,TARGET_DB,ACCOUNT
                """);

        Optional<List<Table>> result = tableProvider.readCsv(csv.toString());

        assertThat(result).isPresent();
        Table table = result.get().get(0);
        assertThat(table.getSourceSchema()).isEqualTo("SRC_SCHEMA");
        assertThat(table.getTargetSchema()).isEqualTo("TARGET_DB");
        assertThat(table.getTableName()).isEqualTo("ACCOUNT");
        assertThat(table.getIdName()).isNull();
        assertThat(table.getQueryCondition()).isEmpty();
        assertThat(table.isHexId()).isFalse();
    }

    @Test
    void readsAllColumnsIncludingHexFlag(@TempDir Path tmp) throws IOException {
        Path csv = tmp.resolve("tables.csv");
        Files.writeString(csv, """
                SourceSchema,TargetSchema,TableName,PrimaryKeyName,QueryCondition,IsHexId
                SRC_SCHEMA,TARGET_DB,USERS,ID,WHERE X=1,true
                """);

        Optional<List<Table>> result = tableProvider.readCsv(csv.toString());

        assertThat(result).isPresent();
        Table table = result.get().get(0);
        assertThat(table.getIdName()).isEqualTo("ID");
        assertThat(table.getQueryCondition()).isEqualTo("WHERE X=1");
        assertThat(table.isHexId()).isTrue();
    }

    @Test
    void readsMultipleRows(@TempDir Path tmp) throws IOException {
        Path csv = tmp.resolve("tables.csv");
        Files.writeString(csv, """
                SourceSchema,TargetSchema,TableName,PrimaryKeyName
                SRC_SCHEMA,TARGET_DB,A,ID
                SRC_SCHEMA,TARGET_DB,B,
                """);

        Optional<List<Table>> result = tableProvider.readCsv(csv.toString());

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(2);
        assertThat(result.get()).extracting(Table::getTableName).containsExactly("A", "B");
    }
}
