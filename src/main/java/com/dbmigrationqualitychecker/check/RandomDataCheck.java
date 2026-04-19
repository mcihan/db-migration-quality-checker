package com.dbmigrationqualitychecker.check;

import com.dbmigrationqualitychecker.check.support.ValueComparator;
import com.dbmigrationqualitychecker.model.QueryResult;
import com.dbmigrationqualitychecker.model.RecordData;
import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class RandomDataCheck implements MigrationCheck {

    private final DatabaseRepository source;
    private final DatabaseRepository target;

    public RandomDataCheck(
            @Qualifier("sourceRepository") DatabaseRepository source,
            @Qualifier("targetRepository") DatabaseRepository target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public ReportType reportType() {
        return ReportType.RANDOM_DATA_COMPARISON;
    }

    @Override
    public CheckOutcome execute(Table table) {
        return StringUtils.isNotBlank(table.idName()) ? checkByPrimaryKey(table) : checkByFullRow(table);
    }

    private CheckOutcome checkByPrimaryKey(Table table) {
        QueryResult<RecordData> sourceResult = source.getRandomData(table);
        List<RecordData> sourceRows = sourceResult.result();
        if (sourceRows.isEmpty()) {
            return emptyDataOutcome(table);
        }

        List<String> ids = sourceRows.stream().map(RecordData::id).toList();
        QueryResult<RecordData> targetResult = target.findAllByIds(table, ids);
        List<RecordData> targetRows = targetResult.result();

        for (RecordData sourceRow : sourceRows) {
            Optional<RecordData> targetRow =
                    targetRows.stream().filter(r -> r.id().equals(sourceRow.id())).findFirst();

            if (targetRow.isEmpty()) {
                return CheckOutcome.failed(
                        table.tableName(),
                        "Data mismatch between source and target DB.",
                        List.of(lookupQuery(table, sourceRow.id())),
                        String.format("There is no corresponded data on target. ID:%s%n", sourceRow.id()));
            }

            String columnDiff = diffColumns(sourceRow, targetRow.get());
            if (!columnDiff.isEmpty()) {
                return CheckOutcome.failed(
                        table.tableName(),
                        "Data mismatch between source and target DB.",
                        List.of(lookupQuery(table, sourceRow.id())),
                        columnDiff);
            }
        }
        return CheckOutcome.passed(
                table.tableName(),
                String.format("%d Records between source and target checked and they matched!", sourceRows.size()));
    }

    private CheckOutcome checkByFullRow(Table table) {
        QueryResult<RecordData> sourceResult = source.getDataFromTable(table);
        if (sourceResult.result().isEmpty()) {
            return emptyDataOutcome(table);
        }

        for (RecordData sourceRow : sourceResult.result()) {
            QueryResult<RecordData> targetResult = target.findByColumns(table, sourceRow);
            if (targetResult.result().isEmpty()) {
                return CheckOutcome.failed(
                        table.tableName(),
                        "Corresponded record couldn't found on target DB.",
                        List.of(targetResult.query()));
            }
            if (targetResult.result().size() > 1) {
                return CheckOutcome.failed(
                        table.tableName(), "Multiple records founded on target DB.", List.of(targetResult.query()));
            }
        }
        return CheckOutcome.passed(
                table.tableName(),
                String.format(
                        "%d Records between source and target checked and they matched!",
                        sourceResult.result().size()));
    }

    private static String diffColumns(RecordData source, RecordData target) {
        StringBuilder diff = new StringBuilder();
        for (String key : source.columns().keySet()) {
            String sourceValue = source.columns().get(key);
            String targetValue = target.columns().get(key);
            if (ValueComparator.differ(sourceValue, targetValue)) {
                diff.append(String.format("Failed column: %s%n", key))
                        .append(String.format("on source:\t%s%n", sourceValue))
                        .append(String.format("on target:\t%s%n%n", targetValue));
            }
        }
        return diff.toString();
    }

    private static CheckOutcome emptyDataOutcome(Table table) {
        return CheckOutcome.passed(
                table.tableName(),
                "There is no data on both of source and target",
                List.of(String.format("SELECT * FROM %s;", table.tableName())));
    }

    private static String lookupQuery(Table table, String id) {
        return String.format("SELECT * FROM %s WHERE %s=%s;", table.tableName(), table.idName(), id);
    }
}
