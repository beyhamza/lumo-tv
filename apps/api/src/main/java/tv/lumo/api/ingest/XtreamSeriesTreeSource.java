package tv.lumo.api.ingest;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tv.lumo.api.catalog.CatalogWriteRepository;
import tv.lumo.api.catalog.SeriesTreeSource;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.ingest.xtream.XtreamClient;
import tv.lumo.api.shared.crypto.CredentialCipher;
import tv.lumo.api.source.SourceRepository;

/**
 * Fetches the tree of one series from an Xtream panel, and caches it (S6-03).
 *
 * <h2>Why this is not part of the ingestion</h2>
 *
 * {@code get_series_info} takes one series and answers with all of it. Calling it
 * while walking a catalogue would mean one request per series, against the user's
 * own provider, at every synchronisation — eight hundred on an ordinary panel.
 * That is not slow, it is the kind of traffic that gets an address banned, and
 * {@link HostConcurrencyLimiter} protects <em>our</em> infrastructure rather than
 * theirs.
 *
 * <h2>The cache expires, and that is the difference with a film's synopsis</h2>
 *
 * A plot never changes once fetched. A series in production gains an episode a
 * week, so a tree fetched last month is wrong in the direction nobody notices — a
 * viewer who cannot see the episode that came out this morning.
 *
 * <p>The window is <b>short and uniform</b> rather than clever. Nothing in the
 * data distinguishes a series that ended in 2011 from one airing tonight: no
 * status, no end date, nothing. A rule that pretended to tell them apart would be
 * guessing, and guessing wrong in exactly the case that matters.
 *
 * <h2>Two protections, and they are what stop the banning</h2>
 *
 * <b>One request in flight per series.</b> Three screens opening the same series
 * on three devices is one call, not three. Without this, a series opened at the
 * same moment on a phone, a television and a browser triples the load for the same
 * answer.
 *
 * <p><b>A cap per source, not only per host.</b> {@link HostConcurrencyLimiter}
 * bounds what this application does to one hostname; this bounds what one
 * subscription does to its own panel. Somebody browsing twenty series in a minute
 * must not produce twenty simultaneous requests to their provider — and the two
 * are not the same gate, because one user's panel may be shared with a thousand
 * others behind the same host.
 */
@Component
public class XtreamSeriesTreeSource implements SeriesTreeSource {

    private static final Logger log = LoggerFactory.getLogger(XtreamSeriesTreeSource.class);

    /**
     * How long a tree stays fresh.
     *
     * <p>Six hours: long enough that browsing a catalogue costs one call per series
     * rather than one per open, short enough that an episode released this morning
     * is visible this evening. Deliberately not tunable per series — see the class
     * documentation for why a clever rule here would be a wrong one.
     */
    private static final Duration TREE_TTL = Duration.ofHours(6);

    /**
     * Concurrent tree fetches allowed against one source.
     *
     * <p>Two rather than one: a viewer who opens a series, goes back, and opens
     * another should not queue behind the first. Two rather than five: the point is
     * to keep somebody's panel from seeing a burst, and beyond two there is no
     * viewer left to serve — a person reads one screen at a time.
     */
    private static final int MAX_CONCURRENT_PER_SOURCE = 2;

    private static final Duration SOURCE_ENTRY_TTL = Duration.ofHours(1);
    private static final int MAX_TRACKED_SOURCES = 10_000;
    private static final long ACQUIRE_TIMEOUT_SECONDS = 10;

    private final SourceRepository sources;
    private final CatalogWriteRepository catalogWrites;
    private final CredentialCipher cipher;
    private final XtreamClient xtream;

    /**
     * Series with a fetch in flight.
     *
     * <p>A plain map rather than a cache: entries are removed by the fetch that put
     * them there, in a {@code finally}, so it cannot grow. The value is unused —
     * what matters is whether the key is present.
     */
    private final ConcurrentHashMap<UUID, Boolean> inFlight = new ConcurrentHashMap<>();

    /**
     * Per-source permits, evicted after an hour of disuse.
     *
     * <p>Same shape and same reasoning as {@link HostConcurrencyLimiter}'s per-host
     * map: a source being used is being accessed, so it is never evicted while it
     * matters, and an entry dropped and recreated could briefly allow one extra
     * call to a panel nobody has touched for an hour.
     */
    private final Cache<UUID, Semaphore> sourcePermits = Caffeine.newBuilder()
            .expireAfterAccess(SOURCE_ENTRY_TTL)
            .maximumSize(MAX_TRACKED_SOURCES)
            .build();

    /**
     * Where a stale refresh runs.
     *
     * <p>Virtual threads, and the per-source semaphore above is what bounds them —
     * ADR 0005 §1 again: virtual threads removed the ceiling a fixed pool used to
     * provide for free, so the ceiling has to be written down somewhere.
     */
    private final ExecutorService refreshers = Executors.newVirtualThreadPerTaskExecutor();

    public XtreamSeriesTreeSource(SourceRepository sources,
                                  CatalogWriteRepository catalogWrites,
                                  CredentialCipher cipher,
                                  XtreamClient xtream) {
        this.sources = sources;
        this.catalogWrites = catalogWrites;
        this.cipher = cipher;
        this.xtream = xtream;
    }

    @Override
    public Availability ensureTree(UUID sourceId, UUID seriesId, String externalId,
                                   OffsetDateTime treeFetchedAt) {
        boolean cached = treeFetchedAt != null;
        boolean fresh = cached
                && treeFetchedAt.isAfter(OffsetDateTime.now().minus(TREE_TTL));

        if (fresh) {
            return Availability.AVAILABLE;
        }
        if (externalId == null) {
            // No panel identifier: there is nothing to ask for. An M3U series
            // cannot exist (ADR 0010), so this is a row from a source whose kind
            // changed under it, and the honest answer is what is stored.
            return cached ? Availability.AVAILABLE : Availability.UNAVAILABLE;
        }

        if (cached) {
            // Stale. Serve what is held and refresh behind it: an empty waiting
            // screen over data we already have is a regression for a feature whose
            // whole purpose is convenience.
            refreshers.submit(() -> fetchAndStore(sourceId, seriesId, externalId));
            return Availability.AVAILABLE;
        }

        // Nothing to show. This one is worth waiting for.
        return fetchAndStore(sourceId, seriesId, externalId)
                ? Availability.AVAILABLE
                : Availability.UNAVAILABLE;
    }

    /**
     * One fetch, guarded by both protections.
     *
     * @return whether a tree is in the table when this returns. A refusal to start
     *     because another fetch is already in flight counts as <b>not</b> stored:
     *     the caller has nothing cached in that path, and answering
     *     {@code AVAILABLE} would serve an empty tree as though it were the truth.
     */
    private boolean fetchAndStore(UUID sourceId, UUID seriesId, String externalId) {
        // Single flight. `putIfAbsent` rather than `computeIfAbsent`: the work below
        // is slow, and holding a bin lock across a network call would block every
        // other series that hashes to it.
        if (inFlight.putIfAbsent(seriesId, Boolean.TRUE) != null) {
            log.debug("Tree of series {} is already being fetched", seriesId);
            return false;
        }

        Semaphore permits = sourcePermits.get(sourceId,
                id -> new Semaphore(MAX_CONCURRENT_PER_SOURCE));
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.info("Source {}: too many tree fetches in flight, refusing one", sourceId);
                return false;
            }
            return fetch(sourceId, seriesId, externalId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (acquired) {
                permits.release();
            }
            inFlight.remove(seriesId);
        }
    }

    private boolean fetch(UUID sourceId, UUID seriesId, String externalId) {
        SourceRepository.SourceRow source = sources.findForIngestion(sourceId).orElse(null);
        if (source == null || source.kind() != SourceKind.XTREAM) {
            return false;
        }

        try {
            byte[] sealed = sources.findSealedPassword(sourceId).orElse(null);
            if (sealed == null) {
                return false;
            }

            List<XtreamClient.XtreamSeason> tree = xtream.fetchSeriesInfo(
                    source.host(), source.username(), cipher.open(sealed), externalId);
            if (tree == null) {
                // The panel answered with something unreadable. Not stamped: that
                // is a moment rather than a fact, and the next open tries again.
                return false;
            }

            catalogWrites.replaceTree(seriesId, sourceId, toUpserts(tree));
            return true;
        } catch (IngestionException e) {
            log.info("Could not fetch the tree of series {}: {}", seriesId, e.code());
            return false;
        } catch (RuntimeException e) {
            log.warn("Unexpected failure fetching the tree of series {}", seriesId, e);
            return false;
        }
    }

    private static List<CatalogWriteRepository.SeasonUpsert> toUpserts(
            List<XtreamClient.XtreamSeason> tree) {
        List<CatalogWriteRepository.SeasonUpsert> seasons = new ArrayList<>(tree.size());
        for (XtreamClient.XtreamSeason season : tree) {
            List<CatalogWriteRepository.EpisodeUpsert> episodes =
                    new ArrayList<>(season.episodes().size());
            for (XtreamClient.XtreamEpisode episode : season.episodes()) {
                episodes.add(new CatalogWriteRepository.EpisodeUpsert(
                        episode.externalId(),
                        episode.seasonNumber(),
                        episode.episodeNumber(),
                        episode.name(),
                        episode.durationSeconds(),
                        episode.plot(),
                        episode.streamUrl(),
                        episode.containerExtension()));
            }
            seasons.add(new CatalogWriteRepository.SeasonUpsert(
                    season.seasonNumber(), season.episodeCount(), season.posterUrl(), episodes));
        }
        return seasons;
    }

    /** Visible for tests: the freshness window this source applies. */
    static Duration treeTtl() {
        return TREE_TTL;
    }

}
