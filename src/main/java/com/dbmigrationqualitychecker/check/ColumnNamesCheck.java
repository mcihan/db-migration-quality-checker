package com.dbmigrationqualitychecker.check;

import com.dbmigrationqualitychecker.model.QueryResult;
import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ColumnNamesCheck implements MigrationCheck {

    private final DatabaseRepository source;
    private final DatabaseRepository target;

    public ColumnNamesCheck(
            @Qualifier("sourceRepository") DatabaseRepository source,
            @Qualifier("targetRepository") DatabaseRepository target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public ReportType reportType() {
        return ReportType.COLUMN_NAME_COMPARISON;
    }

    @Override
    public CheckOutcome execute(Table table) {
        QueryResult<String> sourceResult = source.getColumnNames(table);
        QueryResult<String> targetResult = target.getColumnNames(table);
        List<String> queries = List.of(sourceResult.query(), targetResult.query());

        if (sourceResult.result().equals(targetResult.result())) {
            return CheckOutcome.passed(table.tableName(), "Column names match.", queries);
        }
        String message = String.format(
                "Column names do not match!%n\tsource columns: %s%n\ttarget columns: %s",
                sourceResult.result(), targetResult.result());
        return CheckOutcome.failed(table.tableName(), message, queries);
    }
}
