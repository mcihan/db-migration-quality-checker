package com.dbmigrationqualitychecker.service;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.ReportService;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.Db2Repository;
import com.dbmigrationqualitychecker.repository.MySqlRepository;
import com.dbmigrationqualitychecker.repository.entity.RecordData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.dbmigrationqualitychecker.report.ReportType.RANDOM_DATA_COMPARISON;


@Service
@RequiredArgsConstructor
@Slf4j
public class RandomDataComparisonService extends ComparisonServiceBase {

    private final Db2Repository db2Repository;
    private final MySqlRepository mySqlRepository;
    private final TableProvider tableProvider;

    public void findDiff() {
        Instant startTime = initiate(RANDOM_DATA_COMPARISON);
        int threadPoolSize = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        List<Table> tables = tableProvider.getTables();
        log.info("RandomDataComparison start!");

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

        reportResult(RANDOM_DATA_COMPARISON, (int) successfulTestCount, (int) failedTestCount, startTime);

        executor.shutdown();
        log.info("RandomDataComparison end!");
    }

    private boolean compare(Table table) {
        if (StringUtils.isNotBlank(table.getIdName())) {
            return checkTableWhichHasPrimaryId(table);
        } else {
            return fullColumnCheck(table);
        }
    }

    private boolean checkTableWhichHasPrimaryId(Table table) {
        String tableName = table.getTableName();
        String query = "";
        QueryResult db2RandomDataQueryResult = db2Repository.getRandomData(table);
        List<RecordData> db2Result = db2RandomDataQueryResult.getResult();
        if (!db2Result.isEmpty()) {
            List<String> randomIds = db2Result.stream().map(r -> r.getId()).toList();
            QueryResult mysqlQueryResult = mySqlRepository.findAllByIds(table, randomIds);

            List<RecordData> mysqlResult = mysqlQueryResult.getResult();
            boolean isFailed = false;
            String failureDetail = "";
            query = mysqlQueryResult.getQuery();

            for (String id : randomIds) {
                Optional<RecordData> db2Opt = db2Result.stream().filter(r -> r.getId().equals(id)).findFirst();
                if (db2Opt.isPresent()) {
                    RecordData db2RecordData = db2Opt.get();
                    Optional<RecordData> mysqlDataOpt = mysqlResult.stream().filter(r -> r.getId().equals(id)).findFirst();
                    if (mysqlDataOpt.isPresent()) {
                        RecordData mysqlRecord = mysqlDataOpt.get();
                        for (String key : db2RecordData.getColumns().keySet()) {
                            String db2Value = db2RecordData.getColumns().get(key);
                            String mysqlValue = mysqlRecord.getColumns().get(key);
                            if (compareValues(db2Value, mysqlValue)) {
                                isFailed = true;
                                failureDetail += String.format("Failed column: %s\n", key);
                                failureDetail += String.format("on db2:  \t%s\n", db2Value);
                                failureDetail += String.format("on mysql:\t%s\n\n", mysqlValue);
                            }
                        }
                    } else {
                        isFailed = true;
                        failureDetail += String.format("There is no corresponded data on Mysql. ID:%s\n", id);
                    }
                } else {
                    isFailed = true;
                }

                if (isFailed) {
                    query = buildQuery(tableName, table.getIdName(), id);
                    String description = "Data mismatch between DB2 and Mysql DB.";
                    ReportService.writeFailedResult(RANDOM_DATA_COMPARISON, tableName, description, query, failureDetail);
                    return false;
                }
            }

        } else {
            log.info("Skipped, there is no enough data: " + db2Result.size());
            reportSuccessfulResult(tableName, 0, List.of(query));
            return true;
        }

        reportSuccessfulResult(tableName, db2Result.size(), List.of(query));
        return true;
    }


    private boolean fullColumnCheck(Table table) {
        String tableName = table.getTableName();
        QueryResult db2QueryResult = db2Repository.getDataFromTable(table);
        List<RecordData> db2Result = db2QueryResult.getResult();

        ArrayList<String> queries = new ArrayList<>();
        for (RecordData record : db2Result) {
            QueryResult mysqlQueryResult = mySqlRepository.getDataReportFromTable(table, record);
            String query = mysqlQueryResult.getQuery();
            List<RecordData> result = mysqlQueryResult.getResult();
            queries.add(query);

            if (result.isEmpty()) {
                String description = "Corresponded record couldn't found on Mysql DB.";
                ReportService.writeFailedResult(RANDOM_DATA_COMPARISON, tableName, description, query);
                return false;
            }

            if (result.size() > 1) {
                String description = "Multiple records founded on Mysql DB.";
                ReportService.writeFailedResult(RANDOM_DATA_COMPARISON, tableName, description, query);
                return false;
            }
        }
        reportSuccessfulResult(tableName, db2Result.size(), queries);
        return true;
    }


    private void reportSuccessfulResult(String tableName, int resultSize, List<String> queries) {
        if (resultSize < 1) {
            String description = "There is no data on both of DB2 and MYSQL";
            ReportService.writeSuccessfulResult(RANDOM_DATA_COMPARISON, tableName, description, buildQuery(tableName));
        } else {
            String description = String.format("%s Records between MySQL and DB2 checked and they matched!", resultSize);
            ReportService.writeSuccessfulResult(RANDOM_DATA_COMPARISON, tableName, description);
        }
    }

    public boolean compareValues(String db2Value, String mysqlValue) {
        if (db2Value == null && mysqlValue == null) {
            return false;
        }

        return compareCheckingIfDate(db2Value, mysqlValue);
    }

    public boolean compareCheckingIfDate(String db2Value, String mysqlValue) {

        try {
            if (db2Value.length() != mysqlValue.length()) {
                int min = Math.min(db2Value.length(), mysqlValue.length());

                if (db2Value.length() > min && !checkPadding(db2Value)
                        || mysqlValue.length() > min && !checkPadding(mysqlValue)) {
                    return false;
                }

                db2Value = db2Value.substring(0, min);
                mysqlValue = mysqlValue.substring(0, min);
            }

        } catch (Exception e) {
        }
        return db2Value != null && !db2Value.equals(mysqlValue);
    }

    private boolean checkPadding(String date) {
        for (char c : date.toCharArray()) {
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
