package com.dbmigrationqualitychecker.report;

import com.dbmigrationqualitychecker.repository.entity.RecordData;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReportDetail {
    private String query;
    private String tableName;
    private List<RecordData> records;
    private List<String> columnNames;
}
