package com.dbmigrationqualitychecker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.ReportTestSupport;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import com.dbmigrationqualitychecker.repository.entity.IndexDetails;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexComparisonServiceTest {

    @Mock
    DatabaseRepository sourceRepository;

    @Mock
    DatabaseRepository targetRepository;

    @Mock
    TableProvider tableProvider;

    IndexComparisonService service;

    private final Table table =
            Table.builder().tableName("T").sourceSchema("S").targetSchema("M").build();

    @BeforeEach
    void init() {
        service = new IndexComparisonService(sourceRepository, targetRepository, tableProvider);
    }

    @BeforeEach
    @AfterEach
    void cleanReports() throws IOException {
        ReportTestSupport.cleanReportDir();
    }

    private IndexDetails idx(String name, String cols, String unique) {
        return IndexDetails.builder()
                .tableName("T")
                .indexName(name)
                .columnNames(cols)
                .unique(unique)
                .build();
    }

    @Test
    void passesWhenIndexesMatch() throws IOException {
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(sourceRepository.getIndexDetails(table))
                .thenReturn(QueryResult.<IndexDetails>builder()
                        .query("q")
                        .result(List.of(idx("PK", "ID", "0")))
                        .build());
        when(targetRepository.getIndexDetails(table))
                .thenReturn(QueryResult.<IndexDetails>builder()
                        .query("q")
                        .result(List.of(idx("PRIMARY", "ID", "0")))
                        .build());

        service.findDiff();
        assertThat(ReportTestSupport.readReport(ReportType.INDEX_COMPARISON))
                .contains("Total:1", "Successful:1", "Failure:0");
    }

    @Test
    void reportsMissingIndexOnTarget() throws IOException {
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(sourceRepository.getIndexDetails(table))
                .thenReturn(QueryResult.<IndexDetails>builder()
                        .query("q")
                        .result(List.of(idx("IDX_A", "A,B", "0")))
                        .build());
        when(targetRepository.getIndexDetails(table))
                .thenReturn(QueryResult.<IndexDetails>builder()
                        .query("q")
                        .result(List.of())
                        .build());

        service.findDiff();
        String report = ReportTestSupport.readReport(ReportType.INDEX_COMPARISON);
        assertThat(report).contains("There is no index on target", "[Test FAILED]");
    }

    @Test
    void reportsExtraIndexOnTargetAlongsideMissingFailure() throws IOException {
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(sourceRepository.getIndexDetails(table))
                .thenReturn(QueryResult.<IndexDetails>builder()
                        .query("q")
                        .result(List.of(idx("IDX_MISSING", "CODE", "0")))
                        .build());
        when(targetRepository.getIndexDetails(table))
                .thenReturn(QueryResult.<IndexDetails>builder()
                        .query("q")
                        .result(List.of(idx("IDX_EXTRA", "NAME", "1")))
                        .build());

        service.findDiff();
        assertThat(ReportTestSupport.readReport(ReportType.INDEX_COMPARISON))
                .contains("Extra indexes on target", "IDX_EXTRA", "There is no index on target");
    }

    @Test
    void warnsWhenSourceHasNoIndexes() throws IOException {
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(sourceRepository.getIndexDetails(table))
                .thenReturn(QueryResult.<IndexDetails>builder()
                        .query("q")
                        .result(List.of())
                        .build());
        when(targetRepository.getIndexDetails(table))
                .thenReturn(QueryResult.<IndexDetails>builder()
                        .query("q")
                        .result(List.of())
                        .build());

        service.findDiff();
        assertThat(ReportTestSupport.readReport(ReportType.INDEX_COMPARISON)).contains("Successful:1");
    }
}
