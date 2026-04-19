package com.dbmigrationqualitychecker.service;

import com.dbmigrationqualitychecker.report.ReportService;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.Db2Repository;
import com.dbmigrationqualitychecker.repository.MySqlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.dbmigrationqualitychecker.report.ReportType.TABLE_ROW_COUNT_COMPARISON;


@Service
@RequiredArgsConstructor
@Slf4j
public class TableRowCountComparisonService extends ComparisonServiceBase {

    private final Db2Repository db2Repository;
    private final MySqlRepository mySqlRepository;
    private final TableProvider tableProvider;

    public void findDiff() {
        Instant startTime = initiate(TABLE_ROW_COUNT_COMPARISON);
        int threadPoolSize = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        List<Table> tables = tableProvider.getTables();
        log.info("TableRowCountComparison start!");

        List<CompletableFuture<Boolean>> futures = tables.stream()
                .map(table -> CompletableFuture.supplyAsync(() -> {
                    try {
                        log.info("Comparing table: {}", table.getTableName());
                        return compare(table);
                    } catch (Exception ex) {
                        log.error("Error comparing table {}: {}", table.getTableName(), ex.getMessage(), ex);
                        return false;
                    }
                }, executor))
                .collect(Collectors.toList());

        List<Boolean> results = futures.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (InterruptedException | ExecutionException ex) {
                        log.error("Error retrieving future result: {}", ex.getMessage(), ex);
                        return false;
                    }
                })
                .toList();

        long successfulTestCount = results.stream().filter(Boolean::booleanValue).count();
        long failedTestCount = tables.size() - successfulTestCount;

        reportResult(TABLE_ROW_COUNT_COMPARISON, (int) successfulTestCount, (int) failedTestCount, startTime);

        executor.shutdown();
        log.info("TableRowCountComparison end!");
    }

    private boolean compare(Table table) {
        String tableName = table.getTableName();
        int db2TableCount = db2Repository.getRowCount(table);
        int mysqlTableCount = mySqlRepository.getRowCount(table);

        String query = String.format("select count(1) from %s.%s %s", table.getSourceSchema(), tableName, table.getQueryCondition());
        if (db2TableCount == mysqlTableCount) {
            String description = String.format("Total data count matches. DB2: %s, Mysql: %s", db2TableCount, mysqlTableCount);
            ReportService.writeSuccessfulResult(TABLE_ROW_COUNT_COMPARISON, tableName, description, query);
            return true;
        } else {
            StringBuilder description = new StringBuilder();
            description.append("Total data count does not match.\n")
                    .append(String.format("DB2: \t%s\n", db2TableCount))
                    .append(String.format("MYSQL: \t%s\n\n", mysqlTableCount))
                    .append(String.format("Diff: \t%s\n", Math.abs(db2TableCount - mysqlTableCount)))
                    .append(getDbWhichHasMoreData(db2TableCount, mysqlTableCount))
                    .append(" has more data.");
            ReportService.writeFailedResult(TABLE_ROW_COUNT_COMPARISON, tableName, description.toString(), query);
            return false;
        }
    }
    private String getDbWhichHasMoreData(int db2TableCount, int mysqlTableCount) {
        return db2TableCount > mysqlTableCount ? "DB2" : "MySQL";
    }

}
