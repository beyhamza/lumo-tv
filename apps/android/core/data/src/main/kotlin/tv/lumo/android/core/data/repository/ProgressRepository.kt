package tv.lumo.android.core.data.repository

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.map
import tv.lumo.android.core.data.model.EpisodeProgress
import tv.lumo.android.core.data.model.WatchProgress
import tv.lumo.android.core.data.valueOrNull
import tv.lumo.android.network.generated.api.UserdataApi
import tv.lumo.android.network.generated.model.PlaybackProgress
import tv.lumo.android.network.generated.model.ProgressItemType
import tv.lumo.android.network.generated.model.SaveProgressRequest

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
    ): LumoResult<Unit> = calls.call {
        userdata.saveProgress(
            SaveProgressRequest(
                sourceId = UUID.fromString(sourceId),
                itemType = ProgressItemType.VOD,
                itemRef = filmId,
                positionMs = positionMs,
                durationMs = durationMs,
            ),
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
    ): LumoResult<Unit> = calls.call {
        userdata.saveProgress(
            SaveProgressRequest(
                sourceId = UUID.fromString(sourceId),
                itemType = ProgressItemType.EPISODE,
                itemRef = episodeId,
                positionMs = positionMs,
                durationMs = durationMs,
            ),
        )
    }.map { }

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
     * What is filtered is [WatchProgress.finished], and that filter belongs on
     * this side: the threshold is a product decision, not a storage one, and the
     * server keeps the row so a client that changes its mind about 95 % does not
     * need a migration.
     */
    suspend fun continueWatching(limit: Int = RAIL_SIZE): List<WatchProgress> =
        calls.call { userdata.listProgress(itemType = ProgressItemType.VOD, size = limit * 2) }
            .valueOrNull()
            ?.items
            ?.map { it.asWatchProgress() }
            ?.filterNot { it.finished }
            ?.take(limit)
            .orEmpty()

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
     */
    suspend fun episodesInProgress(limit: Int = RAIL_SIZE): List<EpisodeProgress> =
        calls.call {
            userdata.listProgress(itemType = ProgressItemType.EPISODE, size = limit * 2)
        }
            .valueOrNull()
            ?.items
            ?.map { it.asEpisodeProgress() }
            .orEmpty()

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
    }
}

private fun PlaybackProgress.asWatchProgress() = WatchProgress(
    sourceId = sourceId.toString(),
    filmId = itemRef,
    positionMs = positionMs,
    durationMs = durationMs,
)

private fun PlaybackProgress.asEpisodeProgress() = EpisodeProgress(
    sourceId = sourceId.toString(),
    // The same opaque field, read as what an `EPISODE` row puts in it. The
    // contract settles that this is an `Episode.id`; nothing here re-decides it.
    episodeId = itemRef,
    positionMs = positionMs,
    durationMs = durationMs,
)
