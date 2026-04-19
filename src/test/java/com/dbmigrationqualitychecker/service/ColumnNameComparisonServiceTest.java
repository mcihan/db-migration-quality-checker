package com.dbmigrationqualitychecker.service;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.ReportTestSupport;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.Db2Repository;
import com.dbmigrationqualitychecker.repository.MySqlRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColumnNameComparisonServiceTest {

    @Mock
    Db2Repository db2Repository;

    @Mock
    MySqlRepository mySqlRepository;

    @Mock
    TableProvider tableProvider;

    @InjectMocks
    ColumnNameComparisonService service;

    private final Table table = Table.builder().tableName("USERS").sourceSchema("SRC_SCHEMA").targetSchema("TARGET_DB").build();

    @BeforeEach
    @AfterEach
    void cleanReports() throws IOException {
        ReportTestSupport.cleanReportDir();
    }

    @Test
    void returnsTrueWhenColumnNamesMatch() {
        when(db2Repository.getColumnNames(table))
                .thenReturn(QueryResult.<String>builder().result(List.of("A", "B", "C")).query("q1").build());
        when(mySqlRepository.getColumnNames(table))
                .thenReturn(QueryResult.<String>builder().result(List.of("A", "B", "C")).query("q2").build());

        assertThat(service.compare(table)).isTrue();
    }

    @Test
    void returnsFalseWhenColumnNamesDiffer() throws IOException {
        when(db2Repository.getColumnNames(table))
                .thenReturn(QueryResult.<String>builder().result(List.of("A", "B")).query("q1").build());
        when(mySqlRepository.getColumnNames(table))
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
        when(db2Repository.getColumnNames(t1)).thenReturn(QueryResult.<String>builder().result(List.of("X")).query("q").build());
        when(mySqlRepository.getColumnNames(t1)).thenReturn(QueryResult.<String>builder().result(List.of("X")).query("q").build());
        when(db2Repository.getColumnNames(t2)).thenReturn(QueryResult.<String>builder().result(List.of("X")).query("q").build());
        when(mySqlRepository.getColumnNames(t2)).thenReturn(QueryResult.<String>builder().result(List.of("Y")).query("q").build());

        service.findDiff();

        String report = ReportTestSupport.readReport(ReportType.COLUMN_NAME_COMPARISON);
        assertThat(report).contains("Total:2", "Successful:1", "Failure:1");
    }
}
