package com.dbmigrationqualitychecker.service;

import static com.dbmigrationqualitychecker.report.ReportType.INDEX_COMPARISON;

import com.dbmigrationqualitychecker.report.QueryResult;
import com.dbmigrationqualitychecker.report.ReportService;
import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import com.dbmigrationqualitychecker.repository.entity.IndexDetails;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexComparisonService extends ComparisonServiceBase {

    private final DatabaseRepository sourceRepository;
    private final DatabaseRepository targetRepository;
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
        QueryResult<IndexDetails> sourceResult = sourceRepository.getIndexDetails(table);
        QueryResult<IndexDetails> targetResult = targetRepository.getIndexDetails(table);

        List<IndexDetails> sourceIndexes = sourceResult.getResult();
        List<IndexDetails> targetIndexes = targetResult.getResult();

        boolean isFailed = false;
        StringBuilder message = new StringBuilder();
        if (!sourceIndexes.isEmpty()) {
            for (IndexDetails sourceIndex : sourceIndexes) {
                String sourceColumnNames = sourceIndex.getColumnNames();
                Optional<IndexDetails> targetOpt = targetIndexes.stream()
                        .filter(c -> c.getColumnNames().equals(sourceColumnNames))
                        .findFirst();
                if (targetOpt.isPresent()) {
                    boolean isIndexColumnsMatch =
                            StringUtils.equals(sourceIndex.getColumnNames(), targetOpt.get().getColumnNames());
                    boolean isUniqueEquals =
                            StringUtils.equals(sourceIndex.getUnique(), targetOpt.get().getUnique());

                    if (!isIndexColumnsMatch) {
                        message.append("\n")
                                .append("Mismatches! Index=")
                                .append(sourceIndex.getIndexName())
                                .append("\n");
                        isFailed = true;
                    } else if (!isUniqueEquals) {
                        appendIndexMismatchFailureMessage(sourceIndex, message);
                        isFailed = true;
                    }
                } else {
                    appendNoIndexFailureMessage(sourceIndex, message, sourceIndexes, targetIndexes);
                    isFailed = true;
                }
            }
        } else {
            message.append("[WARNING] : Index couldn't be checked, There is no record");
        }

        reportExtraIndexesOnTarget(sourceIndexes, targetIndexes, message);

        if (isFailed) {
            List<String> queries = List.of(sourceResult.getQuery(), targetResult.getQuery());
            ReportService.writeFailedResult(INDEX_COMPARISON, table.getTableName(), message.toString(), queries);
            return false;
        }
        return true;
    }

    private void reportExtraIndexesOnTarget(
            List<IndexDetails> sourceIndexes, List<IndexDetails> targetIndexes, StringBuilder message) {
        if (sourceIndexes.isEmpty()) {
            return;
        }
        StringBuilder extraIndexMessage = new StringBuilder();
        Set<String> sourceIndexColumns =
                sourceIndexes.stream().map(IndexDetails::getColumnNames).collect(Collectors.toSet());
        boolean extraFound = false;
        for (IndexDetails targetIndex : targetIndexes) {
            if (sourceIndexColumns.add(targetIndex.getColumnNames())) {
                extraIndexMessage
                        .append("IndexName=")
                        .append(targetIndex.getIndexName())
                        .append("\n");
                extraIndexMessage
                        .append("Columns=")
                        .append(targetIndex.getColumnNames())
                        .append("\n\n");
                extraFound = true;
            }
        }
        if (extraFound) {
            extraIndexMessage.insert(0, "\n[WARNING] : Extra indexes on target!\n");
            message.append(extraIndexMessage);
        }
    }

    private void appendNoIndexFailureMessage(
            IndexDetails sourceIndex,
            StringBuilder message,
            List<IndexDetails> sourceIndexes,
            List<IndexDetails> targetIndexes) {
        message.append("\n");
        message.append("There is no index on target!");
        message.append("\n");
        message.append("source IndexName=").append(sourceIndex.getIndexName());
        message.append("\n");
        message.append("source Columns=").append(sourceIndex.getColumnNames());
        message.append("\n\n");
        message.append("All source column pairs with Index:")
                .append(sourceIndexes.stream()
                        .map(i -> "(" + i.getColumnNames() + ") ")
                        .toList());
        message.append("\n");
        message.append("All target column pairs with Index:")
                .append(targetIndexes.stream()
                        .map(i -> "(" + i.getColumnNames() + ") ")
                        .toList());
        message.append("\n\n");
    }

    private void appendIndexMismatchFailureMessage(IndexDetails sourceIndex, StringBuilder message) {
        message.append("\n");
        message.append("Unique value Mismatches! Index=")
                .append(sourceIndex.getIndexName())
                .append("\t");
        message.append("source Unique=").append(sourceIndex.getUnique()).append(" ");
        message.append("target Unique=").append(sourceIndex.getUnique());
        message.append("\n");
    }
}
