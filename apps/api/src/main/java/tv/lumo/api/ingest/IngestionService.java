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
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.ingest.m3u.M3uStreamParser;
import tv.lumo.api.ingest.xmltv.XmltvStreamParser;
import tv.lumo.api.ingest.xtream.XtreamClient;
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

    private final SourceRepository sources;
    private final CatalogWriteRepository catalogWrites;
    private final CredentialCipher cipher;
    private final IngestionHttpClient http;
    private final XtreamClient xtream;
    private final M3uStreamParser m3uParser;
    private final XmltvStreamParser xmltvParser;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public IngestionService(SourceRepository sources,
                            CatalogWriteRepository catalogWrites,
                            CredentialCipher cipher,
                            IngestionHttpClient http,
                            XtreamClient xtream,
                            M3uStreamParser m3uParser,
                            XmltvStreamParser xmltvParser) {
        this.sources = sources;
        this.catalogWrites = catalogWrites;
        this.cipher = cipher;
        this.http = http;
        this.xtream = xtream;
        this.m3uParser = m3uParser;
        this.xmltvParser = xmltvParser;
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
            log.info("Ingestion of source {} failed: {}", sourceId, e.code());
            sources.markError(sourceId, e.code());
        } catch (Exception e) {
            // Never let a background thread die with the source stuck in SYNCING:
            // markSyncing would refuse to reclaim it and the user could never retry.
            log.error("Unexpected failure ingesting source {}", sourceId, e);
            sources.markError(sourceId, IngestionErrorCode.SOURCE_UNREACHABLE);
        }
    }

    private void ingest(UUID sourceId) {
        SourceRepository.SourceRow source = sources.findForIngestion(sourceId)
                .orElseThrow(() -> new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE,
                        "Source disappeared before ingestion started"));

        log.info("Ingesting source {} ({})", sourceId, source.kind().getValue());

        // M3_U_URL / M3_U_FILE are openapi-generator's mangling of M3U_URL and
        // M3U_FILE: its camelizer splits at the digit-letter boundary. The WIRE
        // values are correct ("M3U_URL"), only the Java constant names are ugly.
        // Left alone rather than fixed with x-enum-varnames in the contract,
        // which would rename constants in all three generated clients at once.
        XtreamClient.XtreamAccount account = switch (source.kind()) {
            case XTREAM -> ingestXtream(source);
            case M3_U_URL -> {
                ingestM3u(source);
                yield null;
            }
            // Not creatable through the API (see SourceKind in the contract).
            case M3_U_FILE -> throw new IngestionException(IngestionErrorCode.SOURCE_INVALID_FORMAT,
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

        // Categories first, because channels reference them. Each upsert returns
        // the id actually in the table, so this map is correct on a re-sync where
        // the rows already existed.
        Map<String, UUID> categoryIds = new HashMap<>();
        int[] categoryPosition = {0};
        xtream.streamLiveCategories(host, source.username(), password, category ->
                categoryIds.put(category.externalId(), catalogWrites.upsertCategoryReturningId(
                        source.id(), category.externalId(), category.name(),
                        "LIVE", categoryPosition[0]++)));

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
                    stream.adult()));
        });
        batcher.flushNow();

        if (seenExternalIds.isEmpty()) {
            throw new IngestionException(IngestionErrorCode.SOURCE_EMPTY,
                    "The panel returned no live channel");
        }
        catalogWrites.deleteChannelsNotIn(source.id(), seenExternalIds);
        log.info("Source {}: ingested {} channel(s)", source.id(), seenExternalIds.size());
        return account;
    }

    // ---- M3U ----------------------------------------------------------------

    private void ingestM3u(SourceRepository.SourceRow source) {
        URI uri = URI.create(source.m3uUrl());
        String host = uri.getHost() == null ? source.m3uUrl() : uri.getHost();

        // An M3U has no category list: groups are discovered while streaming and
        // created on first sight. computeIfAbsent means one statement per distinct
        // group, not one per channel.
        Map<String, UUID> categoryIds = new HashMap<>();
        List<String> seenExternalIds = new ArrayList<>();
        int[] position = {0};

        CatalogWriteRepository.Batcher<CatalogWriteRepository.ChannelUpsert> batcher =
                CatalogWriteRepository.batcher(batch -> catalogWrites.upsertChannels(source.id(), batch));

        http.get(host, uri, stream -> m3uParser.parse(stream, channel -> {
            UUID categoryId = categoryIds.computeIfAbsent(channel.categoryName(), name ->
                    catalogWrites.upsertCategoryReturningId(
                            source.id(), "m3u:" + name, name, "LIVE", categoryIds.size()));

            // An M3U carries no stable per-channel id, so one is derived from the
            // entry itself. The stream URL is hashed rather than used directly:
            // external_id is returned by the API, and a stream URL must never
            // leave through a listing (AGENTS.md §5).
            String externalId = "m3u:" + Integer.toHexString(channel.streamUrl().hashCode())
                    + ":" + Integer.toHexString(channel.name().hashCode());
            seenExternalIds.add(externalId);
            batcher.add(new CatalogWriteRepository.ChannelUpsert(
                    UUID.randomUUID(), categoryId, externalId, channel.name(),
                    channel.logoUrl(), channel.tvgId(), channel.streamUrl(),
                    position[0]++, false));
        }));
        batcher.flushNow();
        catalogWrites.deleteChannelsNotIn(source.id(), seenExternalIds);
        log.info("Source {}: ingested {} channel(s) from playlist", source.id(), seenExternalIds.size());
    }

    // ---- XMLTV --------------------------------------------------------------

    private void ingestEpg(SourceRepository.SourceRow source) {
        URI uri = URI.create(source.epgUrl());
        String host = uri.getHost() == null ? source.epgUrl() : uri.getHost();

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
}
