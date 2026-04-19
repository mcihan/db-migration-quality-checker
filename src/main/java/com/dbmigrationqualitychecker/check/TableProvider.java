package com.dbmigrationqualitychecker.check;

import com.dbmigrationqualitychecker.model.Table;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** Loads {@link Table} definitions from {@code data/tables.csv}. */
@Component
@Slf4j
public class TableProvider {

    private static final String DEFAULT_PATH = "data/tables.csv";

    public List<Table> getTables() {
        return readCsv(DEFAULT_PATH).orElseGet(() -> {
            log.info("tables.csv could not be found at {} — returning empty list", DEFAULT_PATH);
            return new ArrayList<>();
        });
    }

    public Optional<List<Table>> readCsv(String path) {
        File file = new File(path);
        if (!file.exists()) {
            log.info("Couldn't read table csv file at {}", path);
            return Optional.empty();
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            List<Table> tables = reader.lines()
                    .skip(1)
                    .map(line -> line.split(","))
                    .map(TableProvider::parseRow)
                    .toList();
            return Optional.of(tables);
        } catch (IOException e) {
            log.error("Failed to read table csv file: {}", path, e);
            return Optional.empty();
        }
    }

    private static Table parseRow(String[] columns) {
        return new Table(
                columns[2],
                columns[0],
                columns[1],
                columns.length > 3 ? StringUtils.trimToNull(columns[3]) : null,
                columns.length > 4 ? StringUtils.defaultIfBlank(columns[4], "") : "",
                columns.length > 5 && "true".equalsIgnoreCase(columns[5]));
    }
}
