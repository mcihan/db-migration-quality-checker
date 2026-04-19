package com.dbmigrationqualitychecker.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.dbmigrationqualitychecker.model.QueryResult;
import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ColumnNamesCheckTest {

    @Mock
    DatabaseRepository source;

    @Mock
    DatabaseRepository target;

    private final Table table = new Table("PAY_ACCOUNT", "S", "M", null, "", false);

    @Test
    void passesWhenColumnListsMatch() {
        when(source.getColumnNames(table)).thenReturn(QueryResult.of("q1", List.of("A", "B", "C")));
        when(target.getColumnNames(table)).thenReturn(QueryResult.of("q2", List.of("A", "B", "C")));

        CheckOutcome outcome = new ColumnNamesCheck(source, target).execute(table);

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.description()).contains("Column names match");
        assertThat(outcome.queries()).containsExactly("q1", "q2");
    }

    @Test
    void failsWhenColumnListsDiffer() {
        when(source.getColumnNames(table)).thenReturn(QueryResult.of("q1", List.of("A", "B")));
        when(target.getColumnNames(table)).thenReturn(QueryResult.of("q2", List.of("A", "C")));

        CheckOutcome outcome = new ColumnNamesCheck(source, target).execute(table);

        assertThat(outcome.failed()).isTrue();
        assertThat(outcome.description()).contains("Column names do not match");
    }
}
