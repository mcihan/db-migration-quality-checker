package com.dbmigrationqualitychecker.model;

public record IndexDetails(String tableName, String indexName, String columnNames, String unique) {}
