package com.dbmigrationqualitychecker.check;

import com.dbmigrationqualitychecker.model.IndexDetails;
import com.dbmigrationqualitychecker.model.QueryResult;
import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class IndexCheck implements MigrationCheck {

    private final DatabaseRepository source;
    private final DatabaseRepository target;

    public IndexCheck(
            @Qualifier("sourceRepository") DatabaseRepository source,
            @Qualifier("targetRepository") DatabaseRepository target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public ReportType reportType() {
        return ReportType.INDEX_COMPARISON;
    }

    @Override
    public CheckOutcome execute(Table table) {
        QueryResult<IndexDetails> sourceResult = source.getIndexDetails(table);
        QueryResult<IndexDetails> targetResult = target.getIndexDetails(table);
        List<String> queries = List.of(sourceResult.query(), targetResult.query());

        List<IndexDetails> sourceIndexes = sourceResult.result();
        List<IndexDetails> targetIndexes = targetResult.result();

        StringBuilder message = new StringBuilder();
        boolean mismatch = false;
        if (sourceIndexes.isEmpty()) {
            message.append("[WARNING] : Index couldn't be checked, There is no record");
        } else {
            for (IndexDetails sourceIndex : sourceIndexes) {
                Optional<IndexDetails> match = targetIndexes.stream()
                        .filter(i -> i.columnNames().equals(sourceIndex.columnNames()))
                        .findFirst();
                if (match.isEmpty()) {
                    appendMissingIndex(message, sourceIndex, sourceIndexes, targetIndexes);
                    mismatch = true;
                    continue;
                }
                if (!StringUtils.equals(sourceIndex.unique(), match.get().unique())) {
                    appendUniqueMismatch(message, sourceIndex);
                    mismatch = true;
                }
            }
        }
        appendExtraTargetIndexes(message, sourceIndexes, targetIndexes);

        return mismatch
                ? CheckOutcome.failed(table.tableName(), message.toString(), queries)
                : CheckOutcome.passed(table.tableName(), "Indexes match.", queries);
    }

    private static void appendMissingIndex(
            StringBuilder message,
            IndexDetails sourceIndex,
            List<IndexDetails> sourceIndexes,
            List<IndexDetails> targetIndexes) {
        message.append('\n')
                .append("There is no index on target!")
                .append('\n')
                .append("source IndexName=")
                .append(sourceIndex.indexName())
                .append('\n')
                .append("source Columns=")
                .append(sourceIndex.columnNames())
                .append("\n\n")
                .append("All source column pairs with Index:")
                .append(sourceIndexes.stream()
                        .map(i -> "(" + i.columnNames() + ") ")
                        .toList())
                .append('\n')
                .append("All target column pairs with Index:")
                .append(targetIndexes.stream()
                        .map(i -> "(" + i.columnNames() + ") ")
                        .toList())
                .append("\n\n");
    }

    private static void appendUniqueMismatch(StringBuilder message, IndexDetails sourceIndex) {
        message.append('\n')
                .append("Unique value Mismatches! Index=")
                .append(sourceIndex.indexName())
                .append('\t')
                .append("source Unique=")
                .append(sourceIndex.unique())
                .append(' ')
                .append("target Unique=")
                .append(sourceIndex.unique())
                .append('\n');
    }

    private static void appendExtraTargetIndexes(
            StringBuilder message, List<IndexDetails> sourceIndexes, List<IndexDetails> targetIndexes) {
        if (sourceIndexes.isEmpty()) {
            return;
        }
        Set<String> seen = sourceIndexes.stream().map(IndexDetails::columnNames).collect(Collectors.toSet());
        StringBuilder extras = new StringBuilder();
        for (IndexDetails targetIndex : targetIndexes) {
            if (seen.add(targetIndex.columnNames())) {
                extras.append("IndexName=")
                        .append(targetIndex.indexName())
                        .append('\n')
                        .append("Columns=")
                        .append(targetIndex.columnNames())
                        .append("\n\n");
            }
        }
        if (!extras.isEmpty()) {
            message.append("\n[WARNING] : Extra indexes on target!\n").append(extras);
        }
    }
}
