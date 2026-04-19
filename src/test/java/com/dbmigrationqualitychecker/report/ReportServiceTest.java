package com.dbmigrationqualitychecker.report;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportServiceTest {

    private static final ReportType TYPE = ReportType.COLUMN_NAME_COMPARISON;

    @BeforeEach
    @AfterEach
    void cleanReports() throws IOException {
        ReportTestSupport.cleanReportDir();
    }

    @Test
    void writesSuccessfulResultWithSingleQuery() throws IOException {
        ReportService.writeSuccessfulResult(TYPE, "T", "ok", "SELECT 1");

        String text = ReportTestSupport.readReport(TYPE);
        assertThat(text).contains("[Test PASSED] : T", "ok", "SELECT 1", "QUERIES:");
    }

    @Test
    void writesSuccessfulResultWithMultipleQueries() throws IOException {
        ReportService.writeSuccessfulResult(TYPE, "T", "ok", List.of("Q1", "Q2"));

        String text = ReportTestSupport.readReport(TYPE);
        assertThat(text).contains("Q1").contains("Q2");
    }

    @Test
    void writesFailedResultWithQueryAndFailureDetails() throws IOException {
        ReportService.writeFailedResult(TYPE, "T", "desc", "Q", "failing-details");

        String text = ReportTestSupport.readReport(TYPE);
        assertThat(text).contains("[Test FAILED]", "desc", "FAILURE DETAILS:", "failing-details");
    }

    @Test
    void writeToFirstLinePrependsText() throws IOException {
        ReportService.writeSuccessfulResult(TYPE, "T", "ok");
        ReportService.writeToFirstLine(TYPE, "HEADER");

        String text = ReportTestSupport.readReport(TYPE);
        assertThat(text).startsWith("HEADER");
    }

    @Test
    void removeFileDeletesExistingFile() throws IOException {
        ReportService.writeSuccessfulResult(TYPE, "T", "ok");
        assertThat(ReportTestSupport.readReport(TYPE)).isNotEmpty();

        ReportService.removeFile(TYPE);
        assertThat(ReportTestSupport.readReport(TYPE)).isEmpty();
    }

    @Test
    void removeFileIsSafeWhenFileDoesNotExist() {
        ReportService.removeFile(ReportType.INDEX_COMPARISON);
    }

    @Test
    void writeWarningProducesUnSeparatedBlock() throws IOException {
        ReportService.writeWarning(TYPE, "watch out");

        String text = ReportTestSupport.readReport(TYPE);
        assertThat(text).contains("[WARNING] : watch out");
    }
}
