package com.dbmigrationqualitychecker;

import com.dbmigrationqualitychecker.report.Table;
import com.dbmigrationqualitychecker.service.ColumnMetadataComparisonService;
import com.dbmigrationqualitychecker.service.ColumnNameComparisonService;
import com.dbmigrationqualitychecker.service.IndexComparisonService;
import com.dbmigrationqualitychecker.service.RandomDataComparisonService;
import com.dbmigrationqualitychecker.service.TableProvider;
import com.dbmigrationqualitychecker.service.TableRowCountComparisonService;
import com.dbmigrationqualitychecker.util.ReportUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class DbMigrationQualityCheckerApplication {
    private final ColumnNameComparisonService columnNameComparisonService;
    private final ColumnMetadataComparisonService columnMetadataComparisonService;
    private final IndexComparisonService indexComparisonService;
    private final TableRowCountComparisonService tableRowCountComparisonService;
    private final RandomDataComparisonService randomDataComparisonService;
    private final TableProvider tableProvider;

    @Value("${config.only-row-count}")
    private boolean isOnlyRowCount;

    @Value("${config.only-random-data}")
    private boolean isOnlyRandomData;

    @Value("${config.only-column-metadata}")
    private boolean isOnlyColumnMetadata;

    public static void main(String[] args) {
        SpringApplication.run(DbMigrationQualityCheckerApplication.class, args);
    }

    @Bean
    public CommandLineRunner compare() {
        return args -> {
            Instant start = Instant.now();
            List<Table> tables = tableProvider.getTables();
            log.info("ALL LOADED TABLES:" + tables);
            if (isOnlyRowCount) {
                CompletableFuture<Void> tableRowCountFuture = CompletableFuture.runAsync(() -> tableRowCountComparisonService.findDiff());
                tableRowCountFuture.join();
            } else if (isOnlyRandomData) {
                CompletableFuture<Void> randomDataFuture = CompletableFuture.runAsync(() -> randomDataComparisonService.findDiff());
                randomDataFuture.join();
            } else if (isOnlyColumnMetadata) {
                CompletableFuture<Void> columMetadataFuture = CompletableFuture.runAsync(() -> columnMetadataComparisonService.findDiff());
                columMetadataFuture.join();
            } else {
                CompletableFuture<Void> columNameFuture = CompletableFuture.runAsync(() -> columnNameComparisonService.findDiff());
                CompletableFuture<Void> columMetadataFuture = CompletableFuture.runAsync(() -> columnMetadataComparisonService.findDiff());
                CompletableFuture<Void> indexFuture = CompletableFuture.runAsync(() -> indexComparisonService.findDiff());
                CompletableFuture<Void> tableRowCountFuture = CompletableFuture.runAsync(() -> tableRowCountComparisonService.findDiff());
                CompletableFuture<Void> randomDataFuture = CompletableFuture.runAsync(() -> randomDataComparisonService.findDiff());
                CompletableFuture.allOf(columNameFuture, columMetadataFuture, indexFuture, tableRowCountFuture, randomDataFuture).join();
            }
            String duration = ReportUtil.formatDuration(start, Instant.now());
            log.info("***********************************************");
            log.info("ALL TEST HAVE BEEN COMPLETED! Duration: {}", duration);
            log.info("***********************************************");
        };
    }

}
