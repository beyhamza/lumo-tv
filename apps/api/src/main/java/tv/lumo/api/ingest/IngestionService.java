package tv.lumo.api.ingest;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tv.lumo.api.catalog.CatalogWriteRepository;
import tv.lumo.api.generated.model.ContentType;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.generated.model.SyncStep;
import tv.lumo.api.ingest.m3u.M3uContentClassifier;
import tv.lumo.api.ingest.m3u.M3uStreamParser;
import tv.lumo.api.ingest.xmltv.XmltvStreamParser;
import tv.lumo.api.ingest.xtream.XtreamClient;
import tv.lumo.api.shared.config.LumoProperties;
import tv.lumo.api.shared.crypto.CredentialCipher;
import tv.lumo.api.source.SourceRepository;

/**
 * Runs a full catalogue ingestion in the background.
 *
 * <p>Asynchronous by design: creating a source returns immediately in
 * {@code PENDING} and the client polls {@code GET /sources/{id}}. A source never
 * blocks an HTTP request (docs/architecture.md §2).
 *
 * <p><b>Failures here are not HTTP errors.</b> By the time this runs the caller
 * has already been answered. A failure is recorded on the row as
 * {@code status = ERROR} plus a stable {@code error_code}, which is what the
 * polling client reads.
 *
 * <p>Concurrency: one virtual thread per ingestion, blocking style throughout
 * (ADR 0005). Virtual threads are never pooled — they are cheap and disposable,
 * and pooling them defeats the whole mechanism. The bound on how many run at once
 * comes from {@link HostConcurrencyLimiter}, not from this executor.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int STALE_SYNC_MINUTES = 30;

    /**
     * The stable identifier of the group the server invents for entries with no
     * {@code group-title}. Clients translate on this rather than on the English
     * fallback name (see {@link #externalIdFor}). Documented in the contract on
     * {@code Category.external_id}.
     */
    private static final String UNCLASSIFIED_EXTERNAL_ID = "m3u:__unclassified__";

    private final SourceRepository sources;
    private final CatalogWriteRepository catalogWrites;
    private final CredentialCipher cipher;
    private final IngestionHttpClient http;
    private final XtreamClient xtream;
    private final M3uStreamParser m3uParser;
    private final XmltvStreamParser xmltvParser;
    private final LumoProperties.AutoSync autoSync;
    /**
     * One virtual thread per ingestion. Never a fixed-size pool: virtual threads
     * are cheap and disposable, and pooling them defeats the entire mechanism
     * (ADR 0005 §4).
     *
     * <p>This is a long-lived submit-and-forget executor rather than the
     * try-with-resources fan-out ADR 0005 describes, because an ingestion is
     * scheduled by an HTTP request that must return immediately and is not joined
     * by anyone. It therefore needs an explicit shutdown — see {@link #shutdown()}.
     *
     * <p>It imposes NO limit on concurrency. The bound is
     * {@link HostConcurrencyLimiter}, deliberately, so backpressure is a decision
     * rather than a side effect of how the executor happens to be sized.
     */
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public IngestionService(SourceRepository sources,
                            CatalogWriteRepository catalogWrites,
                            CredentialCipher cipher,
                            IngestionHttpClient http,
                            XtreamClient xtream,
                            M3uStreamParser m3uParser,
                            XmltvStreamParser xmltvParser,
                            LumoProperties properties) {
        this.sources = sources;
        this.catalogWrites = catalogWrites;
        this.cipher = cipher;
        this.http = http;
        this.xtream = xtream;
        this.m3uParser = m3uParser;
        this.xmltvParser = xmltvParser;
        this.autoSync = properties.autoSync();
    }

    /**
     * Schedules an ingestion.
     *
     * @return false if a synchronisation is already running for this source
     */
    public boolean schedule(UUID sourceId) {
        if (!sources.markSyncing(sourceId)) {
            return false;
        }
        workers.submit(() -> runSafely(sourceId));
        return true;
    }

    private void runSafely(UUID sourceId) {
        try {
            ingest(sourceId);
        } catch (IngestionException e) {
            // The message as well as the code. Every one of these failures is about
            // a third party we do not control, and the code alone — SOURCE_INVALID_FORMAT
            // — says which category it fell into and nothing about why. The
            // messages carry the host, the status and the content type, never a
            // URL and never a credential.
            log.info("Ingestion of source {} failed: {} — {}", sourceId, e.code(), e.getMessage());
            sources.markError(sourceId, e.code());
        } catch (Exception e) {
            // Never let a background thread die with the source stuck in SYNCING:
            // markSyncing would refuse to reclaim it and the user could never retry.
            //
            // **`SOURCE_UNREACHABLE` is a lie here and it is a deliberate one**,
            // which is worth stating rather than leaving to be discovered. This
            // branch is reached by faults on *our* side — a constraint we had
            // outgrown put every Xtream source into ERROR for two sprints under
            // this exact code, and it named the user's provider for it.
            //
            // It stays, because `IngestionErrorCode` has no value for "our
            // fault": every one of them describes something about the user's
            // server, and a client that met an unknown code would fall back to a
            // generic message anyway. Adding one is a contract change and belongs
            // in a task; until then the log line above is the truthful record and
            // this is the closest available word.
            log.error("Unexpected failure ingesting source {}", sourceId, e);
            sources.markError(sourceId, IngestionErrorCode.SOURCE_UNREACHABLE);
        }
    }

    private void ingest(UUID sourceId) {
        SourceRepository.SourceRow source = sources.findForIngestion(sourceId)
                .orElseThrow(() -> new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE,
                        "Source disappeared before ingestion started"));

        log.info("Ingesting source {} ({})", sourceId, source.kind().getValue());

        XtreamClient.XtreamAccount account = switch (source.kind()) {
            case XTREAM -> ingestXtream(source);
            case M3U_URL -> {
                ingestM3u(source);
                yield null;
            }
            // Not creatable through the API (see SourceKind in the contract).
            case M3U_FILE -> throw new IngestionException(IngestionErrorCode.SOURCE_INVALID_FORMAT,
                    "Uploaded playlists are not ingestible in v1");
        };

        if (source.epgUrl() != null && !source.epgUrl().isBlank()) {
            ingestEpg(source);
        }

        sources.markReady(sourceId,
                account == null ? null : account.expiresAt(),
                account == null ? null : account.maxConnections());
        log.info("Source {} is READY", sourceId);
    }

    // ---- Xtream -------------------------------------------------------------

    private XtreamClient.XtreamAccount ingestXtream(SourceRepository.SourceRow source) {
        String password = openPassword(source.id());
        String host = source.host();

        XtreamClient.XtreamAccount account = xtream.authenticate(host, source.username(), password);
        // The credentials were accepted and nothing has been parsed yet. This is
        // the step the onboarding checklist ticks second, and it is a real phase
        // rather than a reassuring one: an M3U source never reports it, because
        // an M3U source never authenticates.
        sources.markSyncStep(source.id(), SyncStep.AUTHENTICATED);

        // Categories first, because channels reference them. Each upsert returns
        // the id actually in the table, so this map is correct on a re-sync where
        // the rows already existed.
        Map<String, UUID> categoryIds = new HashMap<>();
        int[] categoryPosition = {0};
        sources.markSyncStep(source.id(), SyncStep.PARSING_CHANNELS);
        xtream.streamLiveCategories(host, source.username(), password, category ->
                categoryIds.put(category.externalId(), catalogWrites.upsertCategoryReturningId(
                        source.id(), category.externalId(), category.name(),
                        ContentType.LIVE.getValue(), categoryPosition[0]++)));

        List<String> seenExternalIds = new ArrayList<>();
        int[] position = {0};
        CatalogWriteRepository.Batcher<CatalogWriteRepository.ChannelUpsert> batcher =
                CatalogWriteRepository.batcher(batch -> catalogWrites.upsertChannels(source.id(), batch));

        xtream.streamLiveStreams(host, source.username(), password, stream -> {
            seenExternalIds.add(stream.externalId());
            batcher.add(new CatalogWriteRepository.ChannelUpsert(
                    UUID.randomUUID(),
                    // A channel in a category the panel never listed keeps a null
                    // category rather than being dropped.
                    categoryIds.get(stream.categoryExternalId()),
                    stream.externalId(),
                    stream.name(),
                    stream.logoUrl(),
                    stream.tvgId(),
                    stream.streamUrl(),
                    position[0]++,
                    stream.adult(),
                    stream.number(),
                    stream.quality()));
        });
        batcher.flushNow();

        if (seenExternalIds.isEmpty()) {
            throw new IngestionException(IngestionErrorCode.SOURCE_EMPTY,
                    "The panel returned no live channel");
        }
        catalogWrites.deleteChannelsNotIn(source.id(), seenExternalIds);

        ingestXtreamVod(source, host, password);
        ingestXtreamSeries(source, host, password);

        log.info("Source {}: ingested {} channel(s)", source.id(), seenExternalIds.size());
        return account;
    }

    /**
     * The film catalogue, after the channels.
     *
     * <p><b>Its failure does not fail the source.</b> A panel that serves live
     * television and refuses {@code get_vod_streams} — or has no film catalogue at
     * all, which is common — must still end up {@code READY} with its channels.
     * Letting this throw would turn a source that works into a source that is
     * broken, over a catalogue the user may never open.
     *
     * <p>That is also why the deletion below is guarded by a non-empty list: an
     * empty answer means "no films seen", which after a failed read means "the
     * read failed", not "the panel dropped them all".
     */
    private void ingestXtreamVod(SourceRepository.SourceRow source, String host, String password) {
        sources.markSyncStep(source.id(), SyncStep.PARSING_VOD);

        Map<String, UUID> categoryIds = new HashMap<>();
        int[] categoryPosition = {0};
        List<String> seenExternalIds = new ArrayList<>();
        int[] position = {0};
        CatalogWriteRepository.Batcher<CatalogWriteRepository.VodUpsert> batcher =
                CatalogWriteRepository.batcher(batch -> catalogWrites.upsertVodItems(source.id(), batch));

        try {
            xtream.streamVodCategories(host, source.username(), password, category ->
                    categoryIds.put(category.externalId(), catalogWrites.upsertCategoryReturningId(
                            source.id(), category.externalId(), category.name(),
                            ContentType.VOD.getValue(), categoryPosition[0]++)));

            xtream.streamVodStreams(host, source.username(), password, film -> {
                seenExternalIds.add(film.externalId());
                batcher.add(new CatalogWriteRepository.VodUpsert(
                        UUID.randomUUID(),
                        categoryIds.get(film.categoryExternalId()),
                        film.externalId(),
                        film.name(),
                        film.posterUrl(),
                        film.year(),
                        film.durationSeconds(),
                        film.rating(),
                        film.streamUrl(),
                        film.containerExtension(),
                        position[0]++,
                        film.adult()));
            });
            batcher.flushNow();
        } catch (IngestionException e) {
            // Named, not swallowed: the channels are in and the source is usable,
            // and the next synchronisation will try the films again.
            log.info("Source {}: no film catalogue ingested ({})", source.id(), e.code());
            return;
        } catch (RuntimeException e) {
            // **The promise this method makes is that its failure does not fail
            // the source, and until this catch existed it only kept that promise
            // for one exception type.**
            //
            // A `CHECK` constraint this code had outgrown threw a
            // `DataIntegrityViolationException` on the very first line — the
            // step marker — which went straight past the handler above and killed
            // the whole ingestion. Every Xtream source lost its channels along
            // with its films, and the user was told their provider was
            // unreachable. See `0017-sync-step-values.sql`.
            //
            // Warn rather than info: an `IngestionException` is somebody else's
            // server having a bad day, and this is a fault on our side.
            log.warn("Source {}: film catalogue ingestion failed unexpectedly",
                    source.id(), e);
        }

        if (!seenExternalIds.isEmpty()) {
            catalogWrites.deleteVodNotIn(source.id(), seenExternalIds);
        }
        // Counted apart from the channels on purpose: one total would hide the case
        // where either of the two is zero.
        log.info("Source {}: ingested {} film(s)", source.id(), seenExternalIds.size());
    }

    /**
     * The series list, after the films.
     *
     * <p><b>The flat list only.</b> {@code get_series} answers with every series
     * of the panel; the tree of one series is {@code get_series_info}, one call
     * per series, made when somebody opens it. Walking the trees here would be
     * eight hundred requests against the user's own provider at every
     * synchronisation — not slow, bannable.
     *
     * <p><b>Its failure does not fail the source</b>, exactly as the films'
     * does not. A panel that serves television and refuses {@code get_series} —
     * or simply has no series, which is common — must still end up
     * {@code READY}. The deletion is guarded by a non-empty list for the same
     * reason: an empty answer after a failed read means the read failed, not
     * that the panel dropped everything.
     *
     * <p>There is no M3U counterpart to this method, and there will not be:
     * a playlist declares no season and no episode, and this application does
     * not reconstruct a tree from titles (ADR 0010).
     */
    private void ingestXtreamSeries(SourceRepository.SourceRow source, String host,
                                    String password) {
        sources.markSyncStep(source.id(), SyncStep.PARSING_SERIES);

        Map<String, UUID> categoryIds = new HashMap<>();
        int[] categoryPosition = {0};
        List<String> seenExternalIds = new ArrayList<>();
        int[] position = {0};
        CatalogWriteRepository.Batcher<CatalogWriteRepository.SeriesUpsert> batcher =
                CatalogWriteRepository.batcher(batch -> catalogWrites.upsertSeries(source.id(), batch));

        try {
            xtream.streamSeriesCategories(host, source.username(), password, category ->
                    categoryIds.put(category.externalId(), catalogWrites.upsertCategoryReturningId(
                            source.id(), category.externalId(), category.name(),
                            ContentType.SERIES.getValue(), categoryPosition[0]++)));

            xtream.streamSeries(host, source.username(), password, series -> {
                seenExternalIds.add(series.externalId());
                batcher.add(new CatalogWriteRepository.SeriesUpsert(
                        UUID.randomUUID(),
                        categoryIds.get(series.categoryExternalId()),
                        series.externalId(),
                        series.name(),
                        series.posterUrl(),
                        series.year(),
                        series.episodeRunTimeMinutes(),
                        series.rating(),
                        position[0]++,
                        false));
            });
            batcher.flushNow();
        } catch (IngestionException e) {
            log.info("Source {}: no series catalogue ingested ({})", source.id(), e.code());
            return;
        } catch (RuntimeException e) {
            // The films' guard, for the same reason and with the same history.
            log.warn("Source {}: series catalogue ingestion failed unexpectedly",
                    source.id(), e);
        }

        if (!seenExternalIds.isEmpty()) {
            catalogWrites.deleteSeriesNotIn(source.id(), seenExternalIds);
        }
        // Counted apart from the channels and the films, for the reason they are
        // counted apart from each other: one total hides a zero.
        log.info("Source {}: ingested {} series", source.id(), seenExternalIds.size());
    }
    // ---- M3U ----------------------------------------------------------------

    private void ingestM3u(SourceRepository.SourceRow source) {
        // Rejects anything that is not an absolute http(s) URL, and guarantees a
        // non-null host. The host is what reaches the logs; the URL never does
        // (AGENTS.md §5). Re-validated here rather than trusted from the row: a
        // source stored before that rule existed is still in the table.
        URI uri = SourceUrl.parse(source.m3uUrl());
        String host = SourceUrl.hostOf(uri);

        // An M3U has no category list: groups are discovered while streaming and
        // created on first sight. computeIfAbsent means one statement per distinct
        // group, not one per entry.
        //
        // Keyed on the content type as well as the name, because the same group can
        // legitimately hold both: `VOD - ACTION` with one entry served as `.m3u8`
        // produces a LIVE category and a VOD category of the same name, which is
        // exactly what the unique index on (source_id, content_type, external_id)
        // allows.
        Map<String, UUID> categoryIds = new HashMap<>();
        List<String> seenChannelIds = new ArrayList<>();
        List<String> seenFilmIds = new ArrayList<>();
        int[] channelPosition = {0};
        int[] filmPosition = {0};

        CatalogWriteRepository.Batcher<CatalogWriteRepository.ChannelUpsert> channels =
                CatalogWriteRepository.batcher(batch -> catalogWrites.upsertChannels(source.id(), batch));
        CatalogWriteRepository.Batcher<CatalogWriteRepository.VodUpsert> films =
                CatalogWriteRepository.batcher(batch -> catalogWrites.upsertVodItems(source.id(), batch));

        // No AUTHENTICATED step: a playlist URL is fetched, not authenticated
        // against. The contract says these are the server's real phases and that
        // a step the implementation does not distinguish is a reassuring fiction.
        //
        // No PARSING_VOD either, and for the same reason: a playlist is read once,
        // and its films and its channels come off the same pass.
        sources.markSyncStep(source.id(), SyncStep.PARSING_CHANNELS);
        http.get(host, uri, stream -> m3uParser.parse(stream, entry -> {
            ContentType type = M3uContentClassifier.classify(entry.streamUrl());

            UUID categoryId = categoryIds.computeIfAbsent(
                    type.getValue() + ":" + entry.categoryName(),
                    key -> catalogWrites.upsertCategoryReturningId(
                            source.id(), externalIdFor(entry.categoryName()), entry.categoryName(),
                            type.getValue(), categoryIds.size()));

            // An M3U carries no stable per-entry id, so one is derived from the
            // entry itself. The stream URL is hashed rather than used directly:
            // external_id is returned by the API, and a stream URL must never
            // leave through a listing (AGENTS.md §5).
            String externalId = "m3u:" + Integer.toHexString(entry.streamUrl().hashCode())
                    + ":" + Integer.toHexString(entry.name().hashCode());

            if (type == ContentType.VOD) {
                seenFilmIds.add(externalId);
                films.add(new CatalogWriteRepository.VodUpsert(
                        UUID.randomUUID(), categoryId, externalId, entry.name(),
                        entry.logoUrl(), null, null, null, entry.streamUrl(),
                        // Null, and deliberately: the playlist carries the whole
                        // URL, so there is nothing to build and nothing to store
                        // (ADR 0009, ruling 4).
                        null, filmPosition[0]++, false));
            } else {
                seenChannelIds.add(externalId);
                channels.add(new CatalogWriteRepository.ChannelUpsert(
                        UUID.randomUUID(), categoryId, externalId, entry.name(),
                        entry.logoUrl(), entry.tvgId(), entry.streamUrl(),
                        channelPosition[0]++, false, entry.number(), entry.quality()));
            }
        }));
        channels.flushNow();
        films.flushNow();

        catalogWrites.deleteChannelsNotIn(source.id(), seenChannelIds);
        catalogWrites.deleteVodNotIn(source.id(), seenFilmIds);
        // Counted apart, as for an Xtream panel: one total would hide a playlist
        // the classifier sent entirely one way.
        log.info("Source {}: ingested {} channel(s) and {} film(s) from playlist",
                source.id(), seenChannelIds.size(), seenFilmIds.size());
    }

    // ---- XMLTV --------------------------------------------------------------

    /**
     * The {@code external_id} of an M3U group.
     *
     * <p>Groups from the playlist are keyed by their own name. The one group the
     * server invents — the bucket for entries with no {@code group-title} — gets
     * a stable sentinel instead, because its name is the only user-facing string
     * in the product that the server would otherwise author, in English, for a
     * French user.
     *
     * <p>A client renders its own translation when it sees the sentinel and
     * falls back to {@code name} otherwise. A playlist that happens to contain a
     * group literally called "Unclassified" merges into the same bucket, which
     * is where its channels belong anyway.
     */
    private static String externalIdFor(String groupName) {
        return M3uStreamParser.UNCLASSIFIED.equals(groupName)
                ? UNCLASSIFIED_EXTERNAL_ID
                : "m3u:" + groupName;
    }

    private void ingestEpg(SourceRepository.SourceRow source) {
        URI uri = SourceUrl.parse(source.epgUrl());
        String host = SourceUrl.hostOf(uri);
        sources.markSyncStep(source.id(), SyncStep.FETCHING_EPG);

        CatalogWriteRepository.Batcher<CatalogWriteRepository.ProgrammeUpsert> batcher =
                CatalogWriteRepository.batcher(batch -> catalogWrites.upsertProgrammes(source.id(), batch));

        try {
            http.get(host, uri, stream -> xmltvParser.parse(stream, programme ->
                    batcher.add(new CatalogWriteRepository.ProgrammeUpsert(
                            UUID.randomUUID(), programme.channelId(), programme.startsAt(),
                            programme.endsAt(), programme.title(), programme.description(),
                            programme.category()))));
            batcher.flushNow();
        } catch (IngestionException e) {
            // A guide that will not load must not fail the whole source: the user
            // still has a working channel list, which is what they came for.
            // The EPG is simply absent until the next sync.
            log.warn("Source {}: EPG ingestion failed ({}); channels are unaffected",
                    source.id(), e.code());
        }
    }

    // ---- helpers ------------------------------------------------------------

    private String openPassword(UUID sourceId) {
        byte[] sealed = sources.findSealedPassword(sourceId)
                .orElseThrow(() -> new IngestionException(IngestionErrorCode.SOURCE_AUTH_FAILED,
                        "The source has no stored credential"));
        return cipher.open(sealed);
    }

    /**
     * Stops accepting work and gives running ingestions a moment to finish.
     *
     * <p>Without this the executor is never closed. On a graceful shutdown the
     * JVM would exit with ingestions mid-flight, leaving their sources stuck in
     * SYNCING until the housekeeping sweep releases them — a user staring at a
     * spinner for half an hour for no reason.
     */
    @jakarta.annotation.PreDestroy
    void shutdown() {
        workers.shutdown();
        try {
            if (!workers.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("Ingestion workers did not finish within the shutdown grace period; "
                        + "their sources will be released by the housekeeping sweep");
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        }
    }

    /**
     * Housekeeping: release syncs abandoned by a crash, and purge stale guide rows.
     *
     * <p>Runs with {@code Propagation.NOT_SUPPORTED} so a long purge does not hold
     * a transaction open across the whole sweep.
     */
    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 60_000L)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void housekeeping() {
        int released = sources.releaseStaleSyncs(STALE_SYNC_MINUTES);
        if (released > 0) {
            log.warn("Released {} source(s) stuck in SYNCING for more than {} minutes",
                    released, STALE_SYNC_MINUTES);
        }
        int purged = catalogWrites.purgeExpiredProgrammes();
        if (purged > 0) {
            log.debug("Purged {} expired EPG programme(s)", purged);
        }
    }

    /**
     * Re-synchronises the sources whose owner asked the server to keep them fresh.
     *
     * <p>This is what {@code auto_sync} means. Without it the property is a switch
     * wired to nothing: the contract tells the user the server refreshes the
     * source by itself, the API accepts the value, and the catalogue goes stale
     * anyway — the worst of the three possible outcomes, because it is the one
     * the user cannot see.
     *
     * <p>Bounded on both sides. {@link LumoProperties.AutoSync#batchSize} caps how
     * many start per sweep, so a thousand due sources become a queue rather than a
     * thousand simultaneous connections to a thousand panels; and
     * {@link HostConcurrencyLimiter} still bounds what reaches any single host.
     * The whole point of re-synchronising on the user's behalf is that they do not
     * watch it happen, which is exactly why it must not be what gets their account
     * throttled.
     *
     * <p>{@link #schedule} is idempotent under concurrency — its claim is an
     * {@code UPDATE ... WHERE status <> 'SYNCING'} — so two instances running this
     * sweep at the same time cannot ingest one source twice.
     */
    @Scheduled(fixedDelayString = "${lumo.auto-sync.sweep-interval:PT1H}", initialDelay = 120_000L)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void autoSyncDueSources() {
        if (!autoSync.enabled()) {
            return;
        }
        List<UUID> due = sources.findDueForAutoSync(autoSync.everyHours(), autoSync.batchSize());
        int started = 0;
        for (UUID sourceId : due) {
            if (schedule(sourceId)) {
                started++;
            }
        }
        if (started > 0) {
            log.info("Auto-sync: started {} of {} due source(s)", started, due.size());
        }
    }
}
