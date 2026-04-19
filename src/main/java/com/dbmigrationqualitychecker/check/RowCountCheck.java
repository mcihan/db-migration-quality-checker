package com.dbmigrationqualitychecker.check;

import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class RowCountCheck implements MigrationCheck {

    private final DatabaseRepository source;
    private final DatabaseRepository target;

    public RowCountCheck(
            @Qualifier("sourceRepository") DatabaseRepository source,
            @Qualifier("targetRepository") DatabaseRepository target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public ReportType reportType() {
        return ReportType.TABLE_ROW_COUNT_COMPARISON;
    }

    @Override
    public CheckOutcome execute(Table table) {
        int sourceCount = source.getRowCount(table);
        int targetCount = target.getRowCount(table);

        String query = String.format(
                "select count(1) from %s.%s %s", table.sourceSchema(), table.tableName(), table.queryCondition());
        if (sourceCount == targetCount) {
            return CheckOutcome.passed(
                    table.tableName(),
                    String.format("Total data count matches. source: %s, target: %s", sourceCount, targetCount),
                    List.of(query));
        }
        String description = String.format(
                "Total data count does not match.%nsource:\t%s%ntarget:\t%s%n%nDiff:\t%s%n%s has more data.",
                sourceCount,
                targetCount,
                Math.abs(sourceCount - targetCount),
                sourceCount > targetCount ? "source" : "target");
        return CheckOutcome.failed(table.tableName(), description, List.of(query));
    }
}
