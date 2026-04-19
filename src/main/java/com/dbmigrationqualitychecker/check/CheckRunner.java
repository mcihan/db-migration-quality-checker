package com.dbmigrationqualitychecker.check;

import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.report.ReportWriter;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Orchestrator. Spring injects every {@link MigrationCheck} bean; the runner
 * picks those selected by {@link CheckSelection} and runs them in parallel,
 * handing each check's outcomes off to the {@link ReportWriter}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckRunner {

    private final List<MigrationCheck> checks;
    private final TableProvider tableProvider;
    private final CheckSelection selection;
    private final ReportWriter reportWriter;

    public void runAll() {
        Set<ReportType> selected = selection.selected();
        List<Table> tables = tableProvider.getTables();
        log.info("Running checks {} over {} tables", selected, tables.size());

        List<CompletableFuture<Void>> futures = checks.stream()
                .filter(check -> selected.contains(check.reportType()))
                .map(check -> CompletableFuture.runAsync(() -> runOne(check, tables)))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private void runOne(MigrationCheck check, List<Table> tables) {
        Instant start = Instant.now();
        log.info("{} start", check.reportType());
        List<CheckOutcome> outcomes = tables.parallelStream()
                .map(table -> safelyExecute(check, table))
                .toList();
        reportWriter.write(check.reportType(), start, Instant.now(), outcomes);
        log.info("{} end", check.reportType());
    }

    private CheckOutcome safelyExecute(MigrationCheck check, Table table) {
        try {
            log.debug("{} table={}", check.reportType(), table.tableName());
            return check.execute(table);
        } catch (Exception e) {
            log.error(
                    "{} aborted for table {}: {}", check.reportType(), table.tableName(), e.getMessage(), e);
            return CheckOutcome.failed(table.tableName(), "Check aborted: " + e.getMessage());
        }
    }
}
