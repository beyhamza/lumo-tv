package tv.lumo.api.userdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import tv.lumo.api.generated.model.HideProgressRequest;
import tv.lumo.api.generated.model.PlaybackProgress;
import tv.lumo.api.generated.model.ProgressItemType;
import tv.lumo.api.generated.model.SaveProgressRequest;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.generated.model.WatchedSegment;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * "Continue watching" rules on the server (S12-01, ADR 0012), one test per CW
 * case it guarantees. Segments are placed in the past so the server's clamp to
 * "now" never trims them.
 */
@Import(PostgresContainerInitializer.class)
class ContinueWatchingIntegrationTest extends PostgresIntegrationTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 10, 1, 20, 0, 0, 0, ZoneOffset.UTC);

    @Autowired private UserdataService userdata;
    @Autowired private UserRepository users;
    @Autowired private SourceRepository sources;
    @Autowired private JdbcClient jdbc;

    private UserRow user;
    private UUID sourceId;

    @BeforeEach
    void account() {
        user = users.insert(UUID.randomUUID(), "cw-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "CW Test", "fr");
        sourceId = UUID.randomUUID();
        sources.insert(sourceId, user.id(), "Banc", SourceKind.M3U_URL, null, null, null,
                "https://playlist.example/cw.m3u", null, null, null);
    }

    // ---- watched time (ruling 1) -----------------------------------------------

    @Test
    @DisplayName("CW-01/02/03: no card before 30 s actually watched, a card at 30 s")
    void thresholdIsThirtySecondsWatched() {
        assertThat(save("film", 60_000, seg(0, 10)).getInContinue()).isFalse();
        assertThat(save("film", 60_000, seg(10, 29)).getInContinue()).isFalse();
        PlaybackProgress at30 = save("film", 60_000, seg(29, 30));
        assertThat(at30.getWatchedMs()).isEqualTo(30_000);
        assertThat(at30.getInContinue()).isTrue();
    }

    @Test
    @DisplayName("CW-04: a jump to 20 minutes after 5 s keeps the position but adds no watched time")
    void seekingIsNotWatching() {
        save("film", 5_000, seg(0, 5));
        PlaybackProgress after = save("film", 20 * 60_000, new WatchedSegment[0]);
        assertThat(after.getPositionMs()).isEqualTo(20 * 60_000);
        assertThat(after.getWatchedMs()).isEqualTo(5_000);
        assertThat(after.getInContinue()).isFalse();
    }

    @Test
    @DisplayName("CW-17: 20 s on one device then 10 s on another add up to an eligible card")
    void devicesAddUp() {
        save("film", 20_000, seg(0, 20));
        PlaybackProgress later = save("film", 10_000, seg(600, 610));
        assertThat(later.getWatchedMs()).isEqualTo(30_000);
        assertThat(later.getInContinue()).isTrue();
    }

    @Test
    @DisplayName("CW-29: two devices playing during the same 20 s count 20 s, not 40")
    void simultaneousSecondsCountOnce() {
        save("film", 20_000, seg(0, 20));
        PlaybackProgress other = save("film", 20_000, seg(0, 20));
        assertThat(other.getWatchedMs()).isEqualTo(20_000);
        assertThat(other.getInContinue()).isFalse();
    }

    @Test
    @DisplayName("CW-33: the same save sent three times adds its time once")
    void retriesDoNotMultiply() {
        for (int i = 0; i < 3; i++) {
            save("film", 25_000, seg(0, 25));
        }
        assertThat(list().getFirst().getWatchedMs()).isEqualTo(25_000);
    }

    @Test
    @DisplayName("segments in the future are clamped to the server's clock")
    void futureSegmentsAreClamped() {
        OffsetDateTime now = OffsetDateTime.now();
        SaveProgressRequest request = request("film", 1_000)
                .watchedSegments(List.of(new WatchedSegment(now.plusMinutes(10), now.plusMinutes(20))));
        assertThat(userdata.saveProgress(user.id(), request).getWatchedMs()).isZero();
    }

    // ---- recency (ruling 2) ----------------------------------------------------

    @Test
    @DisplayName("CW-30/32: the latest observation wins, a late old save does not overwrite it")
    void latestObservationWins() {
        saveObserved("film", 40 * 60_000, T0.plusHours(1));
        saveObserved("film", 12 * 60_000, T0.plusHours(2));
        PlaybackProgress late = saveObserved("film", 50 * 60_000, T0.plusMinutes(90));
        assertThat(late.getPositionMs()).isEqualTo(12 * 60_000);
    }

    // ---- hide (ruling 3) -------------------------------------------------------

    @Test
    @DisplayName("CW-10: hiding keeps the progress and takes the card out")
    void hideKeepsProgress() {
        save("film", 40_000, seg(0, 40));
        hide(ProgressItemType.VOD, "film");
        PlaybackProgress hidden = list().getFirst();
        assertThat(hidden.getHidden()).isTrue();
        assertThat(hidden.getInContinue()).isFalse();
        assertThat(hidden.getPositionMs()).isEqualTo(40_000);
    }

    @Test
    @DisplayName("CW-31: a session already running when the card was hidden does not bring it back")
    void runningSessionStaysHidden() {
        OffsetDateTime started = OffsetDateTime.now().minusMinutes(5);
        saveSession("film", 40_000, started, seg(0, 40));
        hide(ProgressItemType.VOD, "film");
        PlaybackProgress still = saveSession("film", 50_000, started, new WatchedSegment[0]);
        assertThat(still.getHidden()).isTrue();
        assertThat(still.getInContinue()).isFalse();
    }

    @Test
    @DisplayName("CW-18/22: a new real start after the hide brings the card back at once")
    void newStartRevealsAtOnce() {
        save("film", 40_000, seg(0, 40));
        hide(ProgressItemType.VOD, "film");
        PlaybackProgress back = saveSession("film", 0, OffsetDateTime.now().plusSeconds(1),
                new WatchedSegment[0]);
        assertThat(back.getHidden()).isFalse();
        assertThat(back.getInContinue()).isTrue();
    }

    @Test
    @DisplayName("hiding an episode hides its series: the next episode does not take the card")
    void hidingAnEpisodeHidesTheSeries() {
        UUID series = series();
        UUID e1 = episode(series, 1);
        UUID e2 = episode(series, 2);
        saveEpisode(e1, 40_000, seg(0, 40));
        saveEpisode(e2, 40_000, seg(100, 140));

        hide(ProgressItemType.EPISODE, e1.toString());

        assertThat(list()).allSatisfy(row -> assertThat(row.getInContinue()).isFalse());
    }

    @Test
    @DisplayName("hiding twice, or an item with no progress, is a no-op")
    void hideIsIdempotent() {
        hide(ProgressItemType.VOD, "never-watched");
        save("film", 40_000, seg(0, 40));
        hide(ProgressItemType.VOD, "film");
        hide(ProgressItemType.VOD, "film");
        assertThat(list().getFirst().getHidden()).isTrue();
    }

    // ---- finished (CW-06/07) and legacy rows -----------------------------------

    @Test
    @DisplayName("CW-06/07: exactly 95 % stays, past 95 % leaves the rail")
    void finishedIsStrictlyPastNinetyFivePercent() {
        SaveProgressRequest at95 = request("film", 95 * 60_000).durationMs(100L * 60_000)
                .watchedSegments(List.of(segment(0, 40)));
        assertThat(userdata.saveProgress(user.id(), at95).getInContinue()).isTrue();
        SaveProgressRequest past = request("film", 95 * 60_000 + 1).durationMs(100L * 60_000);
        assertThat(userdata.saveProgress(user.id(), past).getInContinue()).isFalse();
    }

    @Test
    @DisplayName("CW-09: unknown duration never finishes")
    void unknownDurationStays() {
        assertThat(save("film", 10 * 3_600_000, seg(0, 40)).getInContinue()).isTrue();
    }

    // ---- helpers ---------------------------------------------------------------

    private PlaybackProgress save(String ref, long positionMs, WatchedSegment... segments) {
        return userdata.saveProgress(user.id(), request(ref, positionMs).watchedSegments(List.of(segments)));
    }

    private PlaybackProgress saveObserved(String ref, long positionMs, OffsetDateTime observedAt) {
        return userdata.saveProgress(user.id(), request(ref, positionMs).observedAt(observedAt));
    }

    private PlaybackProgress saveSession(String ref, long positionMs, OffsetDateTime startedAt,
                                         WatchedSegment... segments) {
        return userdata.saveProgress(user.id(), request(ref, positionMs)
                .sessionStartedAt(startedAt).watchedSegments(List.of(segments)));
    }

    private PlaybackProgress saveEpisode(UUID episodeId, long positionMs, WatchedSegment... segments) {
        return userdata.saveProgress(user.id(), new SaveProgressRequest(sourceId, ProgressItemType.EPISODE,
                episodeId.toString(), positionMs).watchedSegments(List.of(segments)));
    }

    private SaveProgressRequest request(String ref, long positionMs) {
        return new SaveProgressRequest(sourceId, ProgressItemType.VOD, ref, positionMs);
    }

    private void hide(ProgressItemType type, String ref) {
        userdata.hideProgress(user.id(), new HideProgressRequest(sourceId, type, ref));
    }

    private List<PlaybackProgress> list() {
        return userdata.listProgress(user.id(), sourceId, null, null, 0, 50).getItems();
    }

    private static WatchedSegment seg(int fromSecond, int toSecond) {
        return segment(fromSecond, toSecond);
    }

    private static WatchedSegment segment(int fromSecond, int toSecond) {
        return new WatchedSegment(T0.plusSeconds(fromSecond), T0.plusSeconds(toSecond));
    }

    private UUID series() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO series (id, source_id, external_id, name) VALUES (:id, :s, :e, 'Série')")
                .param("id", id).param("s", sourceId).param("e", "s-" + id).update();
        return id;
    }

    private UUID episode(UUID series, int number) {
        UUID season = jdbc.sql("""
                INSERT INTO season (id, series_id, season_number) VALUES (:id, :series, 1)
                ON CONFLICT DO NOTHING RETURNING id
                """).param("id", UUID.randomUUID()).param("series", series)
                .query(UUID.class).optional()
                .orElseGet(() -> jdbc.sql("SELECT id FROM season WHERE series_id = :s")
                        .param("s", series).query(UUID.class).single());
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO episode (id, series_id, season_id, source_id, external_id,
                                     season_number, episode_number, stream_url)
                VALUES (:id, :series, :season, :source, :ext, 1, :n, 'https://stream.example/e.mp4')
                """)
                .param("id", id).param("series", series).param("season", season)
                .param("source", sourceId).param("ext", "e-" + id).param("n", number).update();
        return id;
    }
}
