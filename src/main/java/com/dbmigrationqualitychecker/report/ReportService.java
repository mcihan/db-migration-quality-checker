package com.dbmigrationqualitychecker.report;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
@Slf4j
public class ReportService {

    private static final String FILE_PATH = "report/";
    private static final String lineSeparator = "--------------------------------------------------------------------------------------------------";


    public static void writeSuccessfulResult(ReportType reportType, String tableName, String description, String query) {
        String reportText = String.format("[Test PASSED] : %s\n" + "%s" + "\n\nQUERIES:\n" + "%s\n\n", tableName, description, query);
        writeToFile(reportText, reportType);
    }

    public static void writeSuccessfulResult(ReportType reportType, String tableName, String description) {
        String reportText = String.format("[Test PASSED] : %s\n" + "%s" + "\n\n", tableName, description);
        writeToFile(reportText, reportType);
    }

    public static void writeSuccessfulResult(ReportType reportType, String tableName, String description, List<String> queries) {
        String reportText = String.format("[Test PASSED] : %s\n" + "%s" + "\n\nQUERIES:\n" + "%s\n\n", tableName, description, queries.stream().collect(Collectors.joining("\n")));
        writeToFile(reportText, reportType);
    }

    public static void writeFailedResult(ReportType reportType, String tableName, String description, String query) {
        String reportText = String.format("[Test FAILED] : %s\n" + "%s" + "\n\nQUERIES:\n" + "%s\n\n", tableName, description, query);
        writeToFile(reportText, reportType);
    }


    public static void writeFailedResult(ReportType reportType, String tableName, String description) {
        String reportText = String.format("[Test FAILED] : %s\n" + "%s" + "\n\n", tableName, description);
        writeToFile(reportText, reportType);
    }

    public static void writeFailedResult(ReportType reportType, String tableName, String description, String query, String failureDetails) {
        String reportText = String.format("[Test FAILED] : %s\n" + "%s" + "\n\nQUERIES:\n" + "%s\n\n" + "FAILURE DETAILS:\n%s\n", tableName, description, query, failureDetails);
        writeToFile(reportText, reportType);
    }

    public static void writeWarning(ReportType reportType, String description) {
        String reportText = String.format("[WARNING] : %s\n\n", description);
        writeToFile(reportText, reportType, false);
    }

    public static void writeFailedResult(ReportType reportType, String tableName, String description, List<String> queries) {
        String reportText = String.format("[Test FAILED] : %s\n" + "%s" + "\n\nQUERIES:\n" + "%s\n\n", tableName, description, queries.stream().collect(Collectors.joining("\n")));
        writeToFile(reportText, reportType);
    }

    public static void writeToFile(String text, ReportType reportType) {
        writeToFile(text, reportType, true);
    }

    public static void writeToFile(String text, ReportType reportType, boolean isSeperatedItem) {
        try {
            Path path = Paths.get(getFileName(reportType));

            try {
                if (!Files.exists(path)) {
                    Files.createDirectories(path.getParent());
                    Files.createFile(path);
                }
            } catch (Exception e) {

            }

            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.APPEND)) {
                writer.write(text);
                if (isSeperatedItem) {
                    writer.write(lineSeparator);
                    writer.newLine();
                }
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void removeFile(ReportType reportType) {
        try {
            Path path = Paths.get(getFileName(reportType));
            if (Files.exists(path)) {
                Files.delete(path);
            } else {
                System.out.println("File does not exist: " + path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    public static void writeToSpecificLine(ReportType reportType, String text, int lineNumber) {
        try {
            Path path = Paths.get(getFileName(reportType));
            List<String> lines = Files.exists(path) ? Files.readAllLines(path) : List.of();

            while (lines.size() < lineNumber - 1) {
                lines.add("");
            }

            lines.add(lineNumber - 1, text);
            Files.write(path, lines);
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit file", e);
        }
    }

    public static void writeToFirstLine(ReportType reportType, String text) {
        try {
            Path path = Paths.get(getFileName(reportType));
            List<String> lines = Files.exists(path) ? Files.readAllLines(path) : new ArrayList<>();
            text += "\n\n" + lineSeparator;
            lines.add(0, text);
            Files.write(path, lines);
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit file", e);
        }
    }

    private static String getFileName(ReportType reportType) {
        return FILE_PATH + reportType.name() + "_TEST_RESULT.txt";
    }
}
