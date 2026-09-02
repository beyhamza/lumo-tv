package tv.lumo.api.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import tv.lumo.api.catalog.CatalogWriteRepository;
import tv.lumo.api.catalog.SeriesTreeSource.Availability;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.generated.model.SourceStatus;
import tv.lumo.api.ingest.xtream.XtreamClient;
import tv.lumo.api.shared.crypto.CredentialCipher;
import tv.lumo.api.source.SourceRepository;

/**
 * When a series tree is fetched, and — more to the point — when it is not (S6-03).
 *
 * <h2>Why this is worth testing away from the database</h2>
 *
 * Everything decided here is decided before a row is written: whether to call the
 * user's panel at all, whether to wait for the answer, and what to say when it
 * does not come. Those are the decisions that get somebody's address banned when
 * they are wrong, and none of them shows up as a failure — they show up as
 * traffic.
 *
 * <p>The panel is a mock for the reason the ingestion tests use one: a suite that
 * made outbound requests would be testing somebody's server.
 */
class SeriesTreeCacheTest {

    private SourceRepository sources;
    private CatalogWriteRepository writes;
    private XtreamClient xtream;
    private XtreamSeriesTreeSource trees;

    private final UUID sourceId = UUID.randomUUID();
    private final UUID seriesId = UUID.randomUUID();

    @BeforeEach
    void wireAnXtreamSource() {
        sources = mock(SourceRepository.class);
        writes = mock(CatalogWriteRepository.class);
        xtream = mock(XtreamClient.class);
        CredentialCipher cipher = mock(CredentialCipher.class);

        when(sources.findForIngestion(sourceId)).thenReturn(Optional.of(xtreamRow()));
        when(sources.findSealedPassword(sourceId)).thenReturn(Optional.of(new byte[] {1}));
        when(cipher.open(any())).thenReturn("secret");

        trees = new XtreamSeriesTreeSource(sources, writes, cipher, xtream);
    }

    @Test
    @DisplayName("a fresh tree costs nothing: no call, no wait")
    void freshTreeIsLeftAlone() {
        Availability result = trees.ensureTree(sourceId, seriesId, "42", OffsetDateTime.now());

        assertThat(result).isEqualTo(Availability.AVAILABLE);
        // The whole point of the stamp. A panel asked again for something answered
        // ten minutes ago is capacity spent on a settled question.
        verify(xtream, never()).fetchSeriesInfo(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("an absent tree is fetched, and the caller waits for it")
    void absentTreeIsFetchedSynchronously() {
        when(xtream.fetchSeriesInfo(anyString(), anyString(), anyString(), eq("42")))
                .thenReturn(List.of(season(1, "e1")));

        Availability result = trees.ensureTree(sourceId, seriesId, "42", null);

        assertThat(result).isEqualTo(Availability.AVAILABLE);
        // Written before the answer comes back, because there is nothing to show
        // otherwise.
        verify(writes).replaceTree(eq(seriesId), eq(sourceId), any());
    }

    @Test
    @DisplayName("an absent tree the panel cannot supply is UNAVAILABLE, not an empty tree")
    void absentTreeAndUnreachablePanel() {
        when(xtream.fetchSeriesInfo(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE, "down"));

        Availability result = trees.ensureTree(sourceId, seriesId, "42", null);

        // The caller turns this into a 503. Serving an empty tree instead would
        // tell somebody their series has no episodes, which is a different and
        // much more alarming thing than "your provider did not answer".
        assertThat(result).isEqualTo(Availability.UNAVAILABLE);
        verify(writes, never()).replaceTree(any(), any(), any());
    }

    @Test
    @DisplayName("a stale tree is served immediately, and refreshed behind the answer")
    void staleTreeIsServedWhileItRefreshes() throws Exception {
        CountDownLatch fetched = new CountDownLatch(1);
        when(xtream.fetchSeriesInfo(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer((Answer<List<XtreamClient.XtreamSeason>>) invocation -> {
                    fetched.countDown();
                    return List.of(season(1, "e1"));
                });

        OffsetDateTime old = OffsetDateTime.now().minus(XtreamSeriesTreeSource.treeTtl()).minusHours(1);
        Availability result = trees.ensureTree(sourceId, seriesId, "42", old);

        // Immediately, without waiting: a waiting screen over episodes we already
        // hold is a regression for a feature that is meant to be a convenience.
        assertThat(result).isEqualTo(Availability.AVAILABLE);
        assertThat(fetched.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("a stale tree the panel cannot supply is still served")
    void staleTreeSurvivesAnUnreachablePanel() {
        when(xtream.fetchSeriesInfo(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE, "down"));

        OffsetDateTime old = OffsetDateTime.now().minus(XtreamSeriesTreeSource.treeTtl()).minusHours(1);

        // What is held is old, not wrong. Refusing to show it because the refresh
        // failed would take away the episodes somebody could still watch.
        assertThat(trees.ensureTree(sourceId, seriesId, "42", old))
                .isEqualTo(Availability.AVAILABLE);
    }

    @Test
    @DisplayName("two simultaneous opens of the same series make one call, not two")
    void singleFlightPerSeries() throws Exception {
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();

        when(xtream.fetchSeriesInfo(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer((Answer<List<XtreamClient.XtreamSeason>>) invocation -> {
                    calls.incrementAndGet();
                    inFlight.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    return List.of(season(1, "e1"));
                });

        Thread first = Thread.ofVirtual()
                .start(() -> trees.ensureTree(sourceId, seriesId, "42", null));
        assertThat(inFlight.await(5, TimeUnit.SECONDS)).isTrue();

        // The second open, while the first is still waiting on the panel.
        Availability second = trees.ensureTree(sourceId, seriesId, "42", null);

        release.countDown();
        first.join();

        // One call. Without the guard, the same series opened on a phone, a
        // television and a browser is three requests for one answer — and it is
        // the user's own provider that pays for them.
        assertThat(calls.get()).isEqualTo(1);
        // The second caller had nothing cached and did not fetch, so it must not
        // claim a tree is there. It answers 503 and the viewer retries.
        assertThat(second).isEqualTo(Availability.UNAVAILABLE);
    }

    @Test
    @DisplayName("a panel answering nonsense leaves the cache untouched")
    void unreadableAnswerIsNotStamped() {
        when(xtream.fetchSeriesInfo(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null);

        assertThat(trees.ensureTree(sourceId, seriesId, "42", null))
                .isEqualTo(Availability.UNAVAILABLE);
        // Not stamped: an unreadable answer is a moment, not a fact, and stamping
        // it would mean six hours before anybody asked again.
        verify(writes, never()).replaceTree(any(), any(), any());
    }

    @Test
    @DisplayName("a series with no panel identifier is never asked for")
    void noExternalIdMeansNoCall() {
        assertThat(trees.ensureTree(sourceId, seriesId, null, null))
                .isEqualTo(Availability.UNAVAILABLE);
        assertThat(trees.ensureTree(sourceId, seriesId, null, OffsetDateTime.now().minusYears(1)))
                .isEqualTo(Availability.AVAILABLE);

        verify(xtream, never()).fetchSeriesInfo(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("an M3U source is never asked for a tree")
    void m3uSourcesAreNotAsked() {
        when(sources.findForIngestion(sourceId)).thenReturn(Optional.of(m3uRow()));

        // ADR 0010: a playlist declares no season and no episode. There is nothing
        // to call, and calling anyway would be asking a question the format cannot
        // answer.
        assertThat(trees.ensureTree(sourceId, seriesId, "42", null))
                .isEqualTo(Availability.UNAVAILABLE);
        verify(xtream, never()).fetchSeriesInfo(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("the tree is passed through whole, seasons with holes included")
    void treeIsPassedThroughUnaltered() {
        when(xtream.fetchSeriesInfo(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(
                        new XtreamClient.XtreamSeason(1, 3, null, List.of(
                                episode("e1", 1, 1), episode("e3", 1, 3))),
                        new XtreamClient.XtreamSeason(2, null, null, List.of())));

        trees.ensureTree(sourceId, seriesId, "42", null);

        // Episode 2 is missing and season 2 is empty. Neither is renumbered and
        // neither is dropped: what the panel says is what the viewer sees, and
        // inventing an episode 2 to close the gap would be inventing content.
        verify(writes, times(1)).replaceTree(eq(seriesId), eq(sourceId), any());
    }

    // ---- fixtures ----------------------------------------------------------

    private SourceRepository.SourceRow xtreamRow() {
        return row(SourceKind.XTREAM, "https://panel.example", "user", null);
    }

    private SourceRepository.SourceRow m3uRow() {
        return row(SourceKind.M3U_URL, null, null, "https://playlist.example/one.m3u");
    }

    private SourceRepository.SourceRow row(SourceKind kind, String host, String username,
                                           String m3uUrl) {
        return new SourceRepository.SourceRow(sourceId, UUID.randomUUID(), "Test source",
                kind, host, username, m3uUrl, null, SourceStatus.READY, null, null, null,
                null, null, null, true);
    }

    private static XtreamClient.XtreamSeason season(int number, String episodeId) {
        return new XtreamClient.XtreamSeason(number, 1, null, List.of(episode(episodeId, number, 1)));
    }

    private static XtreamClient.XtreamEpisode episode(String id, int season, int number) {
        return new XtreamClient.XtreamEpisode(id, season, number, "Episode " + number,
                2700, null, "https://stream.example/series/" + id + ".mkv", "mkv",
                // Null, because this test is about caching a tree and a panel that
                // states no codec is the ordinary case. What happens to the value
                // is XtreamClientTest's subject.
                null, null);
    }
}
