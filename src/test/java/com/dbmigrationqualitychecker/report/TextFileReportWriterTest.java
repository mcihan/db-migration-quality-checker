package com.dbmigrationqualitychecker.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbmigrationqualitychecker.check.CheckOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextFileReportWriterTest {

    @Test
    void writesHeaderAndPassedBlock(@TempDir Path dir) throws IOException {
        TextFileReportWriter writer = new TextFileReportWriter(dir.toString());
        writer.write(
                ReportType.COLUMN_NAME_COMPARISON,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:05Z"),
                List.of(CheckOutcome.passed("TBL", "ok", List.of("SELECT 1"))));

        String content = Files.readString(dir.resolve("COLUMN_NAME_COMPARISON_TEST_RESULT.txt"));
        assertThat(content)
                .contains("*** COLUMN_NAME_COMPARISON TEST ***")
                .contains("Total:1, Successful:1, Failure:0")
                .contains("[Test PASSED] : TBL")
                .contains("SELECT 1");
    }

    @Test
    void writesFailedBlockWithDetails(@TempDir Path dir) throws IOException {
        TextFileReportWriter writer = new TextFileReportWriter(dir.toString());
        writer.write(
                ReportType.RANDOM_DATA_COMPARISON,
                Instant.now(),
                Instant.now(),
                List.of(CheckOutcome.failed("TBL", "mismatch", List.of("q"), "col X differs")));

        String content = Files.readString(dir.resolve("RANDOM_DATA_COMPARISON_TEST_RESULT.txt"));
        assertThat(content)
                .contains("[Test FAILED] : TBL")
                .contains("mismatch")
                .contains("FAILURE DETAILS:")
                .contains("col X differs");
    }

    @Test
    void countsPassedAndFailedOutcomes(@TempDir Path dir) throws IOException {
        TextFileReportWriter writer = new TextFileReportWriter(dir.toString());
        writer.write(
                ReportType.INDEX_COMPARISON,
                Instant.now(),
                Instant.now(),
                List.of(
                        CheckOutcome.passed("A", "ok"),
                        CheckOutcome.failed("B", "nope"),
                        CheckOutcome.passed("C", "ok")));

        String content = Files.readString(dir.resolve("INDEX_COMPARISON_TEST_RESULT.txt"));
        assertThat(content).contains("Total:3, Successful:2, Failure:1");
    }

    @Test
    void createsOutputDirectoryIfMissing(@TempDir Path dir) throws IOException {
        Path nested = dir.resolve("does/not/exist");
        TextFileReportWriter writer = new TextFileReportWriter(nested.toString());

        writer.write(
                ReportType.TABLE_ROW_COUNT_COMPARISON,
                Instant.now(),
                Instant.now(),
                List.of(CheckOutcome.passed("T", "ok")));

        assertThat(Files.exists(nested.resolve("TABLE_ROW_COUNT_COMPARISON_TEST_RESULT.txt")))
                .isTrue();
    }
}
