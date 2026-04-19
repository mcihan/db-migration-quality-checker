package com.dbmigrationqualitychecker.service;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.ReportTestSupport;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.Db2Repository;
import com.dbmigrationqualitychecker.repository.MySqlRepository;
import com.dbmigrationqualitychecker.repository.entity.IndexDetails;
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
class IndexComparisonServiceTest {

    @Mock
    Db2Repository db2Repository;

    @Mock
    MySqlRepository mySqlRepository;

    @Mock
    TableProvider tableProvider;

    @InjectMocks
    IndexComparisonService service;

    private final Table table = Table.builder().tableName("T").sourceSchema("S").targetSchema("M").build();

    @BeforeEach
    @AfterEach
    void cleanReports() throws IOException {
        ReportTestSupport.cleanReportDir();
    }

    private IndexDetails idx(String name, String cols, String unique) {
        return IndexDetails.builder().tableName("T").indexName(name).columnNames(cols).unique(unique).build();
    }

    @Test
    void passesWhenIndexesMatch() throws IOException {
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(db2Repository.getIndexDetails(table)).thenReturn(QueryResult.<IndexDetails>builder()
                .query("q").result(List.of(idx("PK", "ID", "0"))).build());
        when(mySqlRepository.getIndexDetails(table)).thenReturn(QueryResult.<IndexDetails>builder()
                .query("q").result(List.of(idx("PRIMARY", "ID", "0"))).build());

        service.findDiff();
        assertThat(ReportTestSupport.readReport(ReportType.INDEX_COMPARISON))
                .contains("Total:1", "Successful:1", "Failure:0");
    }

    @Test
    void reportsMissingIndexOnMysql() throws IOException {
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(db2Repository.getIndexDetails(table)).thenReturn(QueryResult.<IndexDetails>builder()
                .query("q").result(List.of(idx("IDX_A", "A,B", "0"))).build());
        when(mySqlRepository.getIndexDetails(table)).thenReturn(QueryResult.<IndexDetails>builder()
                .query("q").result(List.of()).build());

        service.findDiff();
        String report = ReportTestSupport.readReport(ReportType.INDEX_COMPARISON);
        assertThat(report).contains("There is no index on mysql", "[Test FAILED]");
    }

    @Test
    void reportsExtraIndexOnMysqlAlongsideMissingFailure() throws IOException {
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(db2Repository.getIndexDetails(table)).thenReturn(QueryResult.<IndexDetails>builder()
                .query("q").result(List.of(idx("IDX_MISSING", "CODE", "0"))).build());
        when(mySqlRepository.getIndexDetails(table)).thenReturn(QueryResult.<IndexDetails>builder()
                .query("q").result(List.of(idx("IDX_EXTRA", "NAME", "1"))).build());

        service.findDiff();
        assertThat(ReportTestSupport.readReport(ReportType.INDEX_COMPARISON))
                .contains("Extra indexes on mysql", "IDX_EXTRA", "There is no index on mysql");
    }

    @Test
    void warnsWhenDb2HasNoIndexes() throws IOException {
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(db2Repository.getIndexDetails(table)).thenReturn(QueryResult.<IndexDetails>builder()
                .query("q").result(List.of()).build());
        when(mySqlRepository.getIndexDetails(table)).thenReturn(QueryResult.<IndexDetails>builder()
                .query("q").result(List.of()).build());

        service.findDiff();
        assertThat(ReportTestSupport.readReport(ReportType.INDEX_COMPARISON))
                .contains("Successful:1");
    }
}
