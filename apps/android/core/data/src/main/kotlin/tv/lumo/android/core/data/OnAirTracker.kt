package tv.lumo.android.core.data

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.model.EpgProgramme
import tv.lumo.android.core.data.repository.EpgRepository

/**
 * What is on a set of channels right now, asked for **one page at a time** and
 * never one card at a time (US-16, S9-03: "une requête groupée par écran").
 *
 * <h2>The trap is the number of requests</h2>
 *
 * A paginated grid that asked for the guide card by card would make one request
 * per visible card and more at every scroll (S7-04). So a screen hands this the
 * channels of the page on display, and this asks the server once for the ones
 * it does not hold yet — a page that scrolls back into view costs nothing, a
 * page of eight cards costs one request, and the whole grid's three-hour guide
 * is one request per page of it.
 *
 * <h2>"Now" is computed, not fetched</h2>
 *
 * The window held is `[now, now + 3 h)` at the first request. What is on air
 * is [currentAndNext] over it, recomputed on [tick] — when a page comes into
 * view, when the bar opens, when the home screen is shown again — and never on a
 * timer. A window whose end is near is dropped and asked again on the next
 * [show], so a television left on the grid all evening does not run out of
 * guide.
 *
 * <h2>One source at a time</h2>
 *
 * A change of source drops everything held and cancels what is in flight: an
 * answer for the old source arriving after the switch would be drawn under the
 * new source's cards, which is the GD-03 failure (guide-interactions.md).
 *
 * In `core:data` rather than in a feature because the channel grid, the player
 * and the home screen all need it, and a feature never depends on another
 * (apps/android/AGENTS.md §2). Shared by the three screens of S9-03; the Guide
 * view of S9-04 will read the repository on its own terms.
 *
 * @param scope the screen's. Loads live and die with it.
 */
class OnAirTracker(
    private val epg: EpgRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val horizon: Duration = DEFAULT_HORIZON,
) {

    private val _programmes = MutableStateFlow<Map<String, List<EpgProgramme>>>(emptyMap())

    /** The window held per channel id, for a screen that wants "next" as well. */
    val programmes: StateFlow<Map<String, List<EpgProgramme>>> = _programmes.asStateFlow()

    private val _onAir = MutableStateFlow<Map<String, EpgProgramme>>(emptyMap())

    /**
     * The programme on air per channel id, as of the last [tick]. Absent for a
     * channel with nothing on — and a screen draws nothing for it (S7-03).
     */
    val onAir: StateFlow<Map<String, EpgProgramme>> = _onAir.asStateFlow()

    private var sourceId: String? = null
    private var from: Instant? = null
    private var to: Instant? = null

    /** Channel ids asked for, answered or not. Guarded by being touched on [scope] only. */
    private val asked = mutableSetOf<String>()
    private val loads = mutableListOf<Job>()

    /**
     * The channels on display. One request for those not held yet, none when
     * they all are.
     */
    fun show(sourceId: String, channelIds: List<String>) {
        val now = clock.instant()
        if (sourceId != this.sourceId || windowNearlyOver(now)) reset(sourceId, now)

        val missing = channelIds.distinct().filterNot { it in asked }
        if (missing.isEmpty()) {
            tick()
            return
        }
        asked += missing

        val from = checkNotNull(from)
        val to = checkNotNull(to)
        loads += scope.launch {
            epg.window(sourceId, missing, from, to).collect { cached ->
                _programmes.value = _programmes.value + cached.value.channels
                    .associate { it.channelId to it.programmes }
                tick()
            }
        }
    }

    /** Recomputes what is on air against the clock, over what is held. */
    fun tick() {
        val now = clock.instant()
        _onAir.value = _programmes.value.mapNotNull { (channelId, programmes) ->
            currentAndNext(programmes, now).current?.let { channelId to it }
        }.toMap()
    }

    /** Forgets everything held and stops what is in flight. */
    fun clear() {
        loads.forEach(Job::cancel)
        loads.clear()
        asked.clear()
        sourceId = null
        from = null
        to = null
        _programmes.value = emptyMap()
        _onAir.value = emptyMap()
    }

    private fun reset(sourceId: String, now: Instant) {
        clear()
        this.sourceId = sourceId
        from = now
        to = now.plus(horizon)
    }

    private fun windowNearlyOver(now: Instant): Boolean {
        val end = to ?: return true
        return !now.plus(REFRESH_MARGIN).isBefore(end)
    }

    companion object {
        /** S7-03's window: enough for several presses on OK, and for a page of a grid. */
        val DEFAULT_HORIZON: Duration = Duration.ofHours(3)

        /** How close to the window's end a new [show] starts a new window. */
        val REFRESH_MARGIN: Duration = Duration.ofMinutes(15)
    }
}
