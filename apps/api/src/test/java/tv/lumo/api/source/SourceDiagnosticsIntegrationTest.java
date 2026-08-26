package tv.lumo.api.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.generated.model.Source;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.generated.model.SourceStatus;
import tv.lumo.api.generated.model.SyncStep;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * What a source says about itself while it works and after it fails.
 *
 * <p>Three properties the mobile and television canvases need, and one invariant
 * that keeps them honest: <b>{@code sync_step} is non-null only while
 * {@code SYNCING}</b>. That is enforced by a CHECK constraint rather than by
 * convention, because the failure it prevents is a screen showing "fetching the
 * guide" next to a source that failed an hour ago — and nothing in the Java would
 * have caught a writer that forgot to clear it.
 */
@Import(PostgresContainerInitializer.class)
class SourceDiagnosticsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private SourceRepository sources;

    @Autowired
    private SourceService sourceService;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    private UserRow user;
    private UUID sourceId;

    @BeforeEach
    void createSource() {
        user = users.insert(UUID.randomUUID(),
                "diagnostics-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Diagnostics Test", "en");

        sourceId = UUID.randomUUID();
        sources.insert(sourceId, user.id(), "Test source", SourceKind.M3U_URL, null, null, null,
                "https://playlist.example/one.m3u", null, null, null);
    }

    @Test
    @DisplayName("claiming a source opens at CONNECTING, and the step advances")
    void theStepAdvancesWhileSyncing() {
        assertThat(sources.markSyncing(sourceId)).isTrue();
        assertThat(sourceService.getOwned(sourceId, user.id()).getSyncStep())
                .isEqualTo(SyncStep.CONNECTING);

        sources.markSyncStep(sourceId, SyncStep.PARSING_CHANNELS);
        Source syncing = sourceService.getOwned(sourceId, user.id());
        assertThat(syncing.getStatus()).isEqualTo(SourceStatus.SYNCING);
        assertThat(syncing.getSyncStep()).isEqualTo(SyncStep.PARSING_CHANNELS);
    }

    @Test
    @DisplayName("a failure clears the step and dates itself")
    void failureClearsTheStepAndDatesItself() {
        sources.markSyncing(sourceId);
        sources.markSyncStep(sourceId, SyncStep.FETCHING_EPG);

        sources.markError(sourceId, IngestionErrorCode.SOURCE_AUTH_FAILED);

        Source failed = sourceService.getOwned(sourceId, user.id());
        assertThat(failed.getStatus()).isEqualTo(SourceStatus.ERROR);
        assertThat(failed.getErrorCode()).isEqualTo(IngestionErrorCode.SOURCE_AUTH_FAILED);
        // "Credentials refused SINCE YESTERDAY". The age is what makes the
        // message actionable, and last_synced_at cannot carry it.
        assertThat(failed.getLastErrorAt()).isNotNull();
        assertThat(failed.getLastSyncedAt()).isNull();
        assertThat(failed.getSyncStep()).isNull();
    }

    @Test
    @DisplayName("a success clears the error and its date, and counts the catalogue")
    void successClearsTheErrorAndCounts() {
        sources.markSyncing(sourceId);
        sources.markError(sourceId, IngestionErrorCode.SOURCE_UNREACHABLE);

        UUID categoryId = insertCategory();
        insertChannel(categoryId);
        insertChannel(categoryId);

        sources.markSyncing(sourceId);
        sources.markReady(sourceId, null, null);

        Source ready = sourceService.getOwned(sourceId, user.id());
        assertThat(ready.getStatus()).isEqualTo(SourceStatus.READY);
        assertThat(ready.getErrorCode()).isNull();
        assertThat(ready.getLastErrorAt()).isNull();
        assertThat(ready.getSyncStep()).isNull();
        // "2 chaînes · 1 catégorie" — half of that line was already available;
        // this is the other half.
        assertThat(ready.getChannelCount()).isEqualTo(2);
        assertThat(ready.getCategoryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the database refuses a step on a source that is not syncing")
    void aStepCannotOutliveItsIngestion() {
        // The invariant, tested where it lives. A writer that set the status
        // without clearing the step would fail here rather than produce a screen
        // claiming a finished source is still fetching its guide.
        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE source SET status = 'READY', sync_step = 'FETCHING_EPG' WHERE id = :id
                """)
                .param("id", sourceId)
                .update())
                .hasMessageContaining("source_sync_step_check");
    }

    @Test
    @DisplayName("auto-sync picks up a stale READY source and ignores one that failed")
    void autoSyncPicksUpStaleReadySources() {
        sources.markSyncing(sourceId);
        sources.markReady(sourceId, null, null);
        ageLastSync();

        assertThat(sources.findDueForAutoSync(12, 25)).containsExactly(sourceId);

        // A source in ERROR is one whose credentials were refused or whose host is
        // gone. Retrying that every twelve hours forever is how a user's own
        // provider locks their account.
        sources.markSyncing(sourceId);
        sources.markError(sourceId, IngestionErrorCode.SOURCE_AUTH_FAILED);
        assertThat(sources.findDueForAutoSync(12, 25)).doesNotContain(sourceId);
    }

    @Test
    @DisplayName("auto_sync off means the server leaves the source alone")
    void autoSyncOffIsHonoured() {
        sources.markSyncing(sourceId);
        sources.markReady(sourceId, null, null);
        ageLastSync();
        sources.update(sourceId, user.id(), null, null, null, null, null, null, false, false);

        assertThat(sources.findDueForAutoSync(12, 25)).doesNotContain(sourceId);
    }

    // ---- helpers ------------------------------------------------------------

    private void ageLastSync() {
        jdbc.sql("UPDATE source SET last_synced_at = now() - interval '2 days' WHERE id = :id")
                .param("id", sourceId)
                .update();
    }

    private UUID insertCategory() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO category (id, source_id, external_id, name, content_type, position)
                VALUES (:id, :sourceId, 'test:cat', 'Test', 'LIVE', 0)
                """)
                .param("id", id)
                .param("sourceId", sourceId)
                .update();
        return id;
    }

    private void insertChannel(UUID categoryId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO channel (id, source_id, category_id, external_id, name,
                                     stream_url, position)
                VALUES (:id, :sourceId, :categoryId, :externalId, 'Chaîne 01',
                        'https://stream.example/x.m3u8', 0)
                """)
                .param("id", id)
                .param("sourceId", sourceId)
                .param("categoryId", categoryId)
                .param("externalId", "test:" + id)
                .update();
    }
}
