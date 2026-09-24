package tv.lumo.android.core.data

import java.time.Instant
import tv.lumo.android.core.data.model.EpgProgramme

/**
 * What is on a channel at one instant, and what follows it.
 *
 * Both nullable, independently: a gap in the guide has a next programme and no
 * current one; the last programme of a window has a current one and no next.
 */
data class NowAndNext(
    val current: EpgProgramme?,
    val next: EpgProgramme?,
)

/**
 * "En ce moment" and "Ensuite", from a list of programmes and a clock — nothing
 * else (US-16, S9-03: "se calcule depuis les horaires et l'horloge locale, sans
 * requête à la seconde").
 *
 * <h2>A pure function, on purpose</h2>
 *
 * A screen holds a window of three hours and asks this at the moments that
 * matter — when the bar opens, when a page comes into view — instead of asking
 * the server, or ticking every second. The player's info bar goes away after five
 * seconds; redrawing it per second would be redrawing a thing nobody is looking
 * at (S7-03).
 *
 * <h2>The rule</h2>
 *
 * A programme's interval includes its start and excludes its end
 * (guide-interactions.md): at 21:00:00 exactly, the 21:00 programme is on and the
 * 20:00–21:00 one is over. [NowAndNext.current] is the first programme, in start
 * order, that contains [now]; [NowAndNext.next] is the first that starts strictly
 * after [now] — which, across a gap, is the next thing on and not the end of the
 * gap.
 *
 * The list is expected sorted by start, as both the server and the cache
 * deliver it; it is not re-sorted here, so a caller cannot make this quietly
 * expensive on a grid.
 */
fun currentAndNext(programmes: List<EpgProgramme>, now: Instant): NowAndNext = NowAndNext(
    current = programmes.firstOrNull { !it.startsAt.isAfter(now) && it.endsAt.isAfter(now) },
    next = programmes.firstOrNull { it.startsAt.isAfter(now) },
)
