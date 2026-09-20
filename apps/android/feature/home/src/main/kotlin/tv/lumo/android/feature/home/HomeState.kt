package tv.lumo.android.feature.home

import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.SourceNotice
import tv.lumo.android.core.data.aggregatedFavorites
import tv.lumo.android.core.data.channelsOfSource
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.ContinueItem
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.data.model.HOME_RAIL_SIZE
import tv.lumo.android.core.data.notice

/**
 * Which of its faces the home screen is showing (US-017, design S8-E01 and S8-E02).
 *
 * <h2>[Unavailable] is kept apart from [NoSource], unlike on the catalogue grids</h2>
 *
 * `CatalogueSource` folds the two together and says why: a grid has nothing to
 * draw either way. The home screen is where somebody *lands*, and the two send
 * them to opposite places — one to a form, the other to "try again". Telling an
 * account that has three sources to add one, because the network was down at
 * launch, is the mistake `ActiveSourceState.Unavailable` exists to prevent.
 */
sealed interface HomeStep {

    /** The session or the list of sources is still being read. */
    data object Loading : HomeStep

    /** The account has no source. The way forward is adding one. */
    data object NoSource : HomeStep

    /** Several sources and none chosen on this device. The shell is asking (US-018). */
    data object NeedsChoice : HomeStep

    /** Nothing could be fetched and this device holds no choice. Worth retrying. */
    data object Unavailable : HomeStep

    /** A source is being browsed: rails, or the invitation to explore. */
    data object Browsing : HomeStep
}

/**
 * What the home screen makes of [ActiveSourceState], reduced to what it reads.
 *
 * Narrow on purpose, for the reason `CatalogueSource` gives: `Selected` carries a
 * whole `Source` and the whole list, and those change for reasons this screen does
 * not care about — a sync date, a counter, a sibling renamed. Reducing to a step, an
 * identifier and a notice is what lets `distinctUntilChanged` keep three rails from
 * reloading because another source finished importing.
 */
data class HomeSource(
    val step: HomeStep,
    val sourceId: String? = null,
    /**
     * What is said about the source above the rails, or nothing.
     *
     * `SourceNotice` lives in `core:data` since the catalogue grids say the same
     * thing about the same source (US-024, lot C4) — it used to be a type of this
     * module. A notice and not a step: a source that is refreshing, or whose last
     * attempt failed, keeps its rails.
     */
    val notice: SourceNotice? = null,
)

/**
 * <h2>A choice without a list is browsable, and says nothing</h2>
 *
 * `Selected` with a null `Source` is a device that knows which source it browses
 * and could not reach the server. Favourites and recent channels are in Room, so
 * there are rails to draw; there is no status to report, so there is no notice. An
 * unreachable server proves nothing about the source (US-018).
 */
fun ActiveSourceState.asHomeSource(): HomeSource = when (this) {
    ActiveSourceState.Loading -> HomeSource(HomeStep.Loading)
    ActiveSourceState.None -> HomeSource(HomeStep.NoSource)
    ActiveSourceState.Unavailable -> HomeSource(HomeStep.Unavailable)
    is ActiveSourceState.NeedsChoice -> HomeSource(HomeStep.NeedsChoice)
    is ActiveSourceState.Selected -> HomeSource(
        step = HomeStep.Browsing,
        sourceId = sourceId,
        notice = source.notice(),
    )
}

/**
 * One rail of the home screen.
 *
 * A sealed type rather than three nullable lists, so that "which sections, in
 * which order" is a value a test can read — and so that a screen cannot render a
 * title over an empty row: a section with nothing in it is not in the list at all.
 */
sealed interface HomeSection {

    /** Films and episodes in progress, one card per series. Press to resume. */
    data class Continue(val items: List<ContinueItem>) : HomeSection

    /**
     * The favourites of the active source, each channel once.
     *
     * Always comes with a "See all" action: the rail is capped, and the library is
     * where the groups and their organisation live (US-020).
     */
    data class Favorites(val channels: List<FavoriteChannel>) : HomeSection

    /** Recently watched channels. Comes with "All channels", the way into the catalogue. */
    data class Live(val channels: List<Channel>) : HomeSection
}

/**
 * Everything the two home screens draw (US-017).
 *
 * The account's lists are kept whole — [groups], [favorites], [recent] — and
 * everything drawn reads them through the active source. That is what makes a
 * change of source instant here, the argument `FavoritesState` already makes.
 */
data class HomeState(
    val step: HomeStep = HomeStep.Loading,
    val sourceId: String? = null,
    val notice: SourceNotice? = null,
    /** Already scoped to [sourceId], merged, ordered and capped by `core:data`. */
    val continueWatching: List<ContinueItem> = emptyList(),
    val groups: List<FavoriteGroup> = emptyList(),
    val favorites: List<FavoriteChannel> = emptyList(),
    val recent: List<Channel> = emptyList(),
    /**
     * Whether each of the three reads has answered at least once for [sourceId].
     *
     * Three flags and not one, because they settle at different speeds: the two
     * Room reads answer within a frame, the progress list is a request. What they
     * guard is [blank] — "nothing to show" may only be said once all three have
     * had their say, or a new account sees the invitation to explore flash before
     * its rails arrive.
     */
    val continueLoaded: Boolean = false,
    val favoritesLoaded: Boolean = false,
    val recentLoaded: Boolean = false,
) {

    /**
     * The rails, in the validated order — **Continue, Favourites, Live** (layout
     * validated on 19 September 2026) — and only those with something in them.
     *
     * No title over an empty row and no reserved space: a heading over nothing on
     * a first visit is a promise about a feature nobody has used yet.
     */
    val sections: List<HomeSection>
        get() = buildList {
            if (step != HomeStep.Browsing) return@buildList

            if (continueWatching.isNotEmpty()) add(HomeSection.Continue(continueWatching))

            val starred = aggregatedFavorites(groups, favorites, sourceId).take(HOME_RAIL_SIZE)
            if (starred.isNotEmpty()) add(HomeSection.Favorites(starred))

            val watched = recent.channelsOfSource(sourceId).take(HOME_RAIL_SIZE)
            if (watched.isNotEmpty()) add(HomeSection.Live(watched))
        }

    /** Every read has answered for the source on screen. */
    val settled: Boolean
        get() = continueLoaded && favoritesLoaded && recentLoaded

    /**
     * A source, and nothing in any of the three sections.
     *
     * What replaces the rails is an invitation to explore with the three
     * catalogues one press away — never three empty rows (US-017). False until
     * [settled], so the invitation is a conclusion and not a first frame.
     */
    val blank: Boolean
        get() = step == HomeStep.Browsing && settled && sections.isEmpty()

    /** Still waiting for the first answers, with nothing to draw in the meantime. */
    val waiting: Boolean
        get() = step == HomeStep.Browsing && !settled && sections.isEmpty()

    /**
     * This screen, pointed at [source] (US-018).
     *
     * The same source keeps its rails: a status moving from importing to ready
     * must not blank the screen while the reload it triggers is in flight. Another
     * source drops the one rail that was scoped to the old one — the progress
     * cards — and waits for the new one's; the account's lists stay, because they
     * are filtered on the way out and were never the old source's alone.
     */
    fun showing(source: HomeSource): HomeState =
        if (source.sourceId == sourceId) {
            copy(step = source.step, notice = source.notice)
        } else {
            copy(
                step = source.step,
                sourceId = source.sourceId,
                notice = source.notice,
                continueWatching = emptyList(),
                continueLoaded = false,
            )
        }
}
