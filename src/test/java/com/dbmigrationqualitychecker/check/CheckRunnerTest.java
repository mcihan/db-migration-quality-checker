package com.dbmigrationqualitychecker.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.report.ReportType;
import com.dbmigrationqualitychecker.report.ReportWriter;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckRunnerTest {

    @Mock
    TableProvider tableProvider;

    @Mock
    CheckSelection selection;

    @Mock
    ReportWriter reportWriter;

    @Test
    void runsOnlySelectedChecksAndPassesOutcomesToWriter() {
        Table t = new Table("T", "S", "M", null, "", false);
        when(tableProvider.getTables()).thenReturn(List.of(t));
        when(selection.selected()).thenReturn(EnumSet.of(ReportType.COLUMN_NAME_COMPARISON));

        MigrationCheck included = stub(ReportType.COLUMN_NAME_COMPARISON, CheckOutcome.passed("T", "ok"));
        MigrationCheck excluded = stub(ReportType.INDEX_COMPARISON, CheckOutcome.passed("T", "unused"));

        new CheckRunner(List.of(included, excluded), tableProvider, selection, reportWriter).runAll();

        verify(reportWriter)
                .write(eq(ReportType.COLUMN_NAME_COMPARISON), any(Instant.class), any(Instant.class), any());
    }

    @Test
    void convertsThrownExceptionsIntoFailedOutcomes() {
        Table t = new Table("BAD", "S", "M", null, "", false);
        when(tableProvider.getTables()).thenReturn(List.of(t));
        when(selection.selected()).thenReturn(EnumSet.of(ReportType.TABLE_ROW_COUNT_COMPARISON));

        MigrationCheck crashing = new MigrationCheck() {
            @Override
            public ReportType reportType() {
                return ReportType.TABLE_ROW_COUNT_COMPARISON;
            }

            @Override
            public CheckOutcome execute(Table table) {
                throw new RuntimeException("boom");
            }
        };

        new CheckRunner(List.of(crashing), tableProvider, selection, reportWriter).runAll();

        ArgumentCaptor<List<CheckOutcome>> captor = ArgumentCaptor.forClass(List.class);
        verify(reportWriter)
                .write(
                        eq(ReportType.TABLE_ROW_COUNT_COMPARISON),
                        any(Instant.class),
                        any(Instant.class),
                        captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).failed()).isTrue();
        assertThat(captor.getValue().get(0).description()).contains("boom");
    }

    private static MigrationCheck stub(ReportType type, CheckOutcome outcome) {
        return new MigrationCheck() {
            @Override
            public ReportType reportType() {
                return type;
            }

            @Override
            public CheckOutcome execute(Table table) {
                return outcome;
            }
        };
    }
}
