package com.dbmigrationqualitychecker.service;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.ReportService;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.Db2Repository;
import com.dbmigrationqualitychecker.repository.MySqlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

import static com.dbmigrationqualitychecker.report.ReportType.COLUMN_NAME_COMPARISON;

@Service
@RequiredArgsConstructor
@Slf4j
public class ColumnNameComparisonService extends ComparisonServiceBase {

    private final Db2Repository db2Repository;
    private final MySqlRepository mySqlRepository;
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
        QueryResult db2QueryResult = db2Repository.getColumnNames(table);
        QueryResult mysqlQueryResult = mySqlRepository.getColumnNames(table);

        List<String> db2ColumnNames = db2QueryResult.getResult();
        List<String> mysqlColumnNames = mysqlQueryResult.getResult();
        List<String> queries = List.of(db2QueryResult.getQuery(), mysqlQueryResult.getQuery());

        if (db2ColumnNames.equals(mysqlColumnNames)) {
            ReportService.writeSuccessfulResult(COLUMN_NAME_COMPARISON, tableName, "Column names match.", queries);
            return true;
        } else {
            String message = String.format("Column names do not match!\n\tdb2 columns: %s\n\tmysql columns: %s", db2ColumnNames, mysqlColumnNames);
            ReportService.writeFailedResult(COLUMN_NAME_COMPARISON, tableName, message, queries);
            return false;
        }
    }

}
