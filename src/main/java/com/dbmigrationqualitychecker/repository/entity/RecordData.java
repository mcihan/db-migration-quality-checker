package com.dbmigrationqualitychecker.repository.entity;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;

@Data
@Builder
public class RecordData {
    private String id;
    private HashMap<String, String> columns;
}
