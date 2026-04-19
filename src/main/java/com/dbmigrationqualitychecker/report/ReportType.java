package com.dbmigrationqualitychecker.report;

/**
 * One enum value per check type. The file name of each report is derived
 * directly from the enum name:
 * {@code report/<ENUM_NAME>_TEST_RESULT.txt}.
 */
public enum ReportType {
    COLUMN_NAME_COMPARISON,
    COLUMN_METADATA_COMPARISON,
    INDEX_COMPARISON,
    TABLE_ROW_COUNT_COMPARISON,
    RANDOM_DATA_COMPARISON
}
