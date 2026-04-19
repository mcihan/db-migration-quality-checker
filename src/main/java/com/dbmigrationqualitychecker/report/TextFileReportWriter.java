package com.dbmigrationqualitychecker.report;

import com.dbmigrationqualitychecker.check.CheckOutcome;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Default {@link ReportWriter}: writes one {@code .txt} file per
 * {@link ReportType} under the configured directory ({@code report/} by
 * default).
 */
@Component
@Slf4j
public class TextFileReportWriter implements ReportWriter {

    private static final String SEPARATOR = "-".repeat(98);

    private final Path reportDir;

    public TextFileReportWriter(@Value("${config.report-dir:report}") String reportDir) {
        this.reportDir = Paths.get(reportDir);
    }

    @Override
    public void write(ReportType type, Instant startedAt, Instant endedAt, List<CheckOutcome> outcomes) {
        long passed = outcomes.stream().filter(CheckOutcome::passed).count();
        long failed = outcomes.size() - passed;

        StringBuilder body = new StringBuilder();
        body.append(header(type, startedAt, endedAt, outcomes.size(), passed, failed));
        for (CheckOutcome outcome : outcomes) {
            body.append(SEPARATOR).append('\n').append(render(outcome)).append('\n');
        }

        try {
            Files.createDirectories(reportDir);
            Path path = reportDir.resolve(type.name() + "_TEST_RESULT.txt");
            Files.writeString(path, body.toString());
            log.info("Wrote {} ({} pass, {} fail)", path, passed, failed);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write report " + type, e);
        }
    }

    private static String header(
            ReportType type, Instant start, Instant end, int total, long passed, long failed) {
        return new StringBuilder("*** ")
                .append(type.name())
                .append(" TEST ***")
                .append("\n\n")
                .append("Start Time: ")
                .append(start)
                .append('\n')
                .append("End Time:   ")
                .append(end)
                .append('\n')
                .append("Duration:   ")
                .append(formatDuration(start, end))
                .append('\n')
                .append("Total:")
                .append(total)
                .append(", Successful:")
                .append(passed)
                .append(", Failure:")
                .append(failed)
                .append("\n\n\n")
                .toString();
    }

    private static String render(CheckOutcome outcome) {
        StringBuilder sb = new StringBuilder();
        sb.append(outcome.passed() ? "[Test PASSED] : " : "[Test FAILED] : ")
                .append(outcome.tableName())
                .append('\n')
                .append(outcome.description())
                .append('\n');
        if (!outcome.queries().isEmpty()) {
            sb.append("\nQUERIES:\n");
            for (String q : outcome.queries()) {
                sb.append(q).append('\n');
            }
        }
        if (outcome instanceof CheckOutcome.Failed failed
                && failed.failureDetails() != null
                && !failed.failureDetails().isEmpty()) {
            sb.append("\nFAILURE DETAILS:\n").append(failed.failureDetails()).append('\n');
        }
        sb.append('\n');
        return sb.toString();
    }

    static String formatDuration(Instant start, Instant end) {
        Duration d = Duration.between(start, end);
        long minutes = d.toMinutes();
        long seconds = d.getSeconds() % 60;
        long millis = d.toMillis() % 1000;
        return String.format("%dm %ds %dms", minutes, seconds, millis);
    }
}
