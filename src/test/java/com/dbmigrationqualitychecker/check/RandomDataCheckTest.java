package com.dbmigrationqualitychecker.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.dbmigrationqualitychecker.model.QueryResult;
import com.dbmigrationqualitychecker.model.RecordData;
import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RandomDataCheckTest {

    @Mock
    DatabaseRepository source;

    @Mock
    DatabaseRepository target;

    private RecordData row(String id, String name) {
        Map<String, String> cols = new HashMap<>();
        cols.put("ID", id);
        cols.put("NAME", name);
        return new RecordData(id, cols);
    }

    private CheckOutcome run(Table t) {
        return new RandomDataCheck(source, target).execute(t);
    }

    @Test
    void pkPath_passesWhenRowsMatch() {
        Table t = new Table("T", "S", "M", "ID", "", false);
        when(source.getRandomData(t)).thenReturn(QueryResult.of("q", List.of(row("1", "A"))));
        when(target.findAllByIds(any(), any())).thenReturn(QueryResult.of("q", List.of(row("1", "A"))));

        assertThat(run(t).passed()).isTrue();
    }

    @Test
    void pkPath_failsWhenTargetMissingId() {
        Table t = new Table("T", "S", "M", "ID", "", false);
        when(source.getRandomData(t)).thenReturn(QueryResult.of("q", List.of(row("1", "A"))));
        when(target.findAllByIds(any(), any())).thenReturn(QueryResult.of("q", List.of()));

        CheckOutcome outcome = run(t);
        assertThat(outcome.failed()).isTrue();
        assertThat(((CheckOutcome.Failed) outcome).failureDetails())
                .contains("There is no corresponded data on target");
    }

    @Test
    void pkPath_failsOnColumnValueMismatch() {
        Table t = new Table("T", "S", "M", "ID", "", false);
        when(source.getRandomData(t)).thenReturn(QueryResult.of("q", List.of(row("1", "A"))));
        when(target.findAllByIds(any(), any())).thenReturn(QueryResult.of("q", List.of(row("1", "B"))));

        CheckOutcome outcome = run(t);
        assertThat(outcome.failed()).isTrue();
        assertThat(((CheckOutcome.Failed) outcome).failureDetails()).contains("Failed column: NAME");
    }

    @Test
    void pkPath_passesWhenSourceEmpty() {
        Table t = new Table("EMPTY", "S", "M", "ID", "", false);
        when(source.getRandomData(t)).thenReturn(QueryResult.of("q", List.of()));

        CheckOutcome outcome = run(t);
        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.description()).contains("There is no data on both of source and target");
    }

    @Test
    void fullRowPath_failsWhenTargetHasNoMatchingRow() {
        Table t = new Table("T", "S", "M", null, "", false);
        when(source.getDataFromTable(t)).thenReturn(QueryResult.of("q", List.of(row("1", "A"))));
        when(target.findByColumns(any(), any())).thenReturn(QueryResult.of("q", List.of()));

        assertThat(run(t).description()).contains("couldn't found on target DB");
    }

    @Test
    void fullRowPath_failsOnMultipleMatches() {
        Table t = new Table("T", "S", "M", null, "", false);
        when(source.getDataFromTable(t)).thenReturn(QueryResult.of("q", List.of(row("1", "A"))));
        when(target.findByColumns(any(), any()))
                .thenReturn(QueryResult.of("q", List.of(row("1", "A"), row("1", "A"))));

        assertThat(run(t).description()).contains("Multiple records founded");
    }
}
