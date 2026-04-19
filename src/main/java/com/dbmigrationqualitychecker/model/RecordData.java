package com.dbmigrationqualitychecker.model;

import java.util.Map;

public record RecordData(String id, Map<String, String> columns) {

    public static RecordData of(String id, Map<String, String> columns) {
        return new RecordData(id, columns);
    }

    public static RecordData of(Map<String, String> columns) {
        return new RecordData(null, columns);
    }
}
