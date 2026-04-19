package com.dbmigrationqualitychecker.service;

import static com.dbmigrationqualitychecker.report.ReportType.COLUMN_METADATA_COMPARISON;

import com.dbmigrationqualitychecker.report.ReportService;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import com.dbmigrationqualitychecker.repository.entity.ColumnDetails;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ColumnMetadataComparisonService extends ComparisonServiceBase {

    private final DatabaseRepository sourceRepository;
    private final DatabaseRepository targetRepository;
    private final TableProvider tableProvider;

    public void findDiff() {
        Instant startTime = initiate(COLUMN_METADATA_COMPARISON);
        int successfulTestCount = 0;
        int failedTestCount = 0;
        List<Table> tables = tableProvider.getTables();
        log.info("ColumnMetadataComparison start!");
        try {
            for (Table table : tables) {
                log.info("ColumnMetadataComparison table: {}", table);
                boolean isMatches = shouldDDLMatch(table);

                if (isMatches) {
                    successfulTestCount++;
                } else {
                    failedTestCount++;
                }
            }
            reportResult(COLUMN_METADATA_COMPARISON, successfulTestCount, failedTestCount, startTime);
        } catch (Exception e) {
            log.error("ColumnMetadataComparison aborted due to unexpected error", e);
        }
        log.info("ColumnMetadataComparison end!");
    }

    public boolean shouldDDLMatch(Table table) {
        String tableName = table.getTableName();
        List<ColumnDetails> sourceColumns = sourceRepository.getColumnDetails(table);
        List<ColumnDetails> targetColumns = targetRepository.getColumnDetails(table);

        if (sourceColumns.isEmpty()) {
            ReportService.writeSuccessfulResult(COLUMN_METADATA_COMPARISON, tableName, "There is no column");
            return false;
        }

        boolean hasMismatch = false;
        StringBuilder message = new StringBuilder();

        for (ColumnDetails sourceColumn : sourceColumns) {
            Optional<ColumnDetails> matchingColumn = targetColumns.stream()
                    .filter(c -> c.getColumnName().equals(sourceColumn.getColumnName()))
                    .findFirst();

            if (matchingColumn.isEmpty()) {
                appendMessage(message, "Column missing in target", sourceColumn.getColumnName());
                ReportService.writeFailedResult(COLUMN_METADATA_COMPARISON, tableName, message.toString());
            }

            ColumnDetails targetColumn = matchingColumn.get();
            hasMismatch |= compareColumnAttributes(sourceColumn, targetColumn, message);
        }

        if (hasMismatch) {
            ReportService.writeFailedResult(COLUMN_METADATA_COMPARISON, tableName, message.toString());
            return false;
        }
        return true;
    }

    private boolean compareColumnAttributes(
            ColumnDetails sourceColumn, ColumnDetails targetColumn, StringBuilder message) {
        boolean mismatchFound = false;
        StringBuilder columnErrorMessage = new StringBuilder();
        if (!isDefaultMatch(sourceColumn.getColumnDefault(), targetColumn.getColumnDefault())) {
            appendMessage(
                    columnErrorMessage,
                    " Default Value Mismatch",
                    getColumnDefaultValue(sourceColumn),
                    getColumnDefaultValue(targetColumn));
            mismatchFound = true;
        }
        if (!isTypeMatch(sourceColumn.getColumnType(), targetColumn.getColumnType())) {
            appendMessage(
                    columnErrorMessage,
                    " Column Type Mismatch",
                    sourceColumn.getColumnType(),
                    targetColumn.getColumnType());
            mismatchFound = true;
        }
        if (sourceColumn.isNullable() != targetColumn.isNullable()) {
            appendMessage(
                    columnErrorMessage,
                    " Nullability Mismatch",
                    getNullableValue(sourceColumn),
                    getNullableValue(targetColumn));
            mismatchFound = true;
        }
        if (sourceColumn.isAutoIncrement() != targetColumn.isAutoIncrement()) {
            appendMessage(
                    columnErrorMessage,
                    " Auto Increment Mismatch",
                    sourceColumn.isAutoIncrement(),
                    targetColumn.isAutoIncrement());
            mismatchFound = true;
        }

        if (mismatchFound) {
            columnErrorMessage.insert(0, "COLUMN NAME= " + sourceColumn.getColumnName());
            message.append(columnErrorMessage);
        }

        return mismatchFound;
    }

    private void appendMessage(StringBuilder message, String issue, Object sourceValue, Object targetValue) {
        message.append(String.format("%s! source: %s, target: %s%n", issue, sourceValue, targetValue));
    }

    private void appendMessage(StringBuilder message, String issue, String columnName) {
        message.append(String.format("%s: %s%n", issue, columnName));
    }

    private String getColumnDefaultValue(ColumnDetails column) {
        return String.valueOf(column.getColumnDefault());
    }

    private String getNullableValue(ColumnDetails column) {
        return column.isNullable() ? "ALLOWS NULL" : "NOT NULL";
    }

    private boolean isDefaultMatch(String sourceDefault, String targetDefault) {
        if (Objects.isNull(sourceDefault) && Objects.isNull(targetDefault)) {
            return true;
        }
        if (Objects.isNull(sourceDefault) || Objects.isNull(targetDefault)) {
            return false;
        }
        return StringUtils.equalsAnyIgnoreCase(sourceDefault, targetDefault)
                || (sourceDefault.contains("TIMESTAMP") && targetDefault.contains("TIMESTAMP"))
                || (sourceDefault.equals("'0'") && targetDefault.equals("0X30"))
                || (sourceDefault.equals("CHARACTER") && targetDefault.equals("VARBINARY"));
    }

    private boolean isTypeMatch(String sourceType, String targetType) {
        return sourceType.equals(targetType)
                || (sourceType.contains("INT") && targetType.contains("INT"))
                || (sourceType.contains("TIMESTAMP") && targetType.contains("TIMESTAMP"))
                || (sourceType.equals("CHARACTER") && targetType.equals("CHAR"))
                || (sourceType.equals("CHARACTER") && targetType.contains("BINARY"))
                || (sourceType.equals("VARCHAR") && targetType.contains("BINARY"))
                || (sourceType.contains("BIT DATA") && targetType.contains("BINARY"));
    }
}
