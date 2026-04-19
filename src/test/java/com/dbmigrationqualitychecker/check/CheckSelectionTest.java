package com.dbmigrationqualitychecker.check;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbmigrationqualitychecker.report.ReportType;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class CheckSelectionTest {

    @Test
    void defaultSelectionIsEveryReportType() {
        assertThat(new CheckSelection(false, false, false).selected()).isEqualTo(EnumSet.allOf(ReportType.class));
    }

    @Test
    void onlyRowCountFlagNarrowsToOne() {
        assertThat(new CheckSelection(true, false, false).selected())
                .containsExactly(ReportType.TABLE_ROW_COUNT_COMPARISON);
    }

    @Test
    void rowCountWinsWhenMultipleFlagsSet() {
        assertThat(new CheckSelection(true, true, true).selected())
                .containsExactly(ReportType.TABLE_ROW_COUNT_COMPARISON);
    }

    @Test
    void onlyRandomDataFlag() {
        assertThat(new CheckSelection(false, true, false).selected())
                .containsExactly(ReportType.RANDOM_DATA_COMPARISON);
    }

    @Test
    void onlyColumnMetadataFlag() {
        assertThat(new CheckSelection(false, false, true).selected())
                .containsExactly(ReportType.COLUMN_METADATA_COMPARISON);
    }
}
