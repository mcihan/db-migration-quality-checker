package com.dbmigrationqualitychecker.service;

import static com.dbmigrationqualitychecker.report.ReportType.RANDOM_DATA_COMPARISON;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.ReportService;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import com.dbmigrationqualitychecker.repository.entity.RecordData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RandomDataComparisonService extends ComparisonServiceBase {

    private final DatabaseRepository sourceRepository;
    private final DatabaseRepository targetRepository;
    private final TableProvider tableProvider;

    public void findDiff() {
        Instant startTime = initiate(RANDOM_DATA_COMPARISON);
        int threadPoolSize = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        List<Table> tables = tableProvider.getTables();
        log.info("RandomDataComparison start!");

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

        reportResult(RANDOM_DATA_COMPARISON, (int) successfulTestCount, (int) failedTestCount, startTime);

        executor.shutdown();
        log.info("RandomDataComparison end!");
    }

    private boolean compare(Table table) {
        if (StringUtils.isNotBlank(table.getIdName())) {
            return checkTableWhichHasPrimaryId(table);
        }
        return fullColumnCheck(table);
    }

    private boolean checkTableWhichHasPrimaryId(Table table) {
        String tableName = table.getTableName();
        String query = "";
        QueryResult<RecordData> sourceQueryResult = sourceRepository.getRandomData(table);
        List<RecordData> sourceResult = sourceQueryResult.getResult();
        if (sourceResult.isEmpty()) {
            log.info("Skipped, there is no enough data: {}", sourceResult.size());
            reportSuccessfulResult(tableName, 0, List.of(query));
            return true;
        }

        List<String> randomIds = sourceResult.stream().map(RecordData::getId).toList();
        QueryResult<RecordData> targetQueryResult = targetRepository.findAllByIds(table, randomIds);
        List<RecordData> targetResult = targetQueryResult.getResult();

        boolean isFailed = false;
        String failureDetail = "";
        query = targetQueryResult.getQuery();

        for (String id : randomIds) {
            Optional<RecordData> sourceOpt =
                    sourceResult.stream().filter(r -> r.getId().equals(id)).findFirst();
            if (sourceOpt.isPresent()) {
                RecordData sourceRecord = sourceOpt.get();
                Optional<RecordData> targetOpt =
                        targetResult.stream().filter(r -> r.getId().equals(id)).findFirst();
                if (targetOpt.isPresent()) {
                    RecordData targetRecord = targetOpt.get();
                    for (String key : sourceRecord.getColumns().keySet()) {
                        String sourceValue = sourceRecord.getColumns().get(key);
                        String targetValue = targetRecord.getColumns().get(key);
                        if (compareValues(sourceValue, targetValue)) {
                            isFailed = true;
                            failureDetail += String.format("Failed column: %s%n", key);
                            failureDetail += String.format("on source:\t%s%n", sourceValue);
                            failureDetail += String.format("on target:\t%s%n%n", targetValue);
                        }
                    }
                } else {
                    isFailed = true;
                    failureDetail += String.format("There is no corresponded data on target. ID:%s%n", id);
                }
            } else {
                isFailed = true;
            }

            if (isFailed) {
                query = buildQuery(tableName, table.getIdName(), id);
                String description = "Data mismatch between source and target DB.";
                ReportService.writeFailedResult(RANDOM_DATA_COMPARISON, tableName, description, query, failureDetail);
                return false;
            }
        }

        reportSuccessfulResult(tableName, sourceResult.size(), List.of(query));
        return true;
    }

    private boolean fullColumnCheck(Table table) {
        String tableName = table.getTableName();
        QueryResult<RecordData> sourceQueryResult = sourceRepository.getDataFromTable(table);
        List<RecordData> sourceResult = sourceQueryResult.getResult();

        List<String> queries = new ArrayList<>();
        for (RecordData record : sourceResult) {
            QueryResult<RecordData> targetQueryResult = targetRepository.getDataReportFromTable(table, record);
            String query = targetQueryResult.getQuery();
            List<RecordData> result = targetQueryResult.getResult();
            queries.add(query);

            if (result.isEmpty()) {
                String description = "Corresponded record couldn't found on target DB.";
                ReportService.writeFailedResult(RANDOM_DATA_COMPARISON, tableName, description, query);
                return false;
            }

            if (result.size() > 1) {
                String description = "Multiple records founded on target DB.";
                ReportService.writeFailedResult(RANDOM_DATA_COMPARISON, tableName, description, query);
                return false;
            }
        }
        reportSuccessfulResult(tableName, sourceResult.size(), queries);
        return true;
    }

    private void reportSuccessfulResult(String tableName, int resultSize, List<String> queries) {
        if (resultSize < 1) {
            String description = "There is no data on both of source and target";
            ReportService.writeSuccessfulResult(RANDOM_DATA_COMPARISON, tableName, description, buildQuery(tableName));
        } else {
            String description =
                    String.format("%s Records between source and target checked and they matched!", resultSize);
            ReportService.writeSuccessfulResult(RANDOM_DATA_COMPARISON, tableName, description);
        }
    }

    public boolean compareValues(String sourceValue, String targetValue) {
        if (sourceValue == null && targetValue == null) {
            return false;
        }
        return compareCheckingIfDate(sourceValue, targetValue);
    }

    public boolean compareCheckingIfDate(String sourceValue, String targetValue) {
        try {
            if (sourceValue.length() != targetValue.length()) {
                int min = Math.min(sourceValue.length(), targetValue.length());

                if (sourceValue.length() > min && !checkPadding(sourceValue)
                        || targetValue.length() > min && !checkPadding(targetValue)) {
                    return false;
                }

                sourceValue = sourceValue.substring(0, min);
                targetValue = targetValue.substring(0, min);
            }
        } catch (Exception ignored) {
            // null value or empty string — fall through to the final comparison
        }
        return sourceValue != null && !sourceValue.equals(targetValue);
    }

    private boolean checkPadding(String value) {
        for (char c : value.toCharArray()) {
            if (c != '0') {
                return false;
            }
        }
        return true;
    }

    private String buildQuery(String tableName) {
        return String.format("SELECT * FROM %s;", tableName);
    }

    private String buildQuery(String tableName, String idName, String idValue) {
        return String.format("SELECT * FROM %s WHERE %s=%s;", tableName, idName, idValue);
    }
}
