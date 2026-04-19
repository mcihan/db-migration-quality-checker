package com.dbmigrationqualitychecker.check.support;

import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Rules for deciding whether a source column's type/default is "equivalent"
 * to the target's, even when the SQL strings differ between engines (e.g.
 * DB2 {@code CHARACTER} ↔ MySQL {@code CHAR}/{@code BINARY},
 * {@code TIMESTAMP(6)} ↔ {@code TIMESTAMP}, any {@code INT*} ↔ any {@code INT*}).
 */
public final class ColumnTypeCompatibility {

    private ColumnTypeCompatibility() {}

    public static boolean typesMatch(String source, String target) {
        if (source == null || target == null) {
            return Objects.equals(source, target);
        }
        return source.equals(target)
                || (source.contains("INT") && target.contains("INT"))
                || (source.contains("TIMESTAMP") && target.contains("TIMESTAMP"))
                || (source.equals("CHARACTER") && target.equals("CHAR"))
                || (source.equals("CHARACTER") && target.contains("BINARY"))
                || (source.equals("VARCHAR") && target.contains("BINARY"))
                || (source.contains("BIT DATA") && target.contains("BINARY"));
    }

    public static boolean defaultsMatch(String source, String target) {
        if (source == null && target == null) {
            return true;
        }
        if (source == null || target == null) {
            return false;
        }
        return StringUtils.equalsAnyIgnoreCase(source, target)
                || (source.contains("TIMESTAMP") && target.contains("TIMESTAMP"))
                || (source.equals("'0'") && target.equals("0X30"))
                || (source.equals("CHARACTER") && target.equals("VARBINARY"));
    }
}
