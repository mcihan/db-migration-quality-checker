package com.dbmigrationqualitychecker.check;

import com.dbmigrationqualitychecker.check.support.ColumnTypeCompatibility;
import com.dbmigrationqualitychecker.model.ColumnDetails;
import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ColumnMetadataCheck implements MigrationCheck {

    private final DatabaseRepository source;
    private final DatabaseRepository target;

    public ColumnMetadataCheck(
            @Qualifier("sourceRepository") DatabaseRepository source,
            @Qualifier("targetRepository") DatabaseRepository target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public ReportType reportType() {
        return ReportType.COLUMN_METADATA_COMPARISON;
    }

    @Override
    public CheckOutcome execute(Table table) {
        List<ColumnDetails> sourceColumns = source.getColumnDetails(table);
        List<ColumnDetails> targetColumns = target.getColumnDetails(table);

        if (sourceColumns.isEmpty()) {
            return CheckOutcome.passed(table.tableName(), "There is no column");
        }

        StringBuilder message = new StringBuilder();
        boolean mismatch = false;
        for (ColumnDetails sourceColumn : sourceColumns) {
            Optional<ColumnDetails> match = targetColumns.stream()
                    .filter(c -> c.columnName().equals(sourceColumn.columnName()))
                    .findFirst();
            if (match.isEmpty()) {
                message.append(String.format("Column missing in target: %s%n", sourceColumn.columnName()));
                mismatch = true;
                continue;
            }
            mismatch |= appendAttributeMismatches(sourceColumn, match.get(), message);
        }

        return mismatch
                ? CheckOutcome.failed(table.tableName(), message.toString())
                : CheckOutcome.passed(table.tableName(), "Column metadata matches.");
    }

    private boolean appendAttributeMismatches(ColumnDetails source, ColumnDetails target, StringBuilder message) {
        StringBuilder column = new StringBuilder();
        boolean mismatch = false;

        if (!ColumnTypeCompatibility.defaultsMatch(source.columnDefault(), target.columnDefault())) {
            column.append(String.format(
                    " Default Value Mismatch! source: %s, target: %s%n",
                    source.columnDefault(), target.columnDefault()));
            mismatch = true;
        }
        if (!ColumnTypeCompatibility.typesMatch(source.columnType(), target.columnType())) {
            column.append(String.format(
                    " Column Type Mismatch! source: %s, target: %s%n",
                    source.columnType(), target.columnType()));
            mismatch = true;
        }
        if (source.nullable() != target.nullable()) {
            column.append(String.format(
                    " Nullability Mismatch! source: %s, target: %s%n",
                    source.nullable() ? "ALLOWS NULL" : "NOT NULL",
                    target.nullable() ? "ALLOWS NULL" : "NOT NULL"));
            mismatch = true;
        }
        if (source.autoIncrement() != target.autoIncrement()) {
            column.append(String.format(
                    " Auto Increment Mismatch! source: %s, target: %s%n",
                    source.autoIncrement(), target.autoIncrement()));
            mismatch = true;
        }

        if (mismatch) {
            message.append("COLUMN NAME= ").append(source.columnName()).append(column);
        }
        return mismatch;
    }
}
