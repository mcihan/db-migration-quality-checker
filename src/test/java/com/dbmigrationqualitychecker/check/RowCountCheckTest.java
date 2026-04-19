package com.dbmigrationqualitychecker.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RowCountCheckTest {

    @Mock
    DatabaseRepository source;

    @Mock
    DatabaseRepository target;

    private CheckOutcome run(Table t) {
        return new RowCountCheck(source, target).execute(t);
    }

    @Test
    void passesWhenCountsMatch() {
        Table t = new Table("T", "S", "M", null, "", false);
        when(source.getRowCount(t)).thenReturn(42);
        when(target.getRowCount(t)).thenReturn(42);

        CheckOutcome outcome = run(t);
        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.description()).contains("matches", "source: 42", "target: 42");
    }

    @Test
    void failsSayingSourceHasMoreData() {
        Table t = new Table("T", "S", "M", null, "", false);
        when(source.getRowCount(t)).thenReturn(100);
        when(target.getRowCount(t)).thenReturn(95);

        CheckOutcome outcome = run(t);
        assertThat(outcome.failed()).isTrue();
        assertThat(outcome.description()).contains("source has more data", "Diff:\t5");
    }

    @Test
    void failsSayingTargetHasMoreData() {
        Table t = new Table("T", "S", "M", null, "", false);
        when(source.getRowCount(t)).thenReturn(10);
        when(target.getRowCount(t)).thenReturn(20);

        assertThat(run(t).description()).contains("target has more data");
    }
}
