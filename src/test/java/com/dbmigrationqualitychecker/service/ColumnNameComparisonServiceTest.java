package com.dbmigrationqualitychecker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.ReportTestSupport;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ColumnNameComparisonServiceTest {

    @Mock
    DatabaseRepository sourceRepository;

    @Mock
    DatabaseRepository targetRepository;

    @Mock
    TableProvider tableProvider;

    ColumnNameComparisonService service;

    private final Table table = Table.builder()
            .tableName("PAY_ACCOUNT")
            .sourceSchema("SOURCE_SCHEMA")
            .targetSchema("TARGET_DB")
            .build();

    @BeforeEach
    void init() {
        service = new ColumnNameComparisonService(sourceRepository, targetRepository, tableProvider);
    }

    @BeforeEach
    @AfterEach
    void cleanReports() throws IOException {
        ReportTestSupport.cleanReportDir();
    }

    @Test
    void returnsTrueWhenColumnNamesMatch() {
        when(sourceRepository.getColumnNames(table))
                .thenReturn(QueryResult.<String>builder().result(List.of("A", "B", "C")).query("q1").build());
        when(targetRepository.getColumnNames(table))
                .thenReturn(QueryResult.<String>builder().result(List.of("A", "B", "C")).query("q2").build());

        assertThat(service.compare(table)).isTrue();
    }

    @Test
    void returnsFalseWhenColumnNamesDiffer() throws IOException {
        when(sourceRepository.getColumnNames(table))
                .thenReturn(QueryResult.<String>builder().result(List.of("A", "B")).query("q1").build());
        when(targetRepository.getColumnNames(table))
                .thenReturn(QueryResult.<String>builder().result(List.of("A", "C")).query("q2").build());

        assertThat(service.compare(table)).isFalse();

        String report = ReportTestSupport.readReport(ReportType.COLUMN_NAME_COMPARISON);
        assertThat(report).contains("[Test FAILED]", "Column names do not match");
    }

    @Test
    void findDiffIteratesAllTablesAndWritesSummary() throws IOException {
        Table t1 = Table.builder().tableName("T1").sourceSchema("S").targetSchema("M").build();
        Table t2 = Table.builder().tableName("T2").sourceSchema("S").targetSchema("M").build();
        when(tableProvider.getTables()).thenReturn(List.of(t1, t2));
        when(sourceRepository.getColumnNames(t1))
                .thenReturn(QueryResult.<String>builder().result(List.of("X")).query("q").build());
        when(targetRepository.getColumnNames(t1))
                .thenReturn(QueryResult.<String>builder().result(List.of("X")).query("q").build());
        when(sourceRepository.getColumnNames(t2))
                .thenReturn(QueryResult.<String>builder().result(List.of("X")).query("q").build());
        when(targetRepository.getColumnNames(t2))
                .thenReturn(QueryResult.<String>builder().result(List.of("Y")).query("q").build());

        service.findDiff();

        String report = ReportTestSupport.readReport(ReportType.COLUMN_NAME_COMPARISON);
        assertThat(report).contains("Total:2", "Successful:1", "Failure:1");
    }
}
