package com.dbmigrationqualitychecker.service;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.ReportService;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.Db2Repository;
import com.dbmigrationqualitychecker.repository.MySqlRepository;
import com.dbmigrationqualitychecker.repository.entity.IndexDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.dbmigrationqualitychecker.report.ReportType.INDEX_COMPARISON;


@Service
@RequiredArgsConstructor
@Slf4j
public class IndexComparisonService extends ComparisonServiceBase {

    private final Db2Repository db2Repository;
    private final MySqlRepository mySqlRepository;
    private final TableProvider tableProvider;

    public void findDiff() {
        Instant startTime = initiate(INDEX_COMPARISON);
        int successfulTestCount = 0;
        int failedTestCount = 0;
        List<Table> tables = tableProvider.getTables();
        log.info("IndexComparison start!");
        try {
            for (Table table : tables) {
                log.info("IndexComparison table: {}", table);
                boolean isMatches = compare(table);

                if (isMatches) {
                    successfulTestCount++;
                } else {
                    failedTestCount++;
                }
            }
            reportResult(INDEX_COMPARISON, successfulTestCount, failedTestCount, startTime);
        } catch (Exception e) {
            log.error("IndexComparison aborted due to unexpected error", e);
        }
        log.info("IndexComparison end!");
    }

    private boolean compare(Table table) {
        QueryResult<IndexDetails> db2QueryResult = db2Repository.getIndexDetails(table);
        QueryResult<IndexDetails> mysqlQueryResult = mySqlRepository.getIndexDetails(table);

        List<IndexDetails> db2ColumnDetails = db2QueryResult.getResult();
        List<IndexDetails> mysqlColumnDetails = mysqlQueryResult.getResult();

        boolean isFailed = false;
        StringBuilder message = new StringBuilder("");
        if (!db2ColumnDetails.isEmpty()) {

            for (IndexDetails db2Index : db2ColumnDetails) {
                String db2ColumnNames = db2Index.getColumnNames();

                Optional<IndexDetails> mysqlOpt = mysqlColumnDetails.stream()
                        .filter(c -> c.getColumnNames().equals(db2ColumnNames))
                        .findFirst();
                if (mysqlOpt.isPresent()) {
                    boolean isIndexColumnsMatch = StringUtils.equals(db2Index.getColumnNames(), mysqlOpt.get().getColumnNames());
                    boolean isUniqueEquals = StringUtils.equals(db2Index.getUnique(), mysqlOpt.get().getUnique());

                    if (!isIndexColumnsMatch) {
                        message.append("\n").append("Mismatches! Index=").append(db2Index.getIndexName()).append("\n");
                        isFailed = true;
                    } else if (!isUniqueEquals) {
                        appendIndexMismatchFailureMessage(db2Index, message);
                        isFailed = true;
                    }

                } else {
                    appendNoIndexFailureMessage(db2Index, message, db2ColumnDetails, mysqlColumnDetails);
                    isFailed = true;
                }
            }
        } else {
            message.append("[WARNING] : Index couldn't be checked, There is no record");
        }

        reportExtraIndexesOnMysqlSide(db2ColumnDetails, mysqlColumnDetails, message);

        if (isFailed) {
            List<String> queries = List.of(db2QueryResult.getQuery(), mysqlQueryResult.getQuery());
            ReportService.writeFailedResult(INDEX_COMPARISON, table.getTableName(), message.toString(), queries);
            return false;
        }
        return true;

    }

    private void reportExtraIndexesOnMysqlSide(List<IndexDetails> db2ColumnDetails, List<IndexDetails> mysqlColumnDetails, StringBuilder message) {
        StringBuilder extraIndexMessage = new StringBuilder();
        if (!db2ColumnDetails.isEmpty()) {
            Set<String> db2IndexColumns = db2ColumnDetails.stream().map(i -> i.getColumnNames()).collect(Collectors.toSet());
            boolean isThereExtraIndexes = false;
            for (IndexDetails mysqlIndexDetail : mysqlColumnDetails) {
                if (db2IndexColumns.add(mysqlIndexDetail.getColumnNames())) {
                    extraIndexMessage.append("IndexName=").append(mysqlIndexDetail.getIndexName());
                    extraIndexMessage.append("\n");
                    extraIndexMessage.append("Columns=").append(mysqlIndexDetail.getColumnNames());
                    extraIndexMessage.append("\n\n");
                    isThereExtraIndexes = true;
                }
            }
            if (isThereExtraIndexes) {
                extraIndexMessage.insert(0, "\n[WARNING] : Extra indexes on mysql!\n");
                message.append(extraIndexMessage);
//                ReportService.writeWarning(INDEX_COMPARISON, extraIndexMessage.toString());
            }

        }

    }

    private void appendNoIndexFailureMessage(IndexDetails db2Index, StringBuilder message, List<IndexDetails> db2ColumnDetails, List<IndexDetails> mysqlColumnDetails) {
        message.append("\n");
        message.append("There is no index on mysql!");
        message.append("\n");
        message.append("DB2 IndexName=").append(db2Index.getIndexName());
        message.append("\n");
        message.append("DB2 Columns=").append(db2Index.getColumnNames());
        message.append("\n");
        message.append("\n");
        message.append("All DB2 column pairs with Index:").append(db2ColumnDetails.stream().map(i -> "(" + i.getColumnNames() + ") ").toList());
        message.append("\n");
        message.append("All MYSQL column pairs with Index:").append(mysqlColumnDetails.stream().map(i -> "(" + i.getColumnNames() + ") ").toList());
        message.append("\n");
        message.append("\n");
    }

    private void appendIndexMismatchFailureMessage(IndexDetails db2Index, StringBuilder message) {
        message.append("\n");
        message.append("Unique value Mismatches! Index=").append(db2Index.getIndexName()).append("\t");
        message.append("DB2 Unique=").append(db2Index.getUnique()).append(" ");
        message.append("Mysql Unique=").append(db2Index.getUnique());
        message.append("\n");
    }


}
