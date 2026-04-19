package com.dbmigrationqualitychecker.check;

import com.dbmigrationqualitychecker.report.ReportType;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides which {@link ReportType}s should run for this invocation, based on
 * the {@code config.only-*} flags. At most one flag is expected to be true;
 * if multiple are set the first match wins (row-count → random-data →
 * column-metadata), preserving historical behaviour.
 */
@Component
public class CheckSelection {

    private final boolean onlyRowCount;
    private final boolean onlyRandomData;
    private final boolean onlyColumnMetadata;

    public CheckSelection(
            @Value("${config.only-row-count}") boolean onlyRowCount,
            @Value("${config.only-random-data}") boolean onlyRandomData,
            @Value("${config.only-column-metadata}") boolean onlyColumnMetadata) {
        this.onlyRowCount = onlyRowCount;
        this.onlyRandomData = onlyRandomData;
        this.onlyColumnMetadata = onlyColumnMetadata;
    }

    public Set<ReportType> selected() {
        if (onlyRowCount) {
            return EnumSet.of(ReportType.TABLE_ROW_COUNT_COMPARISON);
        }
        if (onlyRandomData) {
            return EnumSet.of(ReportType.RANDOM_DATA_COMPARISON);
        }
        if (onlyColumnMetadata) {
            return EnumSet.of(ReportType.COLUMN_METADATA_COMPARISON);
        }
        return EnumSet.allOf(ReportType.class);
    }
}
