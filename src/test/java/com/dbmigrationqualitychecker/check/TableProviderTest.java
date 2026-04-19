package com.dbmigrationqualitychecker.check;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbmigrationqualitychecker.model.Table;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TableProviderTest {

    private final TableProvider provider = new TableProvider();

    @Test
    void returnsEmptyWhenCsvMissing() {
        assertThat(provider.readCsv("does/not/exist.csv")).isEmpty();
    }

    @Test
    void readsMinimumColumns(@TempDir Path tmp) throws IOException {
        Path csv = tmp.resolve("tables.csv");
        Files.writeString(csv, """
                SourceSchema,TargetSchema,TableName
                SOURCE_SCHEMA,TARGET_DB,ACCOUNT
                """);

        Optional<List<Table>> result = provider.readCsv(csv.toString());

        assertThat(result).isPresent();
        Table table = result.get().get(0);
        assertThat(table.sourceSchema()).isEqualTo("SOURCE_SCHEMA");
        assertThat(table.targetSchema()).isEqualTo("TARGET_DB");
        assertThat(table.tableName()).isEqualTo("ACCOUNT");
        assertThat(table.idName()).isNull();
        assertThat(table.queryCondition()).isEmpty();
        assertThat(table.hexId()).isFalse();
    }

    @Test
    void readsAllColumnsIncludingHexFlag(@TempDir Path tmp) throws IOException {
        Path csv = tmp.resolve("tables.csv");
        Files.writeString(csv, """
                SourceSchema,TargetSchema,TableName,PrimaryKeyName,QueryCondition,IsHexId
                SOURCE_SCHEMA,TARGET_DB,PAY_ACCOUNT,ID,WHERE X=1,true
                """);

        Optional<List<Table>> result = provider.readCsv(csv.toString());

        assertThat(result).isPresent();
        Table table = result.get().get(0);
        assertThat(table.idName()).isEqualTo("ID");
        assertThat(table.queryCondition()).isEqualTo("WHERE X=1");
        assertThat(table.hexId()).isTrue();
    }

    @Test
    void readsMultipleRows(@TempDir Path tmp) throws IOException {
        Path csv = tmp.resolve("tables.csv");
        Files.writeString(csv, """
                SourceSchema,TargetSchema,TableName,PrimaryKeyName
                SOURCE_SCHEMA,TARGET_DB,A,ID
                SOURCE_SCHEMA,TARGET_DB,B,
                """);

        Optional<List<Table>> result = provider.readCsv(csv.toString());

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(2);
        assertThat(result.get()).extracting(Table::tableName).containsExactly("A", "B");
    }
}
