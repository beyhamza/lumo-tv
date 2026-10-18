package tv.lumo.android.core.data.model

/**
 * A film in a "continue watching" rail: what to draw, and where to start it.
 *
 * Moved down from `feature:vod` with the home screen (US-017). The film grid and
 * the home both draw this card, a feature may not import another, and two copies
 * of "a film plus its saved position" would be two places to change the day a
 * card carries one thing more.
 */
data class ResumableFilm(val film: VodItem, val progress: WatchProgress)

/**
 * One card of the home screen's "Continue" rail (US-017, US-019).
 *
 * <h2>Two shapes, because pressing one does two different things</h2>
 *
 * A film resumes a film; a series resumes **an episode**, chosen by
 * `SeriesRepository.resumable` — the one being watched, or the next one when that
 * one is over. A single flattened type would need a nullable episode and a
 * nullable film, and a screen that had to work out which of the two it was holding
 * is exactly how an episode gets opened in the film player.
 *
 * There is no channel here and there cannot be: the contract has no progress for a
 * live channel (`ProgressItemType` has no `LIVE`), and what a channel gets instead
 * is the "Live" rail of recently watched ones.
 */
sealed interface ContinueItem {

    /** Stable across reloads, and unique across the two kinds. A `LazyRow` key. */
    val key: String

    val sourceId: String

    /** When this was last watched, on the server's clock. What the rail sorts on. */
    val updatedAtMillis: Long

    data class Film(val resume: ResumableFilm) : ContinueItem {
        override val key: String get() = "film:${resume.film.id}"
        override val sourceId: String get() = resume.film.sourceId
        override val updatedAtMillis: Long get() = resume.progress.updatedAtMillis
    }

    data class Show(val resume: ResumableSeries) : ContinueItem {
        // The series and not the episode: the card is the series', and it must
        // keep its identity — its place, its focus — when the episode behind it
        // moves on to the next one.
        override val key: String get() = "series:${resume.series.id}"
        override val sourceId: String get() = resume.series.sourceId
        override val updatedAtMillis: Long get() = resume.updatedAtMillis
    }

    /**
     * A card whose film or episode the server no longer has (S12-05, CW-28).
     *
     * Only ever built from a **successful** lookup that left the item out; a lookup
     * that failed proves nothing and keeps the cards as they were (S11-05). Its one
     * action is "Remove from Continue watching"; selecting it says the content is no
     * longer in the catalogue. The progress row is never deleted from here.
     *
     * @property title what this device last called it, or null for a generic label.
     * @property ref the film or episode id of the progress row: what a removal names.
     */
    data class Unavailable(
        override val key: String,
        override val sourceId: String,
        override val updatedAtMillis: Long,
        val title: String?,
        val kind: UnavailableKind,
        val ref: String,
    ) : ContinueItem
}

/** Which catalogue an [ContinueItem.Unavailable] card came from, and leads back to. */
enum class UnavailableKind { FILM, EPISODE }

/**
 * The film rows whose film is gone, as cards (CW-28).
 *
 * The rail's own filter applies — in the rail, not hidden, not finished — so a gone
 * film nobody would have seen a card for does not get one now.
 *
 * @param gone ids a successful lookup did not answer, with their last known title.
 */
fun unavailableFilms(rows: List<WatchProgress>, gone: Map<String, String?>): List<ContinueItem.Unavailable> =
    rows.filter { it.inContinue && it.filmId in gone }
        .distinctBy { it.filmId }
        .map { row ->
            ContinueItem.Unavailable(
                // The film's own key: the card keeps its place and its focus.
                key = "film:${row.filmId}",
                sourceId = row.sourceId,
                updatedAtMillis = row.updatedAtMillis,
                title = gone[row.filmId],
                kind = UnavailableKind.FILM,
                ref = row.filmId,
            )
        }

/**
 * How many cards a home rail holds.
 *
 * Twelve is what the film rail already asked the server for (`S5-11`), and the
 * home keeps the number rather than inventing a second one: past a dozen a rail
 * has stopped being a shortcut and become a catalogue scrolled sideways, which is
 * what "See all" and the catalogue screens are for.
 */
const val HOME_RAIL_SIZE: Int = 12

/**
 * Resolves saved film positions into cards, **in the order of the rows**.
 *
 * The rows arrive most recently updated first, which is the one thing this list
 * knows and the cache does not — so the resolution is driven by the rows and never
 * by whatever order SQLite answered in.
 *
 * A film the cache no longer holds drops out rather than rendering as a gap: it was
 * removed by a re-synchronisation, and a card with no title is worse than one card
 * fewer. A finished film drops out as well; `ProgressRepository.continueWatching`
 * already filters them, and saying it again here is what lets this function be
 * handed any list of rows.
 */
fun resumableFilms(rows: List<WatchProgress>, films: List<VodItem>): List<ResumableFilm> {
    val byId = films.associateBy { it.id }

    return rows
        .filter { it.inContinue }
        .mapNotNull { row -> byId[row.filmId]?.let { film -> ResumableFilm(film, row) } }
}

/**
 * The "Continue" rail of the home screen, as a pure function (US-017).
 *
 * <h2>The rules, each one an acceptance criterion</h2>
 *
 * - **The active source only** (US-018). A null source shows nothing rather than
 *   everything — the rule `ofSource` already applies to favourites.
 * - **Films and episodes merged, most recently watched first.** Each list arrives
 *   sorted; merging them needs the value they were sorted by, which is why both
 *   cards carry the server's `updated_at`. The sort is stable, so two rows written
 *   in the same millisecond keep the order they were given in.
 * - **One card per series.** `SeriesRepository.resumable` already guarantees it;
 *   it is enforced again here because this function is what the screen trusts, and
 *   a rail showing one series twice is the defect `ResumableSeries` exists to
 *   prevent.
 * - **Nothing finished.** A finished film is dropped. A finished *series* never
 *   reaches this far — it has no card — while a finished *episode* with a
 *   successor arrives as a card for that successor at position zero, which is not
 *   finished and stays.
 * - **Gone items stay, as such** (CW-28): [unavailable] cards take their place in
 *   the order like any other.
 * - **Capped at [limit]**, after the merge and not before: cutting each list first
 *   would let twelve old films push out the episode watched an hour ago.
 */
fun continueWatchingOf(
    films: List<ResumableFilm>,
    series: List<ResumableSeries>,
    sourceId: String?,
    limit: Int = HOME_RAIL_SIZE,
    unavailable: List<ContinueItem.Unavailable> = emptyList(),
): List<ContinueItem> {
    if (sourceId == null) return emptyList()

    val filmCards = films
        .filter { it.progress.inContinue }
        .distinctBy { it.film.id }
        .map(ContinueItem::Film)

    val seriesCards = series
        .distinctBy { it.series.id }
        .map(ContinueItem::Show)

    // A gone item never shares a key with a live card: the lookups exclude it.
    val taken = (filmCards + seriesCards).mapTo(HashSet()) { it.key }
    val goneCards = unavailable.distinctBy { it.key }.filterNot { it.key in taken }

    return (filmCards + seriesCards + goneCards)
        .filter { it.sourceId == sourceId }
        .sortedByDescending { it.updatedAtMillis }
        .take(limit)
}
