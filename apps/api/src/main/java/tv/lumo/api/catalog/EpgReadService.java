package tv.lumo.api.catalog;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tv.lumo.api.generated.model.EpgAttemptStatus;
import tv.lumo.api.generated.model.EpgChannelProgrammes;
import tv.lumo.api.generated.model.EpgGrid;
import tv.lumo.api.generated.model.EpgImportStatus;
import tv.lumo.api.generated.model.EpgMappingStatus;
import tv.lumo.api.generated.model.EpgProgramme;
import tv.lumo.api.generated.model.EpgProgrammeList;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.shared.error.ApiException;
import tv.lumo.api.source.SourceRepository;

/**
 * The two guide reads — one channel, and a batch of channels of one source —
 * behind the two operations of the contract (US-16, lot C1).
 *
 * <p><b>Both run in one read-only {@code REPEATABLE READ} transaction</b>, and
 * that is the guarantee D3 asks for rather than a tuning choice. PostgreSQL takes
 * the snapshot at the transaction's first statement, so the import record and
 * the programmes come from the same instant: a {@code SUCCEEDED} read a moment
 * before an attempt started cannot be answered next to rows that attempt has
 * since rewritten. No guide snapshot, no versioning and no shared cache are
 * involved — one isolation level on one transaction is the whole mechanism.
 *
 * <p><b>The batch answer is complete or it is an error.</b> Two ceilings, checked
 * before anything is sent: {@link #MAX_OCCURRENCES} programme appearances and
 * {@link #MAX_BYTES} of UTF-8 JSON. Nothing is trimmed to fit under either; the
 * client asks for less. The bench that fixed the two numbers is
 * {@code docs/releases/0.2.0/s9-00-epg-bench.md}, and they are revised by a
 * contract change, not by configuration — which is why they are constants.
 *
 * <p>Nothing here contacts the provider. A read that reached the user's own
 * server on every grid scroll would be the traffic that gets them banned.
 */
@Service
public class EpgReadService {

    /** Programme appearances per answer; a programme under N channels counts N. */
    public static final int MAX_OCCURRENCES = 5_000;

    /** Uncompressed UTF-8 JSON of the whole answer, envelope included: 4 MiB. */
    public static final int MAX_BYTES = 4 * 1024 * 1024;

    /** Distinct channels per batch — the same bound as {@code ids} on the listings. */
    public static final int MAX_CHANNELS = 100;

    /** The widest window either operation accepts. Four days, as it always was. */
    public static final Duration MAX_WINDOW = Duration.ofHours(96);

    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);

    private final SourceRepository sources;
    private final CatalogReadRepository catalog;
    private final ObjectMapper mapper;

    public EpgReadService(SourceRepository sources, CatalogReadRepository catalog, ObjectMapper mapper) {
        this.sources = sources;
        this.catalog = catalog;
        this.mapper = mapper;
    }

    /**
     * The grid's read: {@code GET /sources/{id}/epg}.
     *
     * <p>In order, and the order is part of the contract: the source is
     * resolved for the caller ({@code SOURCE_NOT_FOUND}), then every requested
     * channel is checked to be one of <em>this</em> source's
     * ({@code CHANNEL_NOT_FOUND}, whole batch, no detail), and only then is
     * anything counted or the import record exposed. An answer that revealed
     * the size of a batch before refusing it would be telling a caller
     * something about channels that are not theirs.
     *
     * <p><b>The two ceilings are checked in the cheapest order.</b> The
     * programme query is bounded at cap + 1 rows, which alone proves the first
     * ceiling broken; then occurrences are counted while the rows are placed
     * under their channels; then, and only for an answer that passed both, the
     * whole {@code EpgGrid} is serialised with the application's own mapper to
     * measure its bytes. <b>That answer is serialised twice</b> — once here to
     * measure, once by Spring to send — and the second pass is accepted rather
     * than avoided: the generated interface returns {@code ResponseEntity<EpgGrid>},
     * and handing Spring the bytes would mean a controller that no longer
     * implements the contract. Under 4 MiB the second pass is milliseconds
     * (the bench measured ~20 ms for an answer larger than that), and the
     * mapper is the same one, so the two byte counts cannot disagree.
     *
     * @param channelIds already validated by {@link #requireBatch}: 1 to 100,
     *                   distinct, none null
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public EpgGrid readGrid(UUID sourceId, UUID userId, List<UUID> channelIds, Window window) {
        SourceRepository.EpgImportRow importRow = sources.findEpgImport(sourceId, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SOURCE_NOT_FOUND,
                        "No such source on this account"));

        Map<UUID, String> tvgIdByChannel = catalog.findChannelTvgIds(sourceId, userId, channelIds);
        for (UUID channelId : channelIds) {
            if (!tvgIdByChannel.containsKey(channelId)) {
                // Deliberately silent about which one: a batch that named it would
                // let a caller confirm channel ids of another source one by one.
                throw ApiException.notFound(ErrorCode.CHANNEL_NOT_FOUND,
                        "At least one requested channel is not a channel of this source");
            }
        }

        // Distinct, non-blank: a tvg_id shared by several channels is read once
        // and placed under each of them below.
        Set<String> tvgIds = new HashSet<>();
        for (String tvgId : tvgIdByChannel.values()) {
            if (tvgId != null && !tvgId.isBlank()) {
                tvgIds.add(tvgId);
            }
        }

        List<EpgProgramme> rows = catalog.findProgrammesByTvgIds(sourceId, userId,
                List.copyOf(tvgIds), window.from(), window.to(), MAX_OCCURRENCES + 1);
        if (rows.size() > MAX_OCCURRENCES) {
            // Each row is at least one occurrence, so this is already decided.
            throw tooLarge();
        }

        // The rows arrive in (starts_at, id) order for the whole source, so each
        // per-tvg_id sublist is in that order too — no second sort.
        Map<String, List<EpgProgramme>> byTvgId = new LinkedHashMap<>();
        for (EpgProgramme row : rows) {
            byTvgId.computeIfAbsent(row.getTvgId(), k -> new ArrayList<>()).add(row);
        }

        List<EpgChannelProgrammes> channels = new ArrayList<>(channelIds.size());
        int occurrences = 0;
        for (UUID channelId : channelIds) {
            String tvgId = tvgIdByChannel.get(channelId);
            if (tvgId == null || tvgId.isBlank()) {
                channels.add(new EpgChannelProgrammes(channelId, EpgMappingStatus.NO_TVG_ID, List.of()));
                continue;
            }
            List<EpgProgramme> programmes = byTvgId.getOrDefault(tvgId, List.of());
            occurrences += programmes.size();
            if (occurrences > MAX_OCCURRENCES) {
                throw tooLarge();
            }
            // The same EpgProgramme instances under every channel that shares
            // the tvg_id: serialisation repeats them, which is what the contract
            // says happens, and nothing downstream mutates them.
            channels.add(new EpgChannelProgrammes(channelId, EpgMappingStatus.MAPPED, programmes));
        }

        EpgGrid grid = new EpgGrid(sourceId, window.from(), window.to(), window.generatedAt(),
                toStatus(importRow), channels);

        // writeValueAsBytes is UTF-8, which is what the contract's ceiling counts.
        if (mapper.writeValueAsBytes(grid).length > MAX_BYTES) {
            throw tooLarge();
        }
        return grid;
    }

    /**
     * The day view's read: {@code GET /channels/{id}/epg}, unchanged in what it
     * returns and now carrying the same import record as the grid.
     *
     * <p>Same transaction shape as {@link #readGrid}, for the same reason: the
     * programmes and the record they are judged by come from one snapshot.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public EpgProgrammeList readChannel(UUID channelId, UUID userId, Window window) {
        // Confirms ownership without loading the stream URL, and yields the
        // source the import record hangs off.
        UUID sourceId = catalog.findOwnedChannelSourceId(channelId, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.CHANNEL_NOT_FOUND,
                        "No such channel on a source owned by the caller"));

        // A channel with no tvg_id, or a source with no guide, yields an empty
        // list rather than an error; `epg` is what says which.
        EpgProgrammeList list = new EpgProgrammeList(
                catalog.findProgrammes(channelId, userId, window.from(), window.to()));
        // Cannot be empty inside this snapshot: the channel was just found on
        // this source for this user. The orElseThrow is for the type, not a case.
        sources.findEpgImport(sourceId, userId)
                .map(EpgReadService::toStatus)
                .ifPresent(list::setEpg);
        return list;
    }

    // ---- validation, shared by both operations ------------------------------

    /**
     * The batch, as the contract bounds it: 1 to 100 identifiers, distinct.
     *
     * <p>Spring has already turned each value into a {@link UUID} — a malformed
     * one never reaches here, it is a {@code 400} from the binder — and the
     * generated interface's {@code @Size} refuses the counts before this runs.
     * Both checks are repeated anyway: this is the place that says what the
     * rule is, and a rule enforced only by a generated annotation is a rule
     * nobody can read.
     *
     * <p>An empty value on the wire ({@code ?channelIds=}) binds as a null
     * element rather than as an absent parameter, which is why null elements
     * are named here: they are the empty-list case, not a programming error.
     */
    public static List<UUID> requireBatch(List<UUID> channelIds) {
        if (channelIds == null || channelIds.isEmpty() || channelIds.contains(null)) {
            throw ApiException.validation(
                    "channelIds must hold between 1 and " + MAX_CHANNELS + " channel identifiers",
                    List.of());
        }
        if (channelIds.size() > MAX_CHANNELS) {
            throw ApiException.validation(
                    "channelIds must not hold more than " + MAX_CHANNELS + " channel identifiers",
                    List.of());
        }
        if (new HashSet<>(channelIds).size() != channelIds.size()) {
            // A duplicate is refused rather than collapsed: the contract promises
            // one entry per identifier in request order, and two entries for one
            // channel would either duplicate the row or break the order.
            throw ApiException.validation("channelIds must not contain the same identifier twice",
                    List.of());
        }
        return List.copyOf(channelIds);
    }

    /**
     * The effective window and the instant it was decided.
     *
     * <p>Defaults are what the contract says: {@code from} is the server's
     * clock, read <em>once</em> — the same instant becomes {@code generated_at}
     * — and {@code to} is {@code from} + 24 h. The rule is
     * {@code 0 < to - from <= 96 h}: an empty window is refused, as the server
     * always did before the contract said so, and 96 h is the four days the
     * single-channel operation has had since sprint 3.
     *
     * @param from inclusive, or null for now
     * @param to   exclusive, or null for {@code from} + 24 h
     */
    public static Window resolveWindow(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start = from == null ? now : from;
        OffsetDateTime end = to == null ? start.plus(DEFAULT_WINDOW) : to;

        if (!end.isAfter(start)) {
            throw ApiException.validation("'to' must be strictly after 'from'", List.of());
        }
        if (Duration.between(start, end).compareTo(MAX_WINDOW) > 0) {
            throw ApiException.validation(
                    "The window must not exceed " + MAX_WINDOW.toHours() + " hours", List.of());
        }
        return new Window(start, end, now);
    }

    /**
     * @param from        inclusive lower bound, defaults applied
     * @param to          exclusive upper bound, defaults applied
     * @param generatedAt the server's clock when the window was resolved; goes out
     *                    as {@code generated_at}, and is the default {@code from}
     */
    public record Window(OffsetDateTime from, OffsetDateTime to, OffsetDateTime generatedAt) {
    }

    // ---- helpers ------------------------------------------------------------

    private static EpgImportStatus toStatus(SourceRepository.EpgImportRow row) {
        return new EpgImportStatus(row.configured(), row.lastSuccessAt(), row.attemptStartedAt(),
                row.attemptFinishedAt(), EpgAttemptStatus.fromValue(row.attemptStatus()));
    }

    private static ApiException tooLarge() {
        return ApiException.unprocessable(ErrorCode.EPG_WINDOW_TOO_LARGE,
                "This batch over this window exceeds " + MAX_OCCURRENCES
                        + " programmes or " + MAX_BYTES + " bytes; ask for fewer channels "
                        + "or a narrower window");
    }
}
