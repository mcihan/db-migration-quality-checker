package com.dbmigrationqualitychecker.check;

import java.util.List;

/**
 * The result of running one {@link MigrationCheck} against one table. A
 * sealed hierarchy so callers can distinguish {@link Passed} vs {@link Failed}
 * with pattern matching, and the type system guarantees both cases are
 * handled.
 */
public sealed interface CheckOutcome permits CheckOutcome.Passed, CheckOutcome.Failed {

    String tableName();

    String description();

    List<String> queries();

    default boolean passed() {
        return this instanceof Passed;
    }

    default boolean failed() {
        return this instanceof Failed;
    }

    static CheckOutcome passed(String tableName, String description) {
        return new Passed(tableName, description, List.of());
    }

    static CheckOutcome passed(String tableName, String description, List<String> queries) {
        return new Passed(tableName, description, queries);
    }

    static CheckOutcome failed(String tableName, String description) {
        return new Failed(tableName, description, List.of(), "");
    }

    static CheckOutcome failed(String tableName, String description, List<String> queries) {
        return new Failed(tableName, description, queries, "");
    }

    static CheckOutcome failed(String tableName, String description, List<String> queries, String details) {
        return new Failed(tableName, description, queries, details);
    }

    record Passed(String tableName, String description, List<String> queries) implements CheckOutcome {}

    record Failed(String tableName, String description, List<String> queries, String failureDetails)
            implements CheckOutcome {}
}
