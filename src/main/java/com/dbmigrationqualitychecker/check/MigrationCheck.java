package com.dbmigrationqualitychecker.check;

import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.report.ReportType;

/**
 * One quality check. Stateless, side-effect free: given a table, produce an
 * outcome. The orchestrator ({@link CheckRunner}) handles iteration and
 * persistence of the result.
 */
public interface MigrationCheck {

    /** The report this check contributes to. */
    ReportType reportType();

    /** Run the check for a single table. Must never return {@code null}. */
    CheckOutcome execute(Table table);
}
