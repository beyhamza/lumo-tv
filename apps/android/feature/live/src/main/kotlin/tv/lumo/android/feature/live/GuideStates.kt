package tv.lumo.android.feature.live

import java.time.Instant
import tv.lumo.android.core.data.EpgFreshness
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.EpgProgramme

/**
 * What the day's EPG read last did (S9-06-03, GD-10).
 *
 * <h2>Why a cache answer is not an answer</h2>
 *
 * The repository emits the **cache first**, always, and only then the server
 * (S7-02: "ce qui est en cache gagne"). On a cold start the cache emission
 * carries one empty programme list per requested channel, so an "answered" set
 * filled by it says "a read came back", not "the guide has nothing" — the two
 * are exactly the confusion GD-10 names. This type keeps the two apart: a
 * [FromCache] read may draw what it holds and must not conclude anything, while
 * only [Complete] (the server spoke) or [Failed] (it did not, and here is why)
 * are terminal.
 *
 * The last emission wins: a page read after a complete one goes back to
 * [FromCache] while its server slice is in flight, which is a load in progress
 * over data already on screen, never a reset.
 */
sealed interface GuideRead {

    /** Nothing has been read for this day yet. */
    data object Idle : GuideRead

    /**
     * The cache answered and the server has not: draw what there is, announce
     * nothing. Its programmes are real, its emptiness is not an answer.
     */
    data object FromCache : GuideRead

    /** The server answered: what the day holds is what it is. */
    data object Complete : GuideRead

    /**
     * The last read failed. [error] is why; the cache is what is on screen, and
     * whether that is data or nothing is [guideStateOf]'s to say.
     */
    data class Failed(val error: LumoError) : GuideRead
}

/**
 * The Guide's own status for the day on screen: the read, and its age.
 *
 * The age comes from [EpgFreshness] — the S9-03 rule, reused and never
 * re-derived here — and [lastImportAt] is the absolute date the design calls
 * "last guide import" (guide-interactions.md, "Données absentes"). `Fresh` and
 * `Stale` carry an age but not the date it was measured from, so the date is
 * kept beside the freshness rather than recomputed from it.
 */
data class GuideStatus(
    val read: GuideRead = GuideRead.Idle,
    val freshness: EpgFreshness? = null,
    val lastImportAt: Instant? = null,
)

/**
 * The status after **one emission** of the day's read (S9-06-03).
 *
 * The repository's two emissions are read for what they are: a cache answer
 * with no reason is [GuideRead.FromCache] and concludes nothing, a network
 * answer is [GuideRead.Complete], and a cache answer carrying a
 * [LumoError] is the [GuideRead.Failed] that GD-10 turns into a screen. Pure,
 * so the mapping is a unit test rather than a line written twice on two
 * surfaces.
 *
 * @param freshness the window's own `EpgWindow.freshness`, already evaluated
 *   against the view model's clock — [GuideStatus] itself never reads a clock.
 */
internal fun GuideStatus.afterRead(
    origin: DataOrigin,
    staleReason: LumoError?,
    freshness: EpgFreshness?,
    lastImportAt: Instant?,
): GuideStatus {
    val read = when {
        staleReason != null -> GuideRead.Failed(staleReason)
        origin == DataOrigin.Network -> GuideRead.Complete
        else -> GuideRead.FromCache
    }
    return GuideStatus(read = read, freshness = freshness, lastImportAt = lastImportAt)
}

/**
 * What the Guide has to say about the day it is showing (S9-06-03, GD-10/11).
 *
 * The states are the design's, and they are mutually exclusive so that a screen
 * cannot draw two sentences at once:
 *
 * - [NotConfigured] — the source has no guide. Nothing is drawn, and the
 *   channel list stays the way to watch (S7-03): no "empty guide" is invented
 *   for a guide that does not exist.
 * - [Loading] — a load in progress or a cache answer with nothing yet. **Never**
 *   the empty sentence: an initial load must not announce an empty guide.
 * - [InitialError] — the read failed and there is no data to show. A screen of
 *   its own, with ways out.
 * - [DataError] — the read failed but the grid has data. The grid and its focus
 *   survive; only a distinct message says the update failed.
 * - [Empty] — the server answered and there is nothing over the day. The neutral
 *   sentence, no cause invented.
 * - [Content] — there is something to draw, whether from the cache or the server.
 */
sealed interface GuideState {

    data object NotConfigured : GuideState

    data object Loading : GuideState

    data object InitialError : GuideState

    data object DataError : GuideState

    data object Empty : GuideState

    data object Content : GuideState
}

/**
 * The one place the "no data / data / empty" rule lives (S9-06-03, GD-10).
 *
 * Pure, over the signals the screen already holds — the read outcome, the
 * answered set, the programmes and the source's configured flag — so the
 * television grid, the phone's day view and a test all read the same sentence
 * from the same function instead of each re-writing it in line (the D1 lesson of
 * S9-06-01). No Compose, no clock: [status] carries the freshness already
 * computed by the view model's clock.
 */
internal fun guideStateOf(
    configured: Boolean?,
    status: GuideStatus,
    answered: Set<String>,
    programmes: Map<String, List<EpgProgramme>>,
): GuideState {
    // A source with no guide draws nothing at all, whatever a read did.
    if (configured == false) return GuideState.NotConfigured

    val hasData = programmes.values.any { it.isNotEmpty() }

    // A failure is told apart by what it leaves on screen, not by when it
    // happened: with programmes the grid stays, without them it is a screen.
    if (status.read is GuideRead.Failed) {
        return if (hasData) GuideState.DataError else GuideState.InitialError
    }

    if (hasData) return GuideState.Content

    // Nothing to draw. Only a completed read that actually answered for some
    // channel may call that "empty"; a cache emission or an unasked day is a
    // load, and saying "aucun programme" there is the GD-10 defect.
    return if (status.read is GuideRead.Complete && answered.isNotEmpty()) {
        GuideState.Empty
    } else {
        GuideState.Loading
    }
}

/**
 * Whether one channel row is an **answer** the grid may draw, empty included.
 *
 * A row with programmes is drawable as soon as the cache hands them over; a row
 * with none is drawable only once the read is terminal, so its empty slot can
 * carry the neutral sentence. Before that it is a skeleton, keeps its height and
 * is not a focus target — an initial load announces nothing.
 */
internal fun guideRowAnswered(
    channelId: String,
    status: GuideStatus,
    answered: Set<String>,
    programmes: Map<String, List<EpgProgramme>>,
): Boolean {
    if (channelId !in answered) return false
    if (programmes[channelId].orEmpty().isNotEmpty()) return true
    return status.read is GuideRead.Complete || status.read is GuideRead.Failed
}

/**
 * The age line the Guide says, or null when it has nothing worth saying
 * (S9-06-03, GD-11).
 *
 * The decision is [EpgFreshness]'s — this only picks the cases that deserve a
 * sentence, and never recomputes the 24-hour threshold. A fresh or undated guide
 * is the ordinary case and stays silent; a stale one dates itself, and an import
 * that failed or was interrupted says so without claiming a date it does not
 * have.
 */
sealed interface GuideAge {

    /** Older than the threshold: the date of the last successful import. */
    data class LastImport(val at: Instant) : GuideAge

    /** The last attempt failed or was interrupted: the guide may be incomplete. */
    data object Incomplete : GuideAge
}

internal fun guideAgeOf(status: GuideStatus): GuideAge? = when (status.freshness) {
    // Stale can only be built from a known import date, but a missing one is
    // "nothing to say" rather than a date invented at the screen.
    is EpgFreshness.Stale -> status.lastImportAt?.let { GuideAge.LastImport(it) }
    is EpgFreshness.AttemptFailed,
    is EpgFreshness.AttemptInterrupted,
    -> GuideAge.Incomplete

    // Running is the in-progress case the source notice already words; fresh and
    // unknown are the ordinary silence.
    else -> null
}
