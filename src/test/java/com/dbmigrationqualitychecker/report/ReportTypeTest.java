package com.dbmigrationqualitychecker.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReportTypeTest {

    @Test
    void enumHasExactlyTheFiveExpectedCheckKinds() {
        assertThat(ReportType.values())
                .containsExactlyInAnyOrder(
                        ReportType.COLUMN_NAME_COMPARISON,
                        ReportType.COLUMN_METADATA_COMPARISON,
                        ReportType.INDEX_COMPARISON,
                        ReportType.TABLE_ROW_COUNT_COMPARISON,
                        ReportType.RANDOM_DATA_COMPARISON);
    }
}
