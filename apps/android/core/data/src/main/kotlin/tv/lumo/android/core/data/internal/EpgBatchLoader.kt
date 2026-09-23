package tv.lumo.android.core.data.internal

import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.network.generated.model.EpgChannelProgrammes
import tv.lumo.android.network.generated.model.EpgGrid
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * One grouped guide read, split as the server demands and merged back into the
 * answer that was asked for (C1 D2).
 *
 * <h2>What a caller sees</h2>
 *
 * One request in, one [EpgGrid] out, shaped exactly as if the server had
 * answered the original request: one entry per channel id in the order asked,
 * programmes sorted by start then id, and a programme that straddled a window
 * cut present **once** in its channel's list. Or one failure — the last `422`
 * when the plan is exhausted, so that a screen branches on the contract's own
 * code and offers a reduction; any other failure as it came.
 *
 * <h2>At most two requests in flight</h2>
 *
 * A [Semaphore] of two permits around the fetch, held for the duration of the
 * request only. The fan-out is recursive — a half that is still too large is
 * halved again — and the permit is released before the children are awaited, so
 * the tree can be eight requests wide at the bottom and the server still sees
 * two at a time (C1 D2: "au plus deux requêtes simultanées").
 *
 * <h2>Deduplication is per channel, never across channels</h2>
 *
 * Two channels of one source that share a `tvg_id` receive the same programme,
 * same UUID, under each of them, and both rows of a grid have to show it. The
 * merge therefore keys on `(channel, programme id)`: a programme is dropped only
 * when the *same channel* already has it from the other half of a window cut.
 */
internal class EpgBatchLoader(
    private val fetch: suspend (sourceId: String, channelIds: List<String>, from: Instant, to: Instant) -> LumoResult<EpgGrid>,
) {

    private val inFlight = Semaphore(MAX_CONCURRENT)

    suspend fun load(sourceId: String, channelIds: List<String>, from: Instant, to: Instant): LumoResult<EpgGrid> =
        load(sourceId, EpgSplitPlanner.Request(channelIds, from, to))

    private suspend fun load(sourceId: String, request: EpgSplitPlanner.Request): LumoResult<EpgGrid> {
        val result = inFlight.withPermit { fetch(sourceId, request.channelIds, request.from, request.to) }

        if (result !is LumoResult.Failure || !result.error.isWindowTooLarge()) return result

        // The plan is exhausted: the 422 is the answer, with its own code, so
        // that the screen offers what the server asked for — less at a time.
        val parts = EpgSplitPlanner.split(request) ?: return result

        return coroutineScope {
            val answers = parts.map { part -> async { load(sourceId, part) } }.awaitAll()
            answers.firstOrNull { it is LumoResult.Failure }
                ?: LumoResult.Success(merge(request, answers.map { (it as LumoResult.Success).value }))
        }
    }

    /**
     * The halves, back in the shape of the whole.
     *
     * Channel halves contribute disjoint entries; window halves contribute the
     * same channels twice, with the programmes on the cut in both. The two
     * cases are one merge: entries are gathered per channel id, and each
     * channel's programmes are deduplicated by id and re-sorted.
     */
    private fun merge(request: EpgSplitPlanner.Request, grids: List<EpgGrid>): EpgGrid {
        val byChannel = grids
            .flatMap { it.channels }
            .groupBy { it.channelId.toString() }

        val channels = request.channelIds.mapNotNull { id ->
            val entries = byChannel[id] ?: return@mapNotNull null
            EpgChannelProgrammes(
                channelId = entries.first().channelId,
                mappingStatus = entries.first().mappingStatus,
                programmes = entries
                    .flatMap { it.programmes }
                    .distinctBy { it.id }
                    .sortedWith(compareBy({ it.startsAt.toInstant() }, { it.id })),
            )
        }

        // The status and the server clock of the first half stand for the
        // whole: they are source level and were read moments apart.
        val first = grids.first()
        return EpgGrid(
            sourceId = first.sourceId,
            from = request.from.atOffset(first.from.offset),
            to = request.to.atOffset(first.to.offset),
            generatedAt = first.generatedAt,
            epg = first.epg,
            channels = channels,
        )
    }

    private fun LumoError.isWindowTooLarge(): Boolean =
        this is LumoError.Api && code == ErrorCode.EPG_WINDOW_TOO_LARGE

    private companion object {
        /** C1 D2. The product's number, not a tuning constant. */
        const val MAX_CONCURRENT = 2
    }
}
