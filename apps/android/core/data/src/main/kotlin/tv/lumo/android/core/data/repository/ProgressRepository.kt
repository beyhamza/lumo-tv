package tv.lumo.android.core.data.repository

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.map
import tv.lumo.android.core.data.model.EpisodeProgress
import tv.lumo.android.core.data.model.WatchProgress
import tv.lumo.android.core.data.model.WatchReport
import tv.lumo.android.core.data.valueOrNull
import tv.lumo.android.network.generated.api.UserdataApi
import tv.lumo.android.network.generated.model.HideProgressRequest
import tv.lumo.android.network.generated.model.PlaybackProgress
import tv.lumo.android.network.generated.model.ProgressItemType
import tv.lumo.android.network.generated.model.SaveProgressRequest
import tv.lumo.android.network.generated.model.WatchedSegment

/**
 * Where the user stopped watching (US-13, S5-11).
 *
 * <h2>The one repository in this module that does not go through Room</h2>
 *
 * Every other read here is cache-first, and this one is deliberately not. A
 * position is **written on one device and read on another** — that is the whole
 * point of asking the server rather than remembering locally — and a device
 * holding its own copy would have to merge two positions that disagree, offline,
 * with no idea which is newer.
 *
 * What it costs: a resume rail is empty on a train, and a film opened offline
 * starts at the beginning. Both are visible, neither is wrong, and both are far
 * better than resuming somebody twenty minutes into a film they finished on the
 * television last night.
 *
 * <h2>`item_ref` is our own `VodItem.id`</h2>
 *
 * Decided in `S5-11` and written into the contract rather than left to each
 * client: a rail has to turn these rows back into films with posters, and
 * `GET /sources/{id}/vod?ids=` is the only operation that does.
 *
 * <h2>There is no live channel here, and it is not a rule this file enforces</h2>
 *
 * `ProgressItemType` has no `LIVE` value, so a progress row for a channel is not
 * something a caller can express — it is a compile error, not a runtime check.
 * What a channel gets instead is `PlaybackRepository.recordWatched`.
 */
@Singleton
class ProgressRepository @Inject internal constructor(
    private val userdata: UserdataApi,
    private val calls: ApiCaller,
) {

    /**
     * Saves a position.
     *
     * An idempotent upsert, so calling it twice with the same values is one row
     * either way. The caller decides *when*; see `savable` on the player's side
     * for what is worth sending at all.
     *
     * The result is returned rather than swallowed so a caller can log it, but
     * there is nothing here for a user to act on: this is bookkeeping around
     * watching a film, not a step of it, and a player that interrupted itself to
     * report a failed save would be worse than a rail that misses a minute.
     */
    suspend fun save(
        sourceId: String,
        filmId: String,
        positionMs: Long,
        durationMs: Long?,
        report: WatchReport = WatchReport(),
    ): LumoResult<Unit> = calls.call {
        userdata.saveProgress(
            saveRequest(sourceId, ProgressItemType.VOD, filmId, positionMs, durationMs, report),
        )
    }.map { }

    /**
     * Saves where somebody is in an episode (S6-08).
     *
     * [save] with `EPISODE` in place of `VOD`, and that is the entire difference:
     * the server stores `item_ref` opaquely and never asks what it points at, so
     * this needed nothing new behind it.
     *
     * Two methods rather than one with a type parameter, because the two ids come
     * from two tables and a caller that could pass either would eventually pass a
     * film id here. The contract argues the same point about `item_ref` itself.
     */
    suspend fun saveEpisode(
        sourceId: String,
        episodeId: String,
        positionMs: Long,
        durationMs: Long?,
        report: WatchReport = WatchReport(),
    ): LumoResult<Unit> = calls.call {
        userdata.saveProgress(
            saveRequest(sourceId, ProgressItemType.EPISODE, episodeId, positionMs, durationMs, report),
        )
    }.map { }

    /**
     * Removes a film's card from "Continue watching" on every device (S12-04, CW-10).
     *
     * The position is kept — the sheet still offers to resume. The result is
     * returned and not swallowed: a removal the server did not confirm keeps the
     * card on screen and says so (CW-26), never a success announced on its own.
     */
    suspend fun hideFilm(sourceId: String, filmId: String): LumoResult<Unit> =
        hide(sourceId, ProgressItemType.VOD, filmId)

    /**
     * Removes a series' card: the server hides **every** episode of the series from
     * any one of them, so that the next episode does not take the card's place.
     * [episodeId] must have a saved row — see ResumableSeries.rowEpisodeId.
     */
    suspend fun hideEpisode(sourceId: String, episodeId: String): LumoResult<Unit> =
        hide(sourceId, ProgressItemType.EPISODE, episodeId)

    private suspend fun hide(sourceId: String, type: ProgressItemType, ref: String): LumoResult<Unit> =
        // `204 No Content`: `empty`, not `call` — the latter reads a missing body as
        // an unreadable answer, and a confirmed removal would be shown as refused.
        calls.empty {
            userdata.hideProgress(
                HideProgressRequest(sourceId = UUID.fromString(sourceId), itemType = type, itemRef = ref),
            )
        }

    /**
     * The saved position of one film, or null.
     *
     * The three filters together, because the contract says that combination
     * yields at most one row — and it is exactly why it exists. Asked once, when
     * a film's screen opens; a player that asked again on every frame would be
     * polling the server for a number it already has.
     */
    suspend fun of(sourceId: String, filmId: String): WatchProgress? =
        calls.call {
            userdata.listProgress(
                sourceId = UUID.fromString(sourceId),
                itemType = ProgressItemType.VOD,
                itemRef = filmId,
                size = 1,
            )
        }.valueOrNull()?.items?.firstOrNull()?.asWatchProgress()

    /**
     * What to put in a "continue watching" rail: started, and not finished.
     *
     * The server returns them most recently updated first — *"which is also the
     * order a 'Continue watching' rail wants"* — so nothing is re-sorted here.
     * What is filtered is [WatchProgress.inContinue], the server's verdict
     * (S12-04, `adr/0012` ruling 4). The 30-second and 95 % rules used to be
     * applied here; three clients copying them is what the ADR removed.
     *
     * @param sourceId the source whose rows are wanted, or null for the account's.
     *   Asked of the server rather than filtered afterwards (US-018): a page of the
     *   account's rows, cut at [limit] and *then* reduced to one source, comes back
     *   short — or empty — for somebody whose other subscription is the one they
     *   watched last night.
     * @return a failure as a failure (S11-05): an outage is not "nothing in
     *   progress", and a rail that turned one into the other emptied itself on
     *   every blip of the network.
     */
    suspend fun continueWatching(
        sourceId: String? = null,
        limit: Int = RAIL_SIZE,
    ): LumoResult<List<WatchProgress>> =
        calls.call {
            userdata.listProgress(
                sourceId = sourceId?.let(UUID::fromString),
                itemType = ProgressItemType.VOD,
                size = limit * PAGE_FACTOR,
            )
        }.map { page ->
            page.items
                .map { it.asWatchProgress() }
                // The server's verdict, and nothing else (S12-04, dr/0012 ruling 4):
                // hidden, finished and not-yet-30-seconds are all out.
                .filter { it.inContinue }
                .take(limit)
        }

    /**
     * Every episode somebody has started, most recently touched first (S6-08).
     *
     * <h2>Finished rows are kept, and that is the difference from [continueWatching]</h2>
     *
     * A **film** past the threshold leaves the rail: there is nothing after it. An
     * **episode** past the threshold is what puts the *next* one in the rail, so
     * dropping it here would silently end every series at the episode somebody
     * actually finished — the worst possible moment.
     *
     * What is dropped is decided one layer up, by `SeriesRepository.resumable`,
     * which is the only place that knows whether a finished episode has a
     * successor. That is the whole reason this returns rows rather than cards.
     *
     * @param sourceId as for [continueWatching], and for the same reason.
     * @return a failure as a failure, as [continueWatching] does.
     */
    suspend fun episodesInProgress(
        sourceId: String? = null,
        limit: Int = RAIL_SIZE,
    ): LumoResult<List<EpisodeProgress>> =
        calls.call {
            userdata.listProgress(
                sourceId = sourceId?.let(UUID::fromString),
                itemType = ProgressItemType.EPISODE,
                size = limit * PAGE_FACTOR,
            )
        }.map { page -> page.items.map { it.asEpisodeProgress() } }

    private companion object {
        /**
         * How many the rail holds, and why it is asked for twice over.
         *
         * Finished films are filtered on this side, so a page of exactly [limit]
         * rows can come back with half of them dropped and leave a rail that
         * looks short for no reason. Asking for twice as many costs one response
         * and makes that ordinary case invisible.
         */
        const val RAIL_SIZE = 12

        /**
         * Rows asked for per card. Four since S12-04: rows the server does not put
         * in the rail — hidden, finished, under 30 s — still come back, and a page of
         * them must not leave the rail short.
         */
        const val PAGE_FACTOR = 4
    }
}

private fun saveRequest(
    sourceId: String,
    type: ProgressItemType,
    ref: String,
    positionMs: Long,
    durationMs: Long?,
    report: WatchReport,
) = SaveProgressRequest(
    sourceId = UUID.fromString(sourceId),
    itemType = type,
    itemRef = ref,
    positionMs = positionMs,
    durationMs = durationMs,
    // Empty is omitted rather than sent: the field is optional, and an older
    // server reads its absence the way it always did.
    watchedSegments = report.segments
        .take(MAX_SEGMENTS)
        .map { (start, end) -> WatchedSegment(startedAt = start.asDateTime(), endedAt = end.asDateTime()) }
        .ifEmpty { null },
    observedAt = report.observedAtMs?.asDateTime(),
    sessionStartedAt = report.sessionStartedAtMs?.asDateTime(),
)

/** SaveProgressRequest.watched_segments.maxItems. */
private const val MAX_SEGMENTS = 20

private fun Long.asDateTime(): OffsetDateTime =
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)

private fun PlaybackProgress.asWatchProgress() = WatchProgress(
    sourceId = sourceId.toString(),
    filmId = itemRef,
    positionMs = positionMs,
    durationMs = durationMs,
    updatedAtMillis = updatedAt.toInstant().toEpochMilli(),
    inContinue = inContinue,
    hidden = hidden,
)

private fun PlaybackProgress.asEpisodeProgress() = EpisodeProgress(
    sourceId = sourceId.toString(),
    // The same opaque field, read as what an `EPISODE` row puts in it. The
    // contract settles that this is an `Episode.id`; nothing here re-decides it.
    episodeId = itemRef,
    positionMs = positionMs,
    durationMs = durationMs,
    updatedAtMillis = updatedAt.toInstant().toEpochMilli(),
    inContinue = inContinue,
    hidden = hidden,
)
