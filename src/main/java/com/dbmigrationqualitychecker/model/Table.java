package com.dbmigrationqualitychecker.model;

/**
 * A table to be checked, as read from {@code data/tables.csv}.
 *
 * @param tableName      name of the table (identical on both sides)
 * @param sourceSchema   schema name on the source DB
 * @param targetSchema   schema/database name on the target DB
 * @param idName         primary-key column, or {@code null} for row-wise matching
 * @param queryCondition optional {@code WHERE …} appended to the row-count query
 * @param hexId          {@code true} when PK is stored as {@code VARBINARY}/UUID and needs {@code lower(hex(id))}
 */
public record Table(
        String tableName,
        String sourceSchema,
        String targetSchema,
        String idName,
        String queryCondition,
        boolean hexId) {

    public Table {
        queryCondition = queryCondition == null ? "" : queryCondition;
    }
}
