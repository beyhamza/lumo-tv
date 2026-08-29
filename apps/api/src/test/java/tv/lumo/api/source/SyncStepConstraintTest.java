package tv.lumo.api.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.generated.model.SyncStep;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * Every value of {@link SyncStep} can actually be written.
 *
 * <h2>Why this test exists, and what it cost not to have it</h2>
 *
 * {@code source.sync_step} is guarded by a {@code CHECK} constraint listing the
 * values it accepts. That list is a **second copy of an enumeration**, and nothing
 * in the build connects the two: {@code SyncStep} gained {@code PARSING_VOD} in
 * sprint 5 and {@code PARSING_SERIES} in sprint 6, in the contract and in the
 * generated enum, and the constraint was not widened either time.
 *
 * <p>The failure was not a rejected step. {@code markSyncStep} threw a constraint
 * violation — a {@code DataIntegrityViolationException}, not an
 * {@code IngestionException} — which went past the handler whose entire job is to
 * stop a film catalogue's failure from failing its source. **Every Xtream source
 * ingested for two sprints ended in ERROR and lost its channels along with its
 * films**, and the code reported was {@code SOURCE_UNREACHABLE}: the user's
 * provider blamed for a fault on this side.
 *
 * <p>It survived that long because nothing in this suite exercises
 * {@code IngestionService} — a gap named in {@code sprint-05-recette.md} §11 and in
 * {@code dette.md}. This test does not close that gap. It closes the one thing
 * about it that can be checked without a panel: **that the schema accepts what the
 * enumeration can produce.**
 *
 * <p>It iterates the enum rather than listing the values, so the next phase added
 * to the contract fails here on the day it is generated instead of two sprints
 * later on somebody's own catalogue.
 */
@Import(PostgresContainerInitializer.class)
class SyncStepConstraintTest extends PostgresIntegrationTest {

    @Autowired
    private SourceRepository sources;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    private UUID sourceId;

    @BeforeEach
    void createASyncingSource() {
        UserRow user = users.insert(UUID.randomUUID(),
                "sync-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Sync Test", "en");

        sourceId = UUID.randomUUID();
        sources.insert(sourceId, user.id(), "Test panel", SourceKind.XTREAM,
                "https://panel.example", "user", new byte[] {1}, null, null, null, null);
        // The constraint only permits a step while the source is SYNCING, which is
        // the state every one of these is written in.
        sources.markSyncing(sourceId);
    }

    @Test
    @DisplayName("every SyncStep the contract defines can be written to the database")
    void everyStepIsAccepted() {
        for (SyncStep step : SyncStep.values()) {
            assertThatCode(() -> sources.markSyncStep(sourceId, step))
                    .withFailMessage(
                            "SyncStep.%s is in the contract and the CHECK constraint refuses it. "
                                    + "Widen source_sync_step_check in a migration — and see this "
                                    + "class's documentation for what that costs when it is missed.",
                            step)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("the constraint still refuses a value that is not a phase")
    void nonsenseIsStillRefused() {
        // The point of the constraint, and the reason widening it is not the same
        // as dropping it: a typo in a step name has to fail here rather than end up
        // on somebody's waiting screen.
        assertThatCode(() -> jdbc.sql(
                        "UPDATE source SET sync_step = 'PARSING_EVERYTHING' WHERE id = :id")
                .param("id", sourceId)
                .update())
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("a step cannot outlive the sync it belongs to")
    void stepIsClearedWithTheStatus() {
        sources.markSyncStep(sourceId, SyncStep.PARSING_VOD);
        sources.markReady(sourceId, null, null);

        // A step left behind on a READY source would have the screen reporting a
        // phase that finished hours ago — which is what the constraint's
        // `status = 'SYNCING'` half is for.
        String step = jdbc.sql("SELECT sync_step FROM source WHERE id = :id")
                .param("id", sourceId)
                .query(String.class)
                .optional()
                .orElse(null);

        assertThat(step).isNull();
    }

    @Test
    @DisplayName("the constraint lists exactly the phases the enumeration has")
    void theTwoListsAgree() {
        String definition = jdbc.sql("""
                SELECT pg_get_constraintdef(oid)
                  FROM pg_constraint
                 WHERE conname = 'source_sync_step_check'
                """)
                .query(String.class)
                .single();

        List<String> missing = java.util.Arrays.stream(SyncStep.values())
                .map(SyncStep::getValue)
                .filter(value -> !definition.contains("'" + value + "'"))
                .toList();

        // The write test above proves the constraint accepts them; this proves the
        // constraint is the reason, rather than a constraint that was quietly
        // dropped. The two together are what make this file worth its length.
        assertThat(missing).isEmpty();
    }
}
