package com.dbmigrationqualitychecker.report;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class ReportTestSupport {

    public static final Path REPORT_DIR = Paths.get("report");

    private ReportTestSupport() {
    }

    public static void cleanReportDir() throws IOException {
        if (Files.exists(REPORT_DIR)) {
            Files.walkFileTree(REPORT_DIR, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        Files.createDirectories(REPORT_DIR);
    }

    public static String readReport(ReportType reportType) throws IOException {
        Path path = REPORT_DIR.resolve(reportType.name() + "_TEST_RESULT.txt");
        return Files.exists(path) ? Files.readString(path) : "";
    }
}
