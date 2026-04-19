package com.dbmigrationqualitychecker.service;

import com.dbmigrationqualitychecker.report.ReportService;
import com.dbmigrationqualitychecker.report.ReportTestSupport;
import com.dbmigrationqualitychecker.report.ReportType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonServiceBaseTest {

    private final ComparisonServiceBase base = new ComparisonServiceBase() {};

    @BeforeEach
    @AfterEach
    void cleanReports() throws IOException {
        ReportTestSupport.cleanReportDir();
    }

    @Test
    void formatsDurationOfTwoMinutesFiveSeconds() {
        Instant start = Instant.ofEpochSecond(0);
        Instant end = Instant.ofEpochSecond(125);
        assertThat(ComparisonServiceBase.formatDuration(start, end)).isEqualTo("2m 5s");
    }

    @Test
    void initiateRemovesExistingFile() throws IOException {
        ReportService.writeSuccessfulResult(ReportType.INDEX_COMPARISON, "T", "ok");
        assertThat(ReportTestSupport.readReport(ReportType.INDEX_COMPARISON)).isNotEmpty();

        base.initiate(ReportType.INDEX_COMPARISON);
        assertThat(ReportTestSupport.readReport(ReportType.INDEX_COMPARISON)).isEmpty();
    }

    @Test
    void reportResultPrependsSummaryHeader() throws IOException {
        Instant start = base.initiate(ReportType.INDEX_COMPARISON);
        ReportService.writeSuccessfulResult(ReportType.INDEX_COMPARISON, "T", "ok");
        base.reportResult(ReportType.INDEX_COMPARISON, 2, 1, start);

        String text = ReportTestSupport.readReport(ReportType.INDEX_COMPARISON);
        assertThat(text).contains("*** INDEX_COMPARISON TEST ***", "Total:3", "Successful:2", "Failure:1");
    }
}
