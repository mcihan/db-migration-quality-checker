package com.dbmigrationqualitychecker.service;

import com.dbmigrationqualitychecker.report.ReportService;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.Db2Repository;
import com.dbmigrationqualitychecker.repository.MySqlRepository;
import com.dbmigrationqualitychecker.repository.entity.ColumnDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.dbmigrationqualitychecker.report.ReportType.COLUMN_METADATA_COMPARISON;

@Service
@RequiredArgsConstructor
@Slf4j
public class ColumnMetadataComparisonService extends ComparisonServiceBase {
    private final Db2Repository db2Repository;
    private final MySqlRepository mySqlRepository;
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
        List<ColumnDetails> db2Columns = db2Repository.getColumnDetails(table);
        List<ColumnDetails> mysqlColumns = mySqlRepository.getColumnDetails(table);

        if (db2Columns.isEmpty()) {
            ReportService.writeSuccessfulResult(COLUMN_METADATA_COMPARISON, tableName, "There is no column");
            return false;
        }

        boolean hasMismatch = false;
        StringBuilder message = new StringBuilder();

        for (ColumnDetails db2Column : db2Columns) {
            Optional<ColumnDetails> matchingColumn = mysqlColumns.stream()
                    .filter(c -> c.getColumnName().equals(db2Column.getColumnName()))
                    .findFirst();

            if (matchingColumn.isEmpty()) {
                appendMessage(message, "Column missing in MySQL", db2Column.getColumnName());
                ReportService.writeFailedResult(COLUMN_METADATA_COMPARISON, tableName, message.toString());
            }

            ColumnDetails mysqlColumn = matchingColumn.get();
            hasMismatch |= compareColumnAttributes(db2Column, mysqlColumn, message);
        }

        if (hasMismatch) {
            ReportService.writeFailedResult(COLUMN_METADATA_COMPARISON, tableName, message.toString());
            return false;
        }
        return true;
    }

    private boolean compareColumnAttributes(ColumnDetails db2Column, ColumnDetails mysqlColumn, StringBuilder message) {
        boolean mismatchFound = false;
        StringBuilder columnErrormessage = new StringBuilder();
        if (!isDefaultMatch(db2Column.getColumnDefault(), mysqlColumn.getColumnDefault())) {
            appendMessage(columnErrormessage, " Default Value Mismatch", getColumnDefaultValue(db2Column), getColumnDefaultValue(mysqlColumn));
            mismatchFound = true;
        }
        if (!isTypeMatch(db2Column.getColumnType(), mysqlColumn.getColumnType())) {
            appendMessage(columnErrormessage, " Column Type Mismatch", db2Column.getColumnType(), mysqlColumn.getColumnType());
            mismatchFound = true;
        }
        if (db2Column.isNullable() != mysqlColumn.isNullable()) {
            appendMessage(columnErrormessage, " Nullability Mismatch", getNullableValue(db2Column), getNullableValue(mysqlColumn));
            mismatchFound = true;
        }
        if (db2Column.isAutoIncrement() != mysqlColumn.isAutoIncrement()) {
            appendMessage(columnErrormessage, " Auto Increment Mismatch", db2Column.isAutoIncrement(), mysqlColumn.isAutoIncrement());
            mismatchFound = true;
        }

        if (mismatchFound) {
            columnErrormessage.insert(0, "COLUMN NAME= " + db2Column.getColumnName());
            message.append(columnErrormessage);
        }

        return mismatchFound;
    }

    private void appendMessage(StringBuilder message, String issue, Object db2Value, Object mysqlValue) {
        message.append(String.format("%s! DB2: %s, MySQL: %s\n", issue, db2Value, mysqlValue));
    }

    private void appendMessage(StringBuilder message, String issue, String columnName) {
        message.append(String.format("%s: %s\n", issue, columnName));
    }

    private String getColumnDefaultValue(ColumnDetails column) {
        return String.valueOf(column.getColumnDefault());
    }

    private String getNullableValue(ColumnDetails db2Column) {
        return db2Column.isNullable() ? "ALLOWS NULL" : "NOT NULL";
    }

    private boolean isDefaultMatch(String db2ColType, String mysqlColType) {
        if (Objects.isNull(db2ColType) && Objects.isNull(mysqlColType)) {
            return true;
        }
        return StringUtils.equalsAnyIgnoreCase(db2ColType, mysqlColType) ||
                (db2ColType.contains("TIMESTAMP") && mysqlColType.contains("TIMESTAMP")) ||
                (db2ColType.equals("'0'") && mysqlColType.equals("0X30")) ||
                (db2ColType.equals("CHARACTER") && mysqlColType.equals("VARBINARY"));
    }

    private boolean isTypeMatch(String db2ColType, String mysqlColType) {
        return db2ColType.equals(mysqlColType) ||
                (db2ColType.contains("INT") && mysqlColType.contains("INT")) ||
                (db2ColType.contains("TIMESTAMP") && mysqlColType.contains("TIMESTAMP")) ||
                (db2ColType.equals("CHARACTER") && mysqlColType.equals("CHAR")) ||
                (db2ColType.equals("CHARACTER") && mysqlColType.contains("BINARY")) ||
                (db2ColType.equals("VARCHAR") && mysqlColType.contains("BINARY")) ||
                (db2ColType.contains("BIT DATA") && mysqlColType.contains("BINARY"));
    }
}
