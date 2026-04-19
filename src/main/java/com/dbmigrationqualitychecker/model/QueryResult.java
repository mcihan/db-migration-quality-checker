package com.dbmigrationqualitychecker.model;

import java.util.List;

public record QueryResult<T>(String query, String tableName, List<T> result) {

    public static <T> QueryResult<T> of(String query, List<T> result) {
        return new QueryResult<>(query, null, result);
    }

    public static <T> QueryResult<T> of(String query, String tableName, List<T> result) {
        return new QueryResult<>(query, tableName, result);
    }
}
