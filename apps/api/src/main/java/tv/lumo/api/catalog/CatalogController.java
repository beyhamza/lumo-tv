package tv.lumo.api.catalog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tv.lumo.api.auth.CurrentUser;
import tv.lumo.api.generated.api.CatalogApi;
import tv.lumo.api.generated.model.Category;
import tv.lumo.api.generated.model.CategoryList;
import tv.lumo.api.generated.model.Channel;
import tv.lumo.api.generated.model.ChannelPage;
import tv.lumo.api.generated.model.ContentType;
import tv.lumo.api.generated.model.EpgProgrammeList;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.PlaybackInfo;
import tv.lumo.api.generated.model.SourceStatus;
import tv.lumo.api.generated.model.Episode;
import tv.lumo.api.generated.model.EpisodePage;
import tv.lumo.api.generated.model.EpisodePlaybackInfo;
import tv.lumo.api.generated.model.Series;
import tv.lumo.api.generated.model.SeriesDetail;
import tv.lumo.api.generated.model.SeriesPage;
import tv.lumo.api.generated.model.VodItem;
import tv.lumo.api.generated.model.VodItemPage;
import tv.lumo.api.generated.model.VodPlaybackInfo;
import tv.lumo.api.shared.error.ApiException;
import tv.lumo.api.source.SourceRepository;

/**
 * Implements the generated {@code CatalogApi}.
 *
 * <p>Reads only. The catalogue is written by ingestion and by nothing else.
 */
@RestController
public class CatalogController implements CatalogApi {

    /** Matches the contract's cap; a client asking for more gets this. */
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_EPG_RANGE_DAYS = 4;

    private final CatalogReadRepository catalog;
    private final SourceRepository sources;
    private final VodPlotSource plots;
    private final SeriesTreeSource trees;

    public CatalogController(CatalogReadRepository catalog, SourceRepository sources,
                             VodPlotSource plots, SeriesTreeSource trees) {
        this.catalog = catalog;
        this.sources = sources;
        this.plots = plots;
        this.trees = trees;
    }

    @Override
    public ResponseEntity<CategoryList> listCategories(UUID id, ContentType contentType) {
        UUID userId = CurrentUser.requireUserId();
        requireReadableSource(id, userId);

        List<Category> categories = catalog.findCategories(id, userId, contentType);
        return ResponseEntity.ok(new CategoryList(categories));
    }

    @Override
    public ResponseEntity<ChannelPage> listChannels(UUID id, UUID categoryId, String q,
                                                    List<UUID> ids, Integer page, Integer size) {
        UUID userId = CurrentUser.requireUserId();
        requireReadableSource(id, userId);

        int pageIndex = page == null ? 0 : Math.max(0, page);
        // Capped server-side so a large catalogue cannot be pulled in one call.
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.clamp(size, 1, MAX_PAGE_SIZE);
        String search = (q == null || q.isBlank()) ? null : q.trim();
        // An empty list is read as "no filter", not as "restrict to nothing". The
        // contract's `ids` is how a client resolves identifiers it already holds,
        // and a caller holding none has no reason to be here — whereas `?ids=` on
        // the end of a URL is an ordinary accident, and answering an empty page to
        // it would look like a catalogue that lost its channels.
        List<UUID> wanted = (ids == null || ids.isEmpty()) ? null : ids;

        List<Channel> channels =
                catalog.findChannels(id, userId, categoryId, search, wanted, pageIndex, pageSize);
        long total = catalog.countChannels(id, userId, categoryId, search, wanted);

        ChannelPage result = new ChannelPage(channels, pageIndex, pageSize, total,
                (int) Math.ceil((double) total / pageSize));
        return ResponseEntity.ok(result);
    }

    /**
     * Films, in the same shape as channels down to the parameter names.
     *
     * <p>Deliberately a second method rather than a flag on {@link #listChannels}:
     * the two return different types, the client that calls one has no use for the
     * other's fields, and a `contentType` parameter would have made the response
     * shape depend on a query value.
     */
    @Override
    public ResponseEntity<VodItemPage> listVod(UUID id, UUID categoryId, String q,
                                               List<UUID> ids, Integer page, Integer size) {
        UUID userId = CurrentUser.requireUserId();
        requireReadableSource(id, userId);

        int pageIndex = page == null ? 0 : Math.max(0, page);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.clamp(size, 1, MAX_PAGE_SIZE);
        String search = (q == null || q.isBlank()) ? null : q.trim();
        // Empty means "no filter", as on the channel listing and for the same
        // reason: `?ids=` on the end of a URL is an ordinary accident, and
        // answering an empty page to it would look like a catalogue that lost its
        // films.
        List<UUID> wanted = (ids == null || ids.isEmpty()) ? null : ids;

        List<VodItem> items =
                catalog.findVod(id, userId, categoryId, search, wanted, pageIndex, pageSize);
        long total = catalog.countVod(id, userId, categoryId, search, wanted);

        VodItemPage result = new VodItemPage(items, pageIndex, pageSize, total,
                (int) Math.ceil((double) total / pageSize));
        return ResponseEntity.ok(result);
    }

    /**
     * One film, with its synopsis — fetched from the provider the first time, and
     * cached from then on.
     *
     * <p>The listing deliberately carries no synopsis, because on an Xtream panel
     * each one costs a call to the user's own server. This is where that call
     * happens, once per film, at the moment somebody asks to see it.
     *
     * <p><b>A provider that cannot answer is not an error here.</b> The film comes
     * back with everything already known and a null {@code plot}; playback does not
     * depend on this operation, and a screen that refused to open because a
     * synopsis was missing would be trading the product for a comfort.
     */
    @Override
    public ResponseEntity<VodItem> getVodItem(UUID id) {
        UUID userId = CurrentUser.requireUserId();

        CatalogReadRepository.VodDetail detail = catalog.findVodOwnedBy(id, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.VOD_ITEM_NOT_FOUND,
                        "No such film on a source owned by the caller"));

        VodItem item = detail.item();
        if (detail.plotFetchedAt() != null) {
            // Asked before. Null here means the provider had nothing, and asking
            // again would spend their capacity on a settled question.
            item.setPlot(detail.plot());
        } else {
            item.setPlot(plots.fetchAndCachePlot(detail.sourceId(), id, detail.externalId()));
        }

        return ResponseEntity.ok(item);
    }

    /**
     * One page of series.
     *
     * <p>The film listing again, and deliberately so: same parameters, same caps,
     * same treatment of an empty {@code ids}.
     *
     * <p><b>An M3U source answers an empty page</b>, and that is not a special
     * case in this method — it is simply that nothing ever wrote a series row for
     * one. Series are an Xtream feature (ADR 0010), and a client explains the
     * absence on the source's own page rather than as an empty tab.
     */
    @Override
    public ResponseEntity<SeriesPage> listSeries(UUID id, UUID categoryId, String q,
                                                 List<UUID> ids, Integer page, Integer size) {
        UUID userId = CurrentUser.requireUserId();
        requireReadableSource(id, userId);

        int pageIndex = page == null ? 0 : Math.max(0, page);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.clamp(size, 1, MAX_PAGE_SIZE);
        String search = (q == null || q.isBlank()) ? null : q.trim();
        List<UUID> wanted = (ids == null || ids.isEmpty()) ? null : ids;

        List<Series> items =
                catalog.findSeries(id, userId, categoryId, search, wanted, pageIndex, pageSize);
        long total = catalog.countSeries(id, userId, categoryId, search, wanted);

        return ResponseEntity.ok(new SeriesPage(items, pageIndex, pageSize, total,
                (int) Math.ceil((double) total / pageSize)));
    }

    /**
     * Episodes by identifier — a resolver, and the one operation here where
     * {@code ids} is required.
     *
     * <p>It exists for a "continue watching" rail: {@code GET /me/progress} returns
     * identifiers, and something has to turn them back into something a screen can
     * draw, in one request rather than one per row.
     *
     * <p><b>Required rather than optional</b>, unlike every other {@code ids} in
     * this controller, because without it this would list every episode of every
     * series of a source. Nobody has a use for that page, and a resolver that
     * cannot be used as a crawler is one fewer thing to rate-limit.
     */
    @Override
    public ResponseEntity<EpisodePage> resolveEpisodes(UUID id, List<UUID> ids,
                                                       Integer page, Integer size) {
        UUID userId = CurrentUser.requireUserId();
        requireReadableSource(id, userId);

        int pageIndex = page == null ? 0 : Math.max(0, page);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.clamp(size, 1, MAX_PAGE_SIZE);

        List<Episode> items = catalog.findEpisodes(id, userId, ids, pageIndex, pageSize);
        long total = catalog.countEpisodes(id, userId, ids);

        return ResponseEntity.ok(new EpisodePage(items, pageIndex, pageSize, total,
                (int) Math.ceil((double) total / pageSize)));
    }

    /**
     * One series and its tree.
     *
     * <p><b>The only operation in this API that can be slow on its first call.</b>
     * The tree comes from {@code get_series_info}, one call to the user's own
     * server per series, made when somebody opens one and cached afterwards.
     *
     * <p><b>The cache expires, unlike a film's synopsis.</b> A plot never changes;
     * a series in production gains an episode a week. So the stamp is compared
     * against a validity window rather than merely checked for presence — which is
     * why {@code series} carries {@code tree_fetched_at} and no boolean beside it.
     *
     * <p><b>A stale tree is served while it refreshes.</b> The viewer sees the
     * episodes they know and the new one appears when the answer arrives. The
     * opposite — a waiting screen over data already held — is a regression for a
     * feature whose whole point is convenience.
     *
     * <p><b>The row is read twice, and the second read is not redundant.</b> A
     * fetch that just ran wrote the tree <em>and</em> the synopsis; the row this
     * method opened with predates both. Serving it would show an empty synopsis
     * beside a full tree, once, on exactly the open that paid for them.
     */
    @Override
    public ResponseEntity<SeriesDetail> getSeries(UUID id) {
        UUID userId = CurrentUser.requireUserId();

        CatalogReadRepository.SeriesDetailRow row = catalog.findSeriesOwnedBy(id, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SERIES_NOT_FOUND,
                        "No such series on a source owned by the caller"));

        SeriesTreeSource.Availability availability = trees.ensureTree(
                row.sourceId(), id, row.externalId(), row.treeFetchedAt());

        if (availability == SeriesTreeSource.Availability.UNAVAILABLE) {
            // 503 and not 404. The series exists; what failed is the call that
            // fills in its seasons. A client that said "series not found" here
            // would send somebody looking for a series their provider still has.
            throw ApiException.unavailable(ErrorCode.SOURCE_UNREACHABLE,
                    "The provider could not supply this series' episodes");
        }

        // Re-read after the fetch: a tree written a moment ago also wrote the
        // synopsis, and the row in hand predates both.
        CatalogReadRepository.SeriesDetailRow current = catalog.findSeriesOwnedBy(id, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SERIES_NOT_FOUND,
                        "No such series on a source owned by the caller"));

        Series series = current.series();
        series.setPlot(current.plot());

        return ResponseEntity.ok(new SeriesDetail(series, catalog.findTree(id)));
    }

    /**
     * The third and last operation that emits a stream URL.
     *
     * <p>The film's, one level deeper. The three refusals are the same three and
     * mean the same things: an episode counts against a subscription's
     * simultaneous-stream ceiling exactly as a channel and a film do.
     */
    @Override
    public ResponseEntity<EpisodePlaybackInfo> getEpisodePlayback(UUID id) {
        UUID userId = CurrentUser.requireUserId();

        CatalogReadRepository.PlaybackRow row = catalog.findEpisodeStreamUrlOwnedBy(id, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.EPISODE_NOT_FOUND,
                        "No such episode on a source owned by the caller"));

        requirePlayableSource(row);

        EpisodePlaybackInfo playback = new EpisodePlaybackInfo(id, row.streamUrl());
        playback.setMaxConnections(row.maxConnections());
        // Nothing is logged here, exactly as for a channel and a film (AGENTS.md §5).
        return ResponseEntity.ok(playback);
    }

    /**
     * One of the two operations that emit a stream URL.
     *
     * <p>Ownership is enforced inside the query, and a channel the caller does not
     * own is reported as 404 rather than 403 so the endpoint cannot be used to
     * probe for channel ids.
     */
    @Override
    public ResponseEntity<PlaybackInfo> getChannelPlayback(UUID id) {
        UUID userId = CurrentUser.requireUserId();

        CatalogReadRepository.PlaybackRow row = catalog.findStreamUrlOwnedBy(id, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.CHANNEL_NOT_FOUND,
                        "No such channel on a source owned by the caller"));

        requirePlayableSource(row);

        PlaybackInfo playback = new PlaybackInfo(id, row.streamUrl());
        // Echoed so the player can explain a stream the panel refuses (US-09).
        playback.setMaxConnections(row.maxConnections());
        // Nothing is logged here. This response body is the single most sensitive
        // one this API produces (AGENTS.md §5).
        return ResponseEntity.ok(playback);
    }

    /**
     * The same, for a film.
     *
     * <p>The three refusals are the channel's three, and they mean the same
     * things: a subscription's simultaneous-stream ceiling counts a film exactly
     * as it counts a channel, so the guard is shared rather than re-argued.
     */
    @Override
    public ResponseEntity<VodPlaybackInfo> getVodPlayback(UUID id) {
        UUID userId = CurrentUser.requireUserId();

        CatalogReadRepository.PlaybackRow row = catalog.findVodStreamUrlOwnedBy(id, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.VOD_ITEM_NOT_FOUND,
                        "No such film on a source owned by the caller"));

        requirePlayableSource(row);

        VodPlaybackInfo playback = new VodPlaybackInfo(id, row.streamUrl());
        playback.setMaxConnections(row.maxConnections());
        // Nothing is logged here, exactly as for a channel (AGENTS.md §5).
        return ResponseEntity.ok(playback);
    }

    @Override
    public ResponseEntity<EpgProgrammeList> getChannelEpg(UUID id, OffsetDateTime from, OffsetDateTime to) {
        UUID userId = CurrentUser.requireUserId();

        // Confirms ownership without loading the stream URL.
        catalog.findChannelTvgId(id, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.CHANNEL_NOT_FOUND,
                        "No such channel on a source owned by the caller"));

        OffsetDateTime start = from == null ? OffsetDateTime.now() : from;
        OffsetDateTime end = to == null ? start.plusDays(1) : to;

        if (!end.isAfter(start)) {
            throw ApiException.validation("'to' must be after 'from'", List.of());
        }
        if (start.plusDays(MAX_EPG_RANGE_DAYS).isBefore(end)) {
            throw ApiException.validation(
                    "The range must not exceed " + MAX_EPG_RANGE_DAYS + " days", List.of());
        }

        // A channel with no tvg_id, or a source with no guide, yields an empty
        // list rather than an error.
        return ResponseEntity.ok(new EpgProgrammeList(catalog.findProgrammes(id, userId, start, end)));
    }

    /**
     * The two conflicts a source can raise at the moment of playback.
     *
     * <p>Shared by the channel and the film paths rather than written twice: they
     * are properties of the *source*, and a film hitting a different rule from a
     * channel on the same subscription would be a bug on whichever side was
     * changed last.
     */
    private void requirePlayableSource(CatalogReadRepository.PlaybackRow row) {
        if (!SourceStatus.READY.getValue().equals(row.sourceStatus())) {
            throw ApiException.conflict(ErrorCode.SOURCE_NOT_READY,
                    "The source has not finished ingesting");
        }
        if (row.sourceExpiresAt() != null && row.sourceExpiresAt().isBefore(OffsetDateTime.now())) {
            throw ApiException.conflict(ErrorCode.SOURCE_EXPIRED,
                    "The subscription with the provider has expired");
        }
    }

    /**
     * Resolves the source and refuses to list a catalogue that is not there yet.
     *
     * <p>Returning an empty list for a PENDING source would look identical to a
     * source that genuinely has no channels, and the client would stop polling.
     */
    private void requireReadableSource(UUID sourceId, UUID userId) {
        SourceRepository.SourceRow source = sources.findOwned(sourceId, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SOURCE_NOT_FOUND,
                        "No such source on this account"));

        if (source.status() != SourceStatus.READY) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SOURCE_NOT_READY,
                    "The source has not finished ingesting");
        }
    }
}
