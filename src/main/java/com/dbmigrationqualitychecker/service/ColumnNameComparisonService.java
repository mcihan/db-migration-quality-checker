package com.dbmigrationqualitychecker.service;

import static com.dbmigrationqualitychecker.report.ReportType.COLUMN_NAME_COMPARISON;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.ReportService;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ColumnNameComparisonService extends ComparisonServiceBase {

    private final DatabaseRepository sourceRepository;
    private final DatabaseRepository targetRepository;
    private final TableProvider tableProvider;

    public void findDiff() {
        Instant startTime = initiate(COLUMN_NAME_COMPARISON);
        int successfulTestCount = 0;
        int failedTestCount = 0;
        List<Table> tables = tableProvider.getTables();
        log.info("ColumnNameComparison start!");
        try {
            for (Table table : tables) {
                log.info("ColumnNameComparison table: {}", table);
                boolean isMatches = compare(table);

                if (isMatches) {
                    successfulTestCount++;
                } else {
                    failedTestCount++;
                }
            }
            reportResult(COLUMN_NAME_COMPARISON, successfulTestCount, failedTestCount, startTime);
        } catch (Exception e) {
            log.error("ColumnNameComparison aborted due to unexpected error", e);
        }
        log.info("ColumnNameComparison end!");
    }

    public boolean compare(Table table) {
        String tableName = table.getTableName();
        QueryResult<String> sourceResult = sourceRepository.getColumnNames(table);
        QueryResult<String> targetResult = targetRepository.getColumnNames(table);

        List<String> sourceColumns = sourceResult.getResult();
        List<String> targetColumns = targetResult.getResult();
        List<String> queries = List.of(sourceResult.getQuery(), targetResult.getQuery());

        if (sourceColumns.equals(targetColumns)) {
            ReportService.writeSuccessfulResult(COLUMN_NAME_COMPARISON, tableName, "Column names match.", queries);
            return true;
        }
        String message = String.format(
                "Column names do not match!%n\tsource columns: %s%n\ttarget columns: %s", sourceColumns, targetColumns);
        ReportService.writeFailedResult(COLUMN_NAME_COMPARISON, tableName, message, queries);
        return false;
    }
}
