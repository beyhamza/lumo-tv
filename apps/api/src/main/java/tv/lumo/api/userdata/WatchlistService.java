package tv.lumo.api.userdata;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tv.lumo.api.generated.model.AddWatchlistItemRequest;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.WatchlistContentType;
import tv.lumo.api.generated.model.WatchlistItem;
import tv.lumo.api.generated.model.WatchlistPage;
import tv.lumo.api.shared.error.ApiException;

/**
 * The watchlist: films and series saved on purpose, shared across devices
 * (US-022, ADR 0011).
 *
 * <p><b>A retry is not a new intention.</b> Each write carries a key the client
 * generated for that intention. The first request under a key runs and records
 * its answer; every later one under the same key gets that answer back and
 * changes nothing (ruling 6). Without this, an add whose response was lost and
 * which is retried after another device removed the item would put it back —
 * the replay would overwrite a newer intention (WL-12).
 *
 * <p>The key is claimed and the outcome recorded in the same transaction as the
 * write. A request that fails rolls the key back with it: it was not accepted,
 * so nothing is recorded and its retry runs as if for the first time.
 */
@Service
public class WatchlistService {

    /** How long a key is remembered. The contract states the same figure. */
    static final Duration KEY_RETENTION = Duration.ofHours(24);

    private static final int MAX_PAGE_SIZE = 200;

    private final WatchlistRepository watchlist;
    private final ObjectMapper objectMapper;

    public WatchlistService(WatchlistRepository watchlist, ObjectMapper objectMapper) {
        this.watchlist = watchlist;
        this.objectMapper = objectMapper;
    }

    /** What a write answers: its status, and for an add, the item. */
    public record Outcome(HttpStatus status, WatchlistItem item) {}

    public WatchlistPage list(UUID userId, UUID sourceId, WatchlistContentType type,
                              Integer page, Integer size) {
        int pageIndex = page == null ? 0 : Math.max(0, page);
        int pageSize = size == null ? 50 : Math.clamp(size, 1, MAX_PAGE_SIZE);

        List<WatchlistItem> items = watchlist.findPage(userId, sourceId, type, pageIndex, pageSize);
        long total = watchlist.count(userId, sourceId, type);
        return new WatchlistPage(items, pageIndex, pageSize, total,
                (int) Math.ceil((double) total / pageSize),
                watchlist.findContentTypes(userId, sourceId));
    }

    /**
     * Adds a film or a series. {@code 201} when it was added, {@code 200} when the
     * account already had it — in which case its rank does not move.
     */
    @Transactional
    public Outcome add(UUID userId, UUID key, AddWatchlistItemRequest request) {
        String fingerprint = "ADD " + request.getContentType().getValue() + " " + request.getContentId();
        Outcome replay = replayed(userId, key, fingerprint);
        if (replay != null) {
            return replay;
        }

        WatchlistRepository.CatalogueEntry entry = watchlist
                .findOwnedContent(request.getContentType(), request.getContentId(), userId)
                .orElseThrow(() -> notFound(request.getContentType()));

        boolean inserted = watchlist.insertIfAbsent(userId, request.getContentType(), entry).isPresent();
        WatchlistItem item = watchlist.findItemByIdentity(userId, request.getContentType(), entry)
                .orElseThrow(() -> new IllegalStateException("Watchlist item vanished inside its own transaction"));

        HttpStatus status = inserted ? HttpStatus.CREATED : HttpStatus.OK;
        watchlist.recordOutcome(userId, key, status.value(), objectMapper.writeValueAsString(item));
        return new Outcome(status, item);
    }

    /**
     * Removes an item by its own id, so an unavailable one goes as easily as any
     * other (WL-03). Progress is not touched.
     */
    @Transactional
    public void remove(UUID userId, UUID key, UUID itemId) {
        if (replayed(userId, key, "REMOVE " + itemId) != null) {
            return;
        }
        if (watchlist.delete(userId, itemId) == 0) {
            throw ApiException.notFound(ErrorCode.WATCHLIST_ITEM_NOT_FOUND,
                    "No such item in this account's watchlist");
        }
        watchlist.recordOutcome(userId, key, HttpStatus.NO_CONTENT.value(), null);
    }

    /**
     * Claims the key, or returns what was answered under it.
     *
     * @return null when the key is new and this request should run
     */
    private Outcome replayed(UUID userId, UUID key, String fingerprint) {
        watchlist.purgeExpiredIntentions(userId, KEY_RETENTION);
        if (watchlist.claim(userId, key, fingerprint)) {
            return null;
        }
        WatchlistRepository.RecordedIntention recorded = watchlist.findIntention(userId, key)
                .orElseThrow(() -> new IllegalStateException("Claimed key has no record"));
        if (!recorded.fingerprint().equals(fingerprint)) {
            throw ApiException.conflict(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used for a different request");
        }
        WatchlistItem item = recorded.body() == null
                ? null
                : objectMapper.readValue(recorded.body(), WatchlistItem.class);
        return new Outcome(HttpStatus.valueOf(recorded.status()), item);
    }

    private static ApiException notFound(WatchlistContentType type) {
        return switch (type) {
            case VOD -> ApiException.notFound(ErrorCode.VOD_ITEM_NOT_FOUND,
                    "No such film on a source owned by the caller");
            case SERIES -> ApiException.notFound(ErrorCode.SERIES_NOT_FOUND,
                    "No such series on a source owned by the caller");
        };
    }
}
