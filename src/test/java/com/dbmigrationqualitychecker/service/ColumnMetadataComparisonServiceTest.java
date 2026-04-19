package com.dbmigrationqualitychecker.service;

import com.dbmigrationqualitychecker.report.ReportTestSupport;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.Db2Repository;
import com.dbmigrationqualitychecker.repository.MySqlRepository;
import com.dbmigrationqualitychecker.repository.entity.ColumnDetails;
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
class ColumnMetadataComparisonServiceTest {

    @Mock
    Db2Repository db2Repository;

    @Mock
    MySqlRepository mySqlRepository;

    @Mock
    TableProvider tableProvider;

    @InjectMocks
    ColumnMetadataComparisonService service;

    private final Table table = Table.builder().tableName("T").sourceSchema("S").targetSchema("M").build();

    @BeforeEach
    @AfterEach
    void cleanReports() throws IOException {
        ReportTestSupport.cleanReportDir();
    }

    private ColumnDetails col(String name, String type, String def, boolean nullable, boolean auto) {
        return ColumnDetails.builder()
                .columnName(name).columnType(type).columnDefault(def)
                .isNullable(nullable).isAutoIncrement(auto).build();
    }

    @Test
    void matchesIdenticalColumns() {
        ColumnDetails c = col("ID", "INT", null, false, true);
        when(db2Repository.getColumnDetails(table)).thenReturn(List.of(c));
        when(mySqlRepository.getColumnDetails(table)).thenReturn(List.of(c));

        assertThat(service.shouldDDLMatch(table)).isTrue();
    }

    @Test
    void matchesEquivalentIntTypes() {
        when(db2Repository.getColumnDetails(table))
                .thenReturn(List.of(col("ID", "INTEGER", null, false, false)));
        when(mySqlRepository.getColumnDetails(table))
                .thenReturn(List.of(col("ID", "BIGINT", null, false, false)));

        assertThat(service.shouldDDLMatch(table)).isTrue();
    }

    @Test
    void matchesCharacterAgainstBinaryType() {
        when(db2Repository.getColumnDetails(table))
                .thenReturn(List.of(col("UID", "CHARACTER", null, true, false)));
        when(mySqlRepository.getColumnDetails(table))
                .thenReturn(List.of(col("UID", "VARBINARY", null, true, false)));

        assertThat(service.shouldDDLMatch(table)).isTrue();
    }

    @Test
    void matchesCharacterAgainstChar() {
        when(db2Repository.getColumnDetails(table))
                .thenReturn(List.of(col("C", "CHARACTER", null, true, false)));
        when(mySqlRepository.getColumnDetails(table))
                .thenReturn(List.of(col("C", "CHAR", null, true, false)));

        assertThat(service.shouldDDLMatch(table)).isTrue();
    }

    @Test
    void matchesTimestampWithPrecisionDifferences() {
        when(db2Repository.getColumnDetails(table))
                .thenReturn(List.of(col("TS", "TIMESTAMP", null, true, false)));
        when(mySqlRepository.getColumnDetails(table))
                .thenReturn(List.of(col("TS", "TIMESTAMP(6)", null, true, false)));

        assertThat(service.shouldDDLMatch(table)).isTrue();
    }

    @Test
    void returnsFalseWhenTypeMismatch() throws IOException {
        when(db2Repository.getColumnDetails(table))
                .thenReturn(List.of(col("C", "VARCHAR", null, true, false)));
        when(mySqlRepository.getColumnDetails(table))
                .thenReturn(List.of(col("C", "DECIMAL", null, true, false)));

        assertThat(service.shouldDDLMatch(table)).isFalse();
        assertThat(ReportTestSupport.readReport(ReportType.COLUMN_METADATA_COMPARISON))
                .contains("Column Type Mismatch");
    }

    @Test
    void returnsFalseWhenNullabilityDiffers() throws IOException {
        when(db2Repository.getColumnDetails(table))
                .thenReturn(List.of(col("C", "INT", null, true, false)));
        when(mySqlRepository.getColumnDetails(table))
                .thenReturn(List.of(col("C", "INT", null, false, false)));

        assertThat(service.shouldDDLMatch(table)).isFalse();
        assertThat(ReportTestSupport.readReport(ReportType.COLUMN_METADATA_COMPARISON))
                .contains("Nullability Mismatch");
    }

    @Test
    void returnsFalseWhenAutoIncrementDiffers() throws IOException {
        when(db2Repository.getColumnDetails(table))
                .thenReturn(List.of(col("ID", "INT", null, false, true)));
        when(mySqlRepository.getColumnDetails(table))
                .thenReturn(List.of(col("ID", "INT", null, false, false)));

        assertThat(service.shouldDDLMatch(table)).isFalse();
        assertThat(ReportTestSupport.readReport(ReportType.COLUMN_METADATA_COMPARISON))
                .contains("Auto Increment Mismatch");
    }

    @Test
    void returnsFalseWhenDefaultValueDiffers() throws IOException {
        when(db2Repository.getColumnDetails(table))
                .thenReturn(List.of(col("ST", "VARCHAR", "X", false, false)));
        when(mySqlRepository.getColumnDetails(table))
                .thenReturn(List.of(col("ST", "VARCHAR", "Y", false, false)));

        assertThat(service.shouldDDLMatch(table)).isFalse();
        assertThat(ReportTestSupport.readReport(ReportType.COLUMN_METADATA_COMPARISON))
                .contains("Default Value Mismatch");
    }

    @Test
    void matchesDefaultQuoteAndHexEquivalents() {
        when(db2Repository.getColumnDetails(table))
                .thenReturn(List.of(col("FLAG", "CHARACTER", "'0'", true, false)));
        when(mySqlRepository.getColumnDetails(table))
                .thenReturn(List.of(col("FLAG", "CHAR", "0X30", true, false)));

        assertThat(service.shouldDDLMatch(table)).isTrue();
    }

    @Test
    void writesSuccessAndReturnsFalseWhenDb2HasNoColumns() throws IOException {
        when(db2Repository.getColumnDetails(table)).thenReturn(List.of());
        when(mySqlRepository.getColumnDetails(table)).thenReturn(List.of());

        assertThat(service.shouldDDLMatch(table)).isFalse();
        assertThat(ReportTestSupport.readReport(ReportType.COLUMN_METADATA_COMPARISON))
                .contains("There is no column");
    }

    @Test
    void findDiffAggregatesCounts() throws IOException {
        when(tableProvider.getTables()).thenReturn(List.of(table));
        when(db2Repository.getColumnDetails(table))
                .thenReturn(List.of(col("X", "INT", null, false, false)));
        when(mySqlRepository.getColumnDetails(table))
                .thenReturn(List.of(col("X", "INT", null, false, false)));

        service.findDiff();

        assertThat(ReportTestSupport.readReport(ReportType.COLUMN_METADATA_COMPARISON))
                .contains("Total:1", "Successful:1", "Failure:0");
    }
}
