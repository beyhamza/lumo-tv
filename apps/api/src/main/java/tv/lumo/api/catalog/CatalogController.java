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

    public CatalogController(CatalogReadRepository catalog, SourceRepository sources) {
        this.catalog = catalog;
        this.sources = sources;
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
     * The only operation that emits a stream URL.
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

        if (!SourceStatus.READY.getValue().equals(row.sourceStatus())) {
            throw ApiException.conflict(ErrorCode.SOURCE_NOT_READY,
                    "The source has not finished ingesting");
        }
        if (row.sourceExpiresAt() != null && row.sourceExpiresAt().isBefore(OffsetDateTime.now())) {
            throw ApiException.conflict(ErrorCode.SOURCE_EXPIRED,
                    "The subscription with the provider has expired");
        }

        PlaybackInfo playback = new PlaybackInfo(id, row.streamUrl());
        // Echoed so the player can explain a stream the panel refuses (US-09).
        playback.setMaxConnections(row.maxConnections());
        // Nothing is logged here. This response body is the single most sensitive
        // one this API produces (AGENTS.md §5).
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
