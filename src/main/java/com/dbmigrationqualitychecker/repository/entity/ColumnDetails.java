package com.dbmigrationqualitychecker.repository.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ColumnDetails {
    private String columnName;
    private String columnType;
    private String columnDefault;
    private boolean isNullable;
    private boolean isAutoIncrement;
}
