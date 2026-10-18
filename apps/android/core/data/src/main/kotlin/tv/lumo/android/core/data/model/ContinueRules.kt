package tv.lumo.android.core.data.model

/**
 * What a player knows about one save besides the position (S12-02, `adr/0012`).
 *
 * @property observedAtMs when the position was read, on this device's clock. The
 *   latest observation wins the position, not the latest request (CW-30, CW-32).
 * @property sessionStartedAtMs when this playback first actually played, or null
 *   while it never has. Brings a hidden card back only if after the hide (CW-18, CW-31).
 * @property segments wall-clock intervals of actual playback since the previous
 *   save, as `start to end` epoch milliseconds, at most twenty.
 */
data class WatchReport(
    val observedAtMs: Long? = null,
    val sessionStartedAtMs: Long? = null,
    val segments: List<Pair<Long, Long>> = emptyList(),
)

/**
 * The main action a film or an episode sheet offers (S12-03).
 *
 * - [Watch]: nothing saved, or saved at zero.
 * - [Resume]: started and not finished — "Resume at 20:14".
 * - [WatchAgain]: finished, strictly past 95 % of a known duration (CW-06/07).
 */
sealed interface PrimaryPlay {
    data object Watch : PrimaryPlay
    data class Resume(val positionMs: Long) : PrimaryPlay
    data object WatchAgain : PrimaryPlay

    /** Where pressing it starts. */
    val startMs: Long
        get() = (this as? Resume)?.positionMs ?: 0L
}

/**
 * The buttons of a sheet: the main one, and "Start over" beside it when it resumes.
 *
 * **Start over only with [PrimaryPlay.Resume].** Under [PrimaryPlay.Watch] there is
 * nothing to start over from, and under [PrimaryPlay.WatchAgain] the main button
 * already starts from the beginning: two buttons doing one thing.
 */
data class SheetPlay(val primary: PrimaryPlay, val startOver: Boolean)

/**
 * The sheet's actions for a saved position, or for none (S12-03).
 *
 * **Whether the card was hidden plays no part** (CW-10): hiding takes a film out
 * of the rail, never its position, and the sheet is where it is found again.
 */
fun sheetPlayOf(positionMs: Long?, durationMs: Long?): SheetPlay = when {
    positionMs == null || positionMs <= 0L -> SheetPlay(PrimaryPlay.Watch, startOver = false)
    watched(positionMs, durationMs) -> SheetPlay(PrimaryPlay.WatchAgain, startOver = false)
    else -> SheetPlay(PrimaryPlay.Resume(positionMs), startOver = true)
}

/**
 * Which episode a series card or sheet plays, and from where.
 *
 * @property row the saved row this was decided from, or null when nothing of the
 *   series was ever saved. What a "Remove from Continue" names: the server hides
 *   the whole series from any of its episodes, and the next episode may have no
 *   row of its own to name.
 */
data class SeriesPick(val row: EpisodeProgress?, val episodeId: String, val positionMs: Long)

/**
 * The episode a series' card opens, or null for no card (S12-04).
 *
 * Driven by the server's flags, no client threshold:
 *
 * - [rows] are the series' saved rows, **most recent first** — the server's order.
 * - **Hidden** (the latest row is): no card. The server hides every row of the
 *   series, and clears the flag only on a row restarted after the hide (CW-31).
 * - **Not yet earned**: no row is `in_continue` and none is finished — nothing has
 *   reached 30 s of actual playback (CW-01/02). A finished episode earns the
 *   series its card even when short (CW-20 does not cancel CW-19).
 * - **The latest row not finished**: that episode, at its position.
 * - **The latest finished**: the next one in [order] (CW-08/19) — at its own
 *   position when started and not finished (CW-12), from the beginning when never
 *   started or already finished (CW-21). No next one: the series is done (CW-15).
 *
 * @param order every episode id of the series, in viewing order.
 */
fun seriesPickOf(rows: List<EpisodeProgress>, order: List<String>): SeriesPick? {
    val latest = rows.firstOrNull() ?: return null
    if (latest.hidden) return null
    val earned = rows.any { it.inContinue || (it.finished && !it.hidden) }
    if (!earned) return null

    if (!latest.finished) return SeriesPick(latest, latest.episodeId, latest.positionMs)

    val index = order.indexOf(latest.episodeId)
    if (index < 0) return null
    val nextId = order.getOrNull(index + 1) ?: return null
    val nextRow = rows.firstOrNull { it.episodeId == nextId }
    return SeriesPick(latest, nextId, nextRow?.takeUnless { it.finished }?.positionMs ?: 0L)
}

/**
 * The row a series' card names when it is **unavailable**, or null (S12-05, CW-28).
 *
 * The card follows the most recent row, as [seriesPickOf] does: when that episode
 * is gone — a successful lookup did not answer it — the card says so rather than
 * falling back on an older episode, which would be resuming something nobody chose
 * (CW-27's "never silently another episode"). The same gates as the live card:
 * hidden, or never earned, means no card at all.
 *
 * @param rows the series' saved rows, most recent first.
 * @param gone episode ids a successful lookup left out.
 */
fun seriesGoneRow(rows: List<EpisodeProgress>, gone: Set<String>): EpisodeProgress? {
    val latest = rows.firstOrNull() ?: return null
    if (latest.hidden || latest.episodeId !in gone) return null
    val earned = rows.any { it.inContinue || (it.finished && !it.hidden) }
    return latest.takeIf { earned }
}

/**
 * The main action of a series sheet: which episode, and which button (S12-03).
 *
 * The rail's rule ([seriesPickOf]) without its gate — a sheet always offers
 * something, hidden or not (CW-10):
 *
 * - nothing saved: **Watch** the first episode;
 * - the most recent one not finished: **Resume** it, with Start over;
 * - finished, with a next one: that one, resumed if started (CW-12), watched from
 *   the beginning otherwise (CW-19), **Watch again** if already finished (CW-21);
 * - finished and last: **Watch again** from the first episode (CW-15).
 *
 * @param progress saved rows by episode id, possibly of other series too.
 * @return null when the series lists no episode.
 */
fun seriesSheetPlayOf(
    seasons: List<Season>,
    progress: Map<String, EpisodeProgress>,
): Pair<Episode, SheetPlay>? {
    val episodes = seasons.flatMap { it.episodes }
    val first = episodes.firstOrNull() ?: return null
    val byId = episodes.associateBy { it.id }

    val latest = episodes.mapNotNull { progress[it.id] }.maxByOrNull { it.updatedAtMillis }
        ?: return first to sheetPlayOf(null, null)

    fun playOf(episode: Episode): SheetPlay {
        val row = progress[episode.id]
        return sheetPlayOf(row?.positionMs, row?.durationMs ?: episode.durationMs)
    }

    if (!latest.finished) return byId.getValue(latest.episodeId).let { it to playOf(it) }

    val next = seasons.episodeAfter(latest.episodeId)
        ?: return first to SheetPlay(PrimaryPlay.WatchAgain, startOver = false)
    return next to playOf(next)
}

private val Episode.durationMs: Long?
    get() = durationSeconds?.let { it * MILLIS_PER_SECOND }

private const val MILLIS_PER_SECOND = 1_000L
