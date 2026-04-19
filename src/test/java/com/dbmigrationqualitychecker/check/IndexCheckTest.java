package com.dbmigrationqualitychecker.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.dbmigrationqualitychecker.model.IndexDetails;
import com.dbmigrationqualitychecker.model.QueryResult;
import com.dbmigrationqualitychecker.model.Table;
import com.dbmigrationqualitychecker.repository.DatabaseRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexCheckTest {

    @Mock
    DatabaseRepository source;

    @Mock
    DatabaseRepository target;

    private final Table table = new Table("T", "S", "M", null, "", false);

    private IndexDetails idx(String name, String cols, String unique) {
        return new IndexDetails("T", name, cols, unique);
    }

    private CheckOutcome run() {
        return new IndexCheck(source, target).execute(table);
    }

    @Test
    void passesWhenIndexesMatch() {
        when(source.getIndexDetails(table)).thenReturn(QueryResult.of("q", List.of(idx("PK", "ID", "0"))));
        when(target.getIndexDetails(table)).thenReturn(QueryResult.of("q", List.of(idx("PRIMARY", "ID", "0"))));

        assertThat(run().passed()).isTrue();
    }

    @Test
    void failsWhenIndexMissingOnTarget() {
        when(source.getIndexDetails(table)).thenReturn(QueryResult.of("q", List.of(idx("IDX_A", "A,B", "0"))));
        when(target.getIndexDetails(table)).thenReturn(QueryResult.of("q", List.of()));

        CheckOutcome outcome = run();
        assertThat(outcome.failed()).isTrue();
        assertThat(outcome.description()).contains("There is no index on target");
    }

    @Test
    void warnsAboutExtraIndexesOnTargetWhenAlreadyFailing() {
        when(source.getIndexDetails(table))
                .thenReturn(QueryResult.of("q", List.of(idx("IDX_MISSING", "CODE", "0"))));
        when(target.getIndexDetails(table))
                .thenReturn(QueryResult.of("q", List.of(idx("IDX_EXTRA", "NAME", "1"))));

        CheckOutcome outcome = run();
        assertThat(outcome.failed()).isTrue();
        assertThat(outcome.description())
                .contains("Extra indexes on target", "IDX_EXTRA", "There is no index on target");
    }

    @Test
    void passesWhenSourceHasNoIndexes() {
        when(source.getIndexDetails(table)).thenReturn(QueryResult.of("q", List.of()));
        when(target.getIndexDetails(table)).thenReturn(QueryResult.of("q", List.of()));

        assertThat(run().passed()).isTrue();
    }
}
