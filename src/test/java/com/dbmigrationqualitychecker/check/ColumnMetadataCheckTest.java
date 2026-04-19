package com.dbmigrationqualitychecker.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.dbmigrationqualitychecker.model.ColumnDetails;
import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ColumnMetadataCheckTest {

    @Mock
    DatabaseRepository source;

    @Mock
    DatabaseRepository target;

    private final Table table = new Table("T", "S", "M", null, "", false);

    private ColumnDetails col(String name, String type, String def, boolean nullable, boolean auto) {
        return new ColumnDetails(name, type, def, nullable, auto);
    }

    private CheckOutcome run() {
        return new ColumnMetadataCheck(source, target).execute(table);
    }

    @Test
    void passesWhenColumnsIdentical() {
        ColumnDetails c = col("ID", "INT", null, false, true);
        when(source.getColumnDetails(table)).thenReturn(List.of(c));
        when(target.getColumnDetails(table)).thenReturn(List.of(c));

        assertThat(run().passed()).isTrue();
    }

    @Test
    void passesWhenTypesAreCompatibleAcrossEngines() {
        when(source.getColumnDetails(table)).thenReturn(List.of(col("UID", "CHARACTER", null, true, false)));
        when(target.getColumnDetails(table)).thenReturn(List.of(col("UID", "VARBINARY", null, true, false)));

        assertThat(run().passed()).isTrue();
    }

    @Test
    void failsOnTypeMismatch() {
        when(source.getColumnDetails(table)).thenReturn(List.of(col("C", "VARCHAR", null, true, false)));
        when(target.getColumnDetails(table)).thenReturn(List.of(col("C", "DECIMAL", null, true, false)));

        CheckOutcome outcome = run();
        assertThat(outcome.failed()).isTrue();
        assertThat(outcome.description()).contains("Column Type Mismatch");
    }

    @Test
    void failsOnNullabilityMismatch() {
        when(source.getColumnDetails(table)).thenReturn(List.of(col("C", "INT", null, true, false)));
        when(target.getColumnDetails(table)).thenReturn(List.of(col("C", "INT", null, false, false)));

        assertThat(run().description()).contains("Nullability Mismatch");
    }

    @Test
    void failsOnAutoIncrementMismatch() {
        when(source.getColumnDetails(table)).thenReturn(List.of(col("ID", "INT", null, false, true)));
        when(target.getColumnDetails(table)).thenReturn(List.of(col("ID", "INT", null, false, false)));

        assertThat(run().description()).contains("Auto Increment Mismatch");
    }

    @Test
    void failsOnDefaultValueMismatch() {
        when(source.getColumnDetails(table)).thenReturn(List.of(col("ST", "VARCHAR", "X", false, false)));
        when(target.getColumnDetails(table)).thenReturn(List.of(col("ST", "VARCHAR", "Y", false, false)));

        assertThat(run().description()).contains("Default Value Mismatch");
    }

    @Test
    void failsWhenColumnMissingInTarget() {
        when(source.getColumnDetails(table)).thenReturn(List.of(col("C", "INT", null, true, false)));
        when(target.getColumnDetails(table)).thenReturn(List.of());

        CheckOutcome outcome = run();
        assertThat(outcome.failed()).isTrue();
        assertThat(outcome.description()).contains("Column missing in target");
    }

    @Test
    void passesWithNoColumnsOnSource() {
        when(source.getColumnDetails(table)).thenReturn(List.of());
        when(target.getColumnDetails(table)).thenReturn(List.of());

        CheckOutcome outcome = run();
        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.description()).contains("no column");
    }
}
