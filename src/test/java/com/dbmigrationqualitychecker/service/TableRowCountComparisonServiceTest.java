package com.dbmigrationqualitychecker.service;

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
class TableRowCountComparisonServiceTest {

    @Mock
    Db2Repository db2Repository;

    @Mock
    MySqlRepository mySqlRepository;

    @Mock
    TableProvider tableProvider;

    @InjectMocks
    TableRowCountComparisonService service;

    @BeforeEach
    @AfterEach
    void cleanReports() throws IOException {
        ReportTestSupport.cleanReportDir();
    }

    @Test
    void reportsSuccessWhenCountsMatch() throws IOException {
        Table t = Table.builder().tableName("T").sourceSchema("S").targetSchema("M").queryCondition("").build();
        when(tableProvider.getTables()).thenReturn(List.of(t));
        when(db2Repository.getRowCount(t)).thenReturn(42);
        when(mySqlRepository.getRowCount(t)).thenReturn(42);

        service.findDiff();

        String report = ReportTestSupport.readReport(ReportType.TABLE_ROW_COUNT_COMPARISON);
        assertThat(report).contains("[Test PASSED] : T", "Total:1", "Successful:1", "Failure:0");
    }

    @Test
    void reportsFailureWhenCountsDiffer() throws IOException {
        Table t = Table.builder().tableName("T2").sourceSchema("S").targetSchema("M").queryCondition("").build();
        when(tableProvider.getTables()).thenReturn(List.of(t));
        when(db2Repository.getRowCount(t)).thenReturn(100);
        when(mySqlRepository.getRowCount(t)).thenReturn(95);

        service.findDiff();

        String report = ReportTestSupport.readReport(ReportType.TABLE_ROW_COUNT_COMPARISON);
        assertThat(report).contains("[Test FAILED]", "DB2 has more data", "Diff:", "5");
        assertThat(report).contains("Failure:1");
    }

    @Test
    void mentionsMysqlHasMoreWhenMysqlCountLarger() throws IOException {
        Table t = Table.builder().tableName("T3").sourceSchema("S").targetSchema("M").queryCondition("").build();
        when(tableProvider.getTables()).thenReturn(List.of(t));
        when(db2Repository.getRowCount(t)).thenReturn(10);
        when(mySqlRepository.getRowCount(t)).thenReturn(20);

        service.findDiff();

        assertThat(ReportTestSupport.readReport(ReportType.TABLE_ROW_COUNT_COMPARISON))
                .contains("MySQL has more data");
    }

    @Test
    void countsFailureWhenRepositoryThrows() throws IOException {
        Table good = Table.builder().tableName("GOOD").sourceSchema("S").targetSchema("M").queryCondition("").build();
        Table bad = Table.builder().tableName("BAD").sourceSchema("S").targetSchema("M").queryCondition("").build();
        when(tableProvider.getTables()).thenReturn(List.of(good, bad));
        when(db2Repository.getRowCount(good)).thenReturn(1);
        when(mySqlRepository.getRowCount(good)).thenReturn(1);
        when(db2Repository.getRowCount(bad)).thenThrow(new RuntimeException("boom"));

        service.findDiff();

        assertThat(ReportTestSupport.readReport(ReportType.TABLE_ROW_COUNT_COMPARISON))
                .contains("Failure:1");
    }
}
