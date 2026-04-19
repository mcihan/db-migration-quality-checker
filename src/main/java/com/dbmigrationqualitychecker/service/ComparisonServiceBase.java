package com.dbmigrationqualitychecker.service;

import com.dbmigrationqualitychecker.report.ReportService;
import com.dbmigrationqualitychecker.report.ReportType;

import java.time.Duration;
import java.time.Instant;


public abstract class ComparisonServiceBase {


    private static String getInitialDescription(String title, Instant startTime) {
        return new StringBuilder(String.format("*** %s TEST ***", title))
                .append("\n")
                .append("\n")
                .append("Start Time: ").append(startTime.toString())
                .toString();
    }

    public static String formatDuration(Instant start, Instant end) {
        Duration duration = Duration.between(start, end);
        long minutes = duration.toMinutes();
        long seconds = duration.getSeconds() % 60;
        return String.format("%dm %ds", minutes, seconds);
    }

    public Instant initiate(ReportType reportType) {
        ReportService.removeFile(reportType);
        return Instant.now();
    }

    public void reportResult(ReportType reportType, int successCount, int failCount, Instant startTime) {
        Instant endTime = Instant.now();
        String duration = formatDuration(startTime, endTime);
        int total = successCount + failCount;

        String initialDescription = getInitialDescription(reportType.name(), startTime);

        StringBuilder reportText = new StringBuilder(initialDescription);
        reportText.append("\n");
        reportText.append("End Time:").append("\t").append(endTime).append("\n");
        reportText.append("Duration:").append("\t").append(duration).append("\n");
        reportText.append("Total:").append(total).append(", ");
        reportText.append("Successful:").append(successCount).append(", ");
        reportText.append("Failure:").append(failCount).append(", ");
        reportText.append("\n");
        reportText.append("\n");
        reportText.append("\n");

        ReportService.writeToFirstLine(reportType, reportText.toString());
    }


}
