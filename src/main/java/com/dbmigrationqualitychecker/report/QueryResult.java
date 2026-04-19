package com.dbmigrationqualitychecker.report;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QueryResult<T> {
    private String query;
    private String tableName;
    private List<T> result;
}
