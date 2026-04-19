package com.dbmigrationqualitychecker.service;

import static com.dbmigrationqualitychecker.report.ReportType.TABLE_ROW_COUNT_COMPARISON;

import com.dbmigrationqualitychecker.report.ReportService;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TableRowCountComparisonService extends ComparisonServiceBase {

    private final DatabaseRepository sourceRepository;
    private final DatabaseRepository targetRepository;
    private final TableProvider tableProvider;

    public void findDiff() {
        Instant startTime = initiate(TABLE_ROW_COUNT_COMPARISON);
        int threadPoolSize = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        List<Table> tables = tableProvider.getTables();
        log.info("TableRowCountComparison start!");

        List<CompletableFuture<Boolean>> futures = tables.stream()
                .map(table -> CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                log.info("Comparing table: {}", table.getTableName());
                                return compare(table);
                            } catch (Exception ex) {
                                log.error("Error comparing table {}: {}", table.getTableName(), ex.getMessage(), ex);
                                return false;
                            }
                        },
                        executor))
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
        int sourceCount = sourceRepository.getRowCount(table);
        int targetCount = targetRepository.getRowCount(table);

        String query = String.format(
                "select count(1) from %s.%s %s", table.getSourceSchema(), tableName, table.getQueryCondition());
        if (sourceCount == targetCount) {
            String description =
                    String.format("Total data count matches. source: %s, target: %s", sourceCount, targetCount);
            ReportService.writeSuccessfulResult(TABLE_ROW_COUNT_COMPARISON, tableName, description, query);
            return true;
        }
        String description = "Total data count does not match.\n" + String.format("source:\t%s%n", sourceCount)
                + String.format("target:\t%s%n%n", targetCount)
                + String.format("Diff:\t%s%n", Math.abs(sourceCount - targetCount))
                + (sourceCount > targetCount ? "source" : "target")
                + " has more data.";
        ReportService.writeFailedResult(TABLE_ROW_COUNT_COMPARISON, tableName, description, query);
        return false;
    }
}
