package com.dbmigrationqualitychecker.service;

import com.dbmigrationqualitychecker.report.Table;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class TableProvider {

    public List<Table> getTables() {
        Optional<List<Table>> tablesOpt = readCsv("data/tables.csv");
        if (tablesOpt.isPresent()) {
            return tablesOpt.get();
        }

        log.info("tables.csv could not be found! Please provide it!");
        return new ArrayList<>();
    }

    public Optional<List<Table>> readCsv(String path) {
        File csvFile = new File(path);
        if (!csvFile.exists()) {
            log.info("Couldn't read table csv file!");
            return Optional.empty();
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            return Optional.of(reader.lines()
                    .skip(1)
                    .map(line -> line.split(","))
                    .map(columns -> Table.builder()
                            .sourceSchema(columns[0])
                            .targetSchema(columns[1])
                            .tableName(columns[2])
                            .idName(columns.length > 3 ? columns[3] : null)
                            .queryCondition(getQueryCondition(columns))
                            .isHexId(columns.length > 5 ? "true".equalsIgnoreCase(columns[5]) : false)
                            .build()).toList());
        } catch (IOException e) {
            log.error("Failed to read table csv file: {}", path, e);
        }
        return Optional.empty();
    }

    private static String getQueryCondition(String[] columns) {
        return columns.length > 4 ? StringUtils.defaultIfBlank(columns[4], "") : "";
    }

}
