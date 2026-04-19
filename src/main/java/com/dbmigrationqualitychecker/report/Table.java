package com.dbmigrationqualitychecker.report;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Table {
    private String tableName;
    private String idName;
    private String id;
    private boolean isHexId;
    private String targetSchema;
    private String sourceSchema;
    private String queryCondition;
}
