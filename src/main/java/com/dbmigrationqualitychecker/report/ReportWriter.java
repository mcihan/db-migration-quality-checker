package com.dbmigrationqualitychecker.report;

import com.dbmigrationqualitychecker.check.CheckOutcome;
import java.time.Instant;
import java.util.List;

/**
 * Writes the outcome of one check type to a persistent report. Implementations
 * can choose any format (text file, JSON, HTML, stdout, …); the checker itself
 * is oblivious.
 */
public interface ReportWriter {

    void write(ReportType type, Instant startedAt, Instant endedAt, List<CheckOutcome> outcomes);
}
