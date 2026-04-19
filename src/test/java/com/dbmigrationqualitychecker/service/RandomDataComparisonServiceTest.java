package com.dbmigrationqualitychecker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.ReportTestSupport;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import com.dbmigrationqualitychecker.repository.entity.RecordData;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RandomDataComparisonServiceTest {

    @Mock
    DatabaseRepository sourceRepository;

    @Mock
    DatabaseRepository targetRepository;

    @Mock
    TableProvider tableProvider;

    RandomDataComparisonService service;

    @BeforeEach
    void init() {
        service = new RandomDataComparisonService(sourceRepository, targetRepository, tableProvider);
    }

    @BeforeEach
    @AfterEach
    void cleanReports() throws IOException {
        ReportTestSupport.cleanReportDir();
    }

    private RecordData rec(String id, String name) {
        HashMap<String, String> cols = new HashMap<>();
        cols.put("ID", id);
        cols.put("NAME", name);
        return RecordData.builder().id(id).columns(cols).build();
    }

    @Test
    void compareValuesReturnsFalseWhenBothNull() {
        assertThat(service.compareValues(null, null)).isFalse();
    }

    @Test
    void compareValuesSwallowsNullSourceWithoutReportingMismatch() {
        assertThat(service.compareValues(null, "x")).isFalse();
    }

    @Test
    void compareValuesReturnsFalseForMatchingStrings() {
        assertThat(service.compareValues("abc", "abc")).isFalse();
    }

    @Test
    void compareValuesReturnsTrueForDifferentStrings() {
        assertThat(service.compareValues("abc", "def")).isTrue();
    }

    @Test
    void compareValuesTreatsTrailingZeroPaddingAsEqual() {
        assertThat(service.compareValues("2024-01-01 10:00:00", "2024-01-01 10:00:00.000000"))
                .isFalse();
    }

    @Test
    void compareValuesNonZeroPaddingTruncatedBeforeComparison() {
        assertThat(service.compareValues("abc", "abcdef")).isFalse();
    }

    @Test
    void idBasedCheckReportsSuccessWhenMatching() throws IOException {
        Table table = Table.builder()
                .tableName("T")
                .sourceSchema("S")
                .targetSchema("M")
                .idName("ID")
                .queryCondition("")
                .build();
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(sourceRepository.getRandomData(table))
                .thenReturn(QueryResult.<RecordData>builder()
                        .result(List.of(rec("1", "A")))
                        .query("q")
                        .build());
        when(targetRepository.findAllByIds(any(), any()))
                .thenReturn(QueryResult.<RecordData>builder()
                        .result(List.of(rec("1", "A")))
                        .query("q")
                        .build());

        service.findDiff();

        String report = ReportTestSupport.readReport(ReportType.RANDOM_DATA_COMPARISON);
        assertThat(report).contains("[Test PASSED]", "Successful:1", "Failure:0");
    }

    @Test
    void idBasedCheckReportsFailureWhenTargetMissingRecord() throws IOException {
        Table table = Table.builder()
                .tableName("T")
                .sourceSchema("S")
                .targetSchema("M")
                .idName("ID")
                .queryCondition("")
                .build();
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(sourceRepository.getRandomData(table))
                .thenReturn(QueryResult.<RecordData>builder()
                        .result(List.of(rec("1", "A")))
                        .query("q")
                        .build());
        when(targetRepository.findAllByIds(any(), any()))
                .thenReturn(QueryResult.<RecordData>builder()
                        .result(List.of())
                        .query("q")
                        .build());

        service.findDiff();

        String report = ReportTestSupport.readReport(ReportType.RANDOM_DATA_COMPARISON);
        assertThat(report).contains("[Test FAILED]", "There is no corresponded data on target");
    }

    @Test
    void idBasedCheckReportsFailureWhenValueDiffers() throws IOException {
        Table table = Table.builder()
                .tableName("T")
                .sourceSchema("S")
                .targetSchema("M")
                .idName("ID")
                .queryCondition("")
                .build();
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(sourceRepository.getRandomData(table))
                .thenReturn(QueryResult.<RecordData>builder()
                        .result(List.of(rec("1", "A")))
                        .query("q")
                        .build());
        when(targetRepository.findAllByIds(any(), any()))
                .thenReturn(QueryResult.<RecordData>builder()
                        .result(List.of(rec("1", "B")))
                        .query("q")
                        .build());

        service.findDiff();

        String report = ReportTestSupport.readReport(ReportType.RANDOM_DATA_COMPARISON);
        assertThat(report).contains("[Test FAILED]", "Failed column: NAME");
    }

    @Test
    void idBasedCheckPassesWhenSourceHasNoRecords() throws IOException {
        Table table = Table.builder()
                .tableName("EMPTY")
                .sourceSchema("S")
                .targetSchema("M")
                .idName("ID")
                .queryCondition("")
                .build();
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(sourceRepository.getRandomData(table))
                .thenReturn(QueryResult.<RecordData>builder()
                        .result(List.of())
                        .query("q")
                        .build());

        service.findDiff();

        assertThat(ReportTestSupport.readReport(ReportType.RANDOM_DATA_COMPARISON))
                .contains("There is no data on both of source and target");
    }

    @Test
    void fullColumnCheckFailsWhenNoMatchingRowFound() throws IOException {
        Table table = Table.builder()
                .tableName("T")
                .sourceSchema("S")
                .targetSchema("M")
                .queryCondition("")
                .build();
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(sourceRepository.getDataFromTable(table))
                .thenReturn(QueryResult.<RecordData>builder()
                        .result(List.of(rec("1", "A")))
                        .query("q")
                        .build());
        when(targetRepository.getDataReportFromTable(any(), any()))
                .thenReturn(QueryResult.<RecordData>builder()
                        .result(List.of())
                        .query("q")
                        .build());

        service.findDiff();

        assertThat(ReportTestSupport.readReport(ReportType.RANDOM_DATA_COMPARISON))
                .contains("[Test FAILED]", "couldn't found on target DB");
    }

    @Test
    void fullColumnCheckFailsWhenMultipleMatches() throws IOException {
        Table table = Table.builder()
                .tableName("T")
                .sourceSchema("S")
                .targetSchema("M")
                .queryCondition("")
                .build();
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(sourceRepository.getDataFromTable(table))
                .thenReturn(QueryResult.<RecordData>builder()
                        .result(List.of(rec("1", "A")))
                        .query("q")
                        .build());
        when(targetRepository.getDataReportFromTable(any(), any()))
                .thenReturn(QueryResult.<RecordData>builder()
                        .result(List.of(rec("1", "A"), rec("1", "A")))
                        .query("q")
                        .build());

        service.findDiff();

        assertThat(ReportTestSupport.readReport(ReportType.RANDOM_DATA_COMPARISON))
                .contains("Multiple records founded");
    }
}
