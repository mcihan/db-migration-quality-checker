package com.dbmigrationqualitychecker.model;

public record ColumnDetails(
        String columnName,
        String columnType,
        String columnDefault,
        boolean nullable,
        boolean autoIncrement) {}
