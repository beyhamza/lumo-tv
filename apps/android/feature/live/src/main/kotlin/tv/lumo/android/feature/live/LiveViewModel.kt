package tv.lumo.android.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.CatalogueFace
import tv.lumo.android.core.data.CatalogueSource
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.NowAndNext
import tv.lumo.android.core.data.OnAirTracker
import tv.lumo.android.core.data.SourceNotice
import tv.lumo.android.core.data.asCatalogueSource
import tv.lumo.android.core.data.channelsOfSource
import tv.lumo.android.core.data.currentAndNext
import tv.lumo.android.core.data.face
import tv.lumo.android.core.data.model.Cached
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.EpgProgramme
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.data.notice
import tv.lumo.android.core.data.ofSource
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.CatalogueRepository
import tv.lumo.android.core.data.repository.EpgRepository
import tv.lumo.android.core.data.repository.FavoriteRepository
import tv.lumo.android.core.data.repository.RecentChannelRepository
import tv.lumo.android.core.data.repository.onFailureNaming
import tv.lumo.android.core.data.sourceId

/**
 * Browsing the channels of a source (US-08).
 *
 * <h2>Everything on this screen comes out of the cache</h2>
 *
 * Categories and channels are read from Room, always, and the network only ever
 * *fills* it. That is what makes the offline scenario ordinary rather than
 * special: the same code path renders the same list, and the only difference is
 * what [Cached.origin] says about it.
 *
 * <h2>Refreshing is not free, so it is not automatic</h2>
 *
 * A refresh walks the source's whole catalogue — fifteen thousand channels is an
 * ordinary source — and doing that every time this screen opens would be a
 * minute of somebody's data for a list that has not changed. So it happens on the
 * first open of an empty cache, and afterwards only when the user asks.
 *
 * The one exception is a source whose **first** import has not succeeded: there
 * is nothing to cache yet, and the screen says so — importing, or failed —
 * rather than showing an empty list.
 *
 * <h2>A catalogue that exists is shown, in every status (lot C4)</h2>
 *
 * Any status but `READY` used to hide the grid, although Room still held the
 * channels and, since C4, the server still lists them. A source that refreshes,
 * or whose last attempt failed, now keeps its grid and gets a [SourceNotice]
 * above it: the real step, or the reason and the warning that the list may be
 * out of date (US-024). The rule itself is `CatalogueSource.face`, in
 * `core:data`, shared with films and series.
 *
 * <h2>The source is the active one, and it can change under the screen</h2>
 *
 * Which source this browses is [ActiveSourceRepository]'s answer, not the first
 * of a list (US-018). The answer is a flow, because the switcher sits in the
 * shell and works while this screen is open: the section stays, the grid is read
 * again for the new source, and whatever filtered the old one — a category, a
 * group, the recent shelf — is dropped, since none of it means anything in a
 * catalogue it did not come from. See [LiveState.browsing].
 *
 * <h2>What is on, one request per page of the grid (US-16, S7-04 by S9-03)</h2>
 *
 * The television's grid says what is on under each card. The trap is the number
 * of requests: a paginated grid that asked card by card would make one per
 * visible card and more at every scroll. So the screen reports the **page** of
 * channels on display ([onChannelsVisible]) and [OnAirTracker] asks once for
 * the ones it does not hold — one request per page, none for a page scrolled
 * back into view. What it answers lands in [LiveState.onAir]; a card with
 * nothing there draws nothing (S7-03).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val catalogue: CatalogueRepository,
    private val activeSource: ActiveSourceRepository,
    private val favorites: FavoriteRepository,
    private val recents: RecentChannelRepository,
    epg: EpgRepository,
    clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state

    private val guide = OnAirTracker(epg, clock, viewModelScope)

    /**
     * The channels of the chosen category, paginated straight out of SQLite.
     *
     * `cachedIn` keeps the loaded windows across a rotation; without it, turning
     * the phone re-reads the first page and jumps the user back to the top.
     */
    val channels: Flow<PagingData<Channel>> = _state
        .map { ChannelQuery(it.sourceId, it.filter, it.favorites, it.recent) }
        .distinctUntilChanged()
        .flatMapLatest { query -> channelsFor(query) }
        .cachedIn(viewModelScope)

    /**
     * The grid's contents, from whichever of the two shelves is open.
     *
     * A favourite group is served as a one-page [PagingData] rather than through
     * its own list, and that is what keeps the television's grid a single code
     * path — same cards, same focus handling, same return-to-the-channel-you-were
     * -watching. The shapes underneath are genuinely different: fifteen thousand
     * channels are read out of SQLite in windows, while a group somebody curated
     * by hand is a list of tens that is already in memory.
     */
    private fun channelsFor(query: ChannelQuery): Flow<PagingData<Channel>> = when {
        query.sourceId == null -> flowOf(PagingData.empty())

        query.filter is CatalogueFilter.Group -> flowOf(
            PagingData.from(
                query.favorites
                    .filter { it.groupId == query.filter.id }
                    // The user's own order, not the provider's.
                    .sortedBy { it.position }
                    .map { it.channel },
            ),
        )

        // Already in the server's order, most recent first. Re-sorting it here on
        // anything — a name, a number — would throw away the one thing this list
        // knows and the catalogue does not.
        query.filter is CatalogueFilter.Recent -> flowOf(PagingData.from(query.recent))

        else -> catalogue.channels(
            query.sourceId,
            (query.filter as? CatalogueFilter.Category)?.id,
        )
    }

    init {
        observeActiveSource()
        observeFavorites()
        viewModelScope.launch {
            guide.onAir.collect { onAir -> _state.update { it.copy(onAir = onAir) } }
        }
        // The same grouped windows, kept whole for the Guide's "En ce moment"
        // list (S9-04-05): the tracker holds current *and* next, and this screen
        // only derived the current one before.
        viewModelScope.launch {
            guide.programmes.collect { programmes ->
                _state.update { it.copy(guideProgrammes = programmes) }
            }
        }
    }

    /**
     * The channels of the page on display, from the television's grid.
     *
     * A page, not the visible cards: the screen rounds what is visible to a
     * page of [EPG_PAGE_SIZE], so that scrolling within a page costs nothing
     * and scrolling into the next costs one request. The tracker holds what it
     * has already asked for; repeating a page is free.
     */
    fun onChannelsVisible(channelIds: List<String>) {
        val sourceId = _state.value.sourceId ?: return
        if (channelIds.isEmpty()) return
        guide.show(sourceId, channelIds)
    }

    /**
     * The channels of the page the Guide's "En ce moment" list is showing
     * (S9-04-05). One grouped request for the page's new channels, none for a
     * page already held, and never one from a card: the list reports its page,
     * and the tracker decides what is missing.
     */
    fun onGuideVisible(channelIds: List<String>) {
        val sourceId = _state.value.sourceId ?: return
        if (channelIds.isEmpty()) return
        guide.show(sourceId, channelIds)
    }

    /**
     * Follows the active source for as long as the screen lives.
     *
     * `collectLatest`, and it is what makes a switch clean: the category observer
     * started for the previous source lives inside the block and is cancelled with
     * it. A plain `collect` would leave the old source's categories streaming into
     * the new source's strip.
     */
    private fun observeActiveSource() {
        // Already resolved means resolved a while ago: the status this screen
        // depends on — importing or ready — may have moved since. One short
        // request per opening, which is what this screen cost before. Still
        // loading means the repository is asking right now, and asking twice
        // would buy nothing.
        if (activeSource.state.value !is ActiveSourceState.Loading) {
            viewModelScope.launch { activeSource.refresh() }
        }

        viewModelScope.launch {
            activeSource.state
                .map { it.asCatalogueSource() }
                .distinctUntilChanged()
                .collectLatest { source -> open(source) }
        }

        // Apart from the collector above, and that is the point: the notice
        // changes with every step of a synchronisation, and none of those steps
        // may restart the grid.
        viewModelScope.launch {
            activeSource.state
                .map { it.notice() }
                .distinctUntilChanged()
                .collect { notice -> _state.update { it.copy(notice = notice) } }
        }
    }

    private suspend fun open(source: CatalogueSource) {
        val sourceId = source.sourceId
        val cached = if (sourceId == null) 0 else catalogue.cachedChannelCount(sourceId)

        // A real source change drops the guide at once, cancelling an answer
        // that is still in flight: a programme of the old source landing under
        // the new one is the GD-03 failure (S9-04-05). A mere status change of
        // the same source keeps what the Guide holds.
        if (sourceId != _state.value.sourceId) guide.clear()

        _state.update { it.browsing(source, cachedItems = cached) }

        // No grid: nothing was ever ingested and nothing is cached. An empty
        // channel list would read as "this source has no channels", which is a
        // different and much more alarming thing than "it is still importing".
        if (sourceId == null || _state.value.step != LiveStep.Browsing) return

        coroutineScope {
            launch { observeCategories(sourceId) }

            // Only a source the server can list: one that never finished an
            // ingestion answers `409`, and asking would put a "refresh failed"
            // banner over a cache that is merely waiting.
            if (cached == 0 && source is CatalogueSource.Ready) {
                refresh()
            }
        }
    }

    /**
     * Asks for the list of sources again.
     *
     * What the screens call every few seconds **while a refresh is on display** —
     * from the composition, so that it stops with the screen: the step of a
     * synchronisation is only worth showing if it moves, and the notice has to go
     * away when the refresh ends.
     */
    fun refreshSource() {
        if (activeSource.state.value is ActiveSourceState.Loading) return
        viewModelScope.launch { activeSource.refresh() }
    }

    private suspend fun observeCategories(sourceId: String) {
        catalogue.categories(sourceId).collect { cached ->
            _state.update {
                it.copy(categories = cached.value, origin = cached.origin)
            }
        }
    }

    /** The user asking for the catalogue to be pulled again. */
    fun refresh() {
        val sourceId = _state.value.sourceId ?: return
        if (_state.value.refreshing) return

        _state.update { it.copy(refreshing = true) }

        viewModelScope.launch {
            val result = catalogue.refresh(sourceId)

            // `SOURCE_NOT_FOUND` is the server saying this source was deleted —
            // from the website, from another device. That is not a refresh that
            // failed, and the banner for one would be the wrong sentence: the
            // repository decides what is browsed next and this screen follows.
            // Anything else, a dead network included, proves nothing (US-018).
            val gone = result is LumoResult.Failure &&
                activeSource.onFailureNaming(sourceId, result.error)

            _state.update {
                // The source may have changed while this ran. The outcome belongs
                // to the catalogue that asked, not to the one that replaced it.
                if (it.sourceId != sourceId) {
                    it
                } else {
                    it.copy(
                        refreshing = false,
                        // Only a failure is worth saying out loud. A success is
                        // visible in the list itself and in the origin indicator.
                        refreshFailed = result is LumoResult.Failure && !gone,
                    )
                }
            }
        }
    }

    /** Null is every channel of the source, which is what the screen opens on. */
    fun onCategorySelected(categoryId: String?) = _state.update {
        it.copy(
            filter = categoryId?.let(CatalogueFilter::Category) ?: CatalogueFilter.All,
        )
    }

    /**
     * Filters the grid to one favourite group (S4-06).
     *
     * The television's only way into favourites, and it is deliberately not a
     * screen: a group filters a grid exactly as a category does, so it is a chip
     * in the same strip. `S2-13` ruled against rails on this screen — a rail caps
     * what it holds, and its eight-hundredth channel cannot be reached — and
     * nothing about a group changes that reasoning.
     */
    fun onGroupSelected(groupId: String) =
        _state.update { it.copy(filter = CatalogueFilter.Group(groupId)) }

    /** Filters the grid to what was watched recently, on any of this account's devices. */
    fun onRecentSelected() =
        _state.update { it.copy(filter = CatalogueFilter.Recent) }

    // ---- favourites (US-12) -------------------------------------------------

    /**
     * A tap on the heart.
     *
     * Not starred yet, so it goes to the default group without a question. This is
     * the frequent gesture and it costs one thumb. Already starred, and the sheet
     * opens instead of silently unstarring: a channel filed in two groups has no
     * single thing a tap could undo, and guessing would remove it from a group the
     * person never mentioned.
     */
    fun onFavoriteClicked(channel: Channel) {
        if (isFavorited(channel.id)) {
            openGroupSheet(channel)
        } else {
            star(channel.id, groupId = null, favorite = true)
        }
    }

    /** A long press always opens the sheet, starred or not. */
    fun onFavoriteLongPressed(channel: Channel) = openGroupSheet(channel)

    fun onGroupToggled(channelId: String, groupId: String, checked: Boolean) {
        if (checked) {
            star(channelId, groupId, favorite = true)
        } else {
            val favoriteId = favoriteIdOf(channelId, groupId) ?: return
            unstar(channelId, groupId, favoriteId)
        }
    }

    /**
     * Creates a group and files the channel in it, in that order.
     *
     * The two are one intention — nobody creates "Documentaire" for the pleasure
     * of an empty group — so a failure to file after a successful creation leaves
     * the group behind rather than trying to undo it. An empty group the user can
     * see and delete beats a silent rollback of something they watched happen.
     */
    fun onGroupCreated(channelId: String, name: String) {
        viewModelScope.launch {
            when (val created = favorites.createGroup(name)) {
                is LumoResult.Failure -> _state.update { it.copy(favoriteError = created.error) }
                is LumoResult.Success -> star(channelId, created.value.id, favorite = true)
            }
        }
    }

    fun onGroupSheetDismissed() = _state.update { it.copy(sheetChannel = null) }

    /** The failure has been shown. Clearing it stops it reappearing on rotation. */
    fun onFavoriteErrorShown() = _state.update { it.copy(favoriteError = null) }

    private fun openGroupSheet(channel: Channel) {
        _state.update { it.copy(sheetChannel = channel) }
    }

    private fun star(channelId: String, groupId: String?, favorite: Boolean) {
        // Optimistic, and it is not a nicety: a heart that takes a round trip to
        // fill is a heart somebody taps twice, and the second tap is a second
        // request against a state that has already changed.
        _state.update { it.copy(pendingFavorites = it.pendingFavorites + (channelId to favorite)) }

        viewModelScope.launch {
            val result = favorites.add(channelId, groupId)
            settle(channelId, result)
        }
    }

    private fun unstar(channelId: String, groupId: String, favoriteId: String) {
        // Removing a channel from one group does not unstar it: it may well be in
        // another. Emptying the heart here would be a lie the next Room emission
        // corrects a moment later, which is a flicker on a list somebody is
        // looking at.
        val stillElsewhere = _state.value.stillFavoritedWithout(channelId, groupId)
        _state.update {
            it.copy(pendingFavorites = it.pendingFavorites + (channelId to stillElsewhere))
        }

        viewModelScope.launch {
            settle(channelId, favorites.remove(favoriteId))
        }
    }

    /**
     * Drops the optimistic entry, and names the failure if there was one.
     *
     * On success the entry is dropped rather than kept: Room has the truth by now,
     * and an override left in place would outlive the change it was standing in
     * for — a heart that stays filled after the favourite is removed from another
     * device.
     */
    private fun settle(channelId: String, result: LumoResult<*>) {
        _state.update {
            it.copy(
                pendingFavorites = it.pendingFavorites - channelId,
                favoriteError = (result as? LumoResult.Failure)?.error,
            )
        }
    }

    private fun isFavorited(channelId: String): Boolean =
        _state.value.pendingFavorites[channelId] ?: (channelId in _state.value.favoritedChannelIds)

    private fun favoriteIdOf(channelId: String, groupId: String): String? =
        _state.value.favorites
            .firstOrNull { it.channel.id == channelId && it.groupId == groupId }
            ?.favoriteId

    /**
     * Favourites and recent channels, **of the active source only** (US-018).
     *
     * Both lists are the account's — a group may hold channels from two
     * subscriptions, and "recently watched" spans every source. What this screen
     * shows is the share that belongs to the catalogue on display, so each is
     * combined with the source being browsed and filtered on the device, the API
     * having no such filter. Nothing is deleted: switching back shows them again.
     *
     * Groups are not filtered. A group is the account's, and one that holds
     * nothing from this source already gets no chip — see
     * [LiveState.groupsWithChannels].
     */
    private fun observeFavorites() {
        val browsed = _state.map { it.sourceId }.distinctUntilChanged()

        viewModelScope.launch {
            favorites.groups().collect { groups -> _state.update { it.copy(groups = groups) } }
        }
        viewModelScope.launch {
            combine(recents.recent(), browsed) { rows, sourceId -> rows.channelsOfSource(sourceId) }
                .collect { rows -> _state.update { it.copy(recent = rows) } }
        }
        // Cheap — one request against a list the server keeps short — and the
        // moment somebody expects to see what they watched on the phone is the
        // moment they turn the television on.
        viewModelScope.launch { recents.refresh() }
        viewModelScope.launch {
            combine(favorites.favorites(), browsed) { rows, sourceId -> rows.ofSource(sourceId) }
                .collect { rows -> _state.update { it.copy(favorites = rows) } }
        }
        viewModelScope.launch {
            favorites.favoritedChannelIds().collect { ids ->
                _state.update { it.copy(favoritedChannelIds = ids) }
            }
        }
    }
}

/**
 * Which shelf the grid is showing.
 *
 * One value rather than a nullable category id beside a nullable group id: those
 * two have a state that means nothing — both set — and the screen would have to
 * decide which one wins every time it drew.
 */
sealed interface CatalogueFilter {

    /** Every channel of the source. What the screen opens on. */
    data object All : CatalogueFilter

    data class Category(val id: String) : CatalogueFilter

    data class Group(val id: String) : CatalogueFilter

    /**
     * The channels this account watched recently (S4-08).
     *
     * A chip like the others, and that settles the divergence with
     * `docs/design/api-gaps.md` M5, which described a *rail* as the television's
     * first screen. `S2-13` chose a grid over rails because a rail caps what it
     * holds and its eight-hundredth channel cannot be reached — an objection that
     * never applied to a list the server keeps short on purpose. What it did not
     * justify was opening a second mechanism on a screen that already has one.
     */
    data object Recent : CatalogueFilter
}

/** What the grid's contents depend on, gathered so the flow restarts on a real change. */
private data class ChannelQuery(
    val sourceId: String?,
    val filter: CatalogueFilter,
    val favorites: List<FavoriteChannel>,
    val recent: List<Channel>,
)

sealed interface LiveStep {

    data object Loading : LiveStep

    /** No source registered yet. The source tab is where that starts. */
    data object NoSource : LiveStep

    /**
     * Several sources, and this device has not been told which to browse.
     *
     * The shell's chooser sits on top of this screen while it lasts (US-018). The
     * step exists so that what is underneath says the same thing, rather than
     * "no channels yet" — which would be false.
     */
    data object NeedsChoice : LiveStep

    /**
     * The first import is running, and this device holds nothing of the source.
     *
     * Only a **first** import: a source that refreshes keeps its grid (lot C4).
     */
    data object Importing : LiveStep

    /** The first import failed, and this device holds nothing of the source. */
    data object ImportFailed : LiveStep

    /**
     * The server could not be reached and there is nothing cached to show
     * (US-024, "Indisponibilité et hors ligne"). Not [NoSource]: an outage
     * proves nothing about the account, and the screen says so.
     */
    data object Unreachable : LiveStep

    data object Browsing : LiveStep
}

data class LiveState(
    val step: LiveStep = LiveStep.Loading,
    val sourceId: String? = null,
    val categories: List<Category> = emptyList(),
    /**
     * Where the list on screen came from.
     *
     * US-08 asks for the offline case to be *said*, discreetly. It cannot be
     * inferred from the data — Room has rows either way — so the repository, which
     * is the only thing that knows whether the last refresh succeeded, says it.
     */
    val origin: DataOrigin = DataOrigin.Cache,
    /** Which shelf the grid is showing: everything, one category, or one group. */
    val filter: CatalogueFilter = CatalogueFilter.All,
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,

    /**
     * What the active source is doing, said above the grid or in the first-import
     * message: the real step of a refresh, or why the last attempt failed and that
     * the list may be out of date (US-024). Null when there is nothing to say.
     */
    val notice: SourceNotice? = null,

    // ---- favourites (US-12) ------------------------------------------------

    /** The account's groups, for the picker. Empty until the first one exists. */
    val groups: List<FavoriteGroup> = emptyList(),

    /**
     * The favourites **of the source being browsed**, which is what says *which*
     * groups a channel is in. The account may hold others: they belong to another
     * catalogue, and they come back with it (US-018).
     */
    val favorites: List<FavoriteChannel> = emptyList(),

    /** Starred channel ids, as Room holds them. The grid draws its hearts from this. */
    val favoritedChannelIds: Set<String> = emptySet(),

    /** What was watched recently in this source, on any device of this account. Server order. */
    val recent: List<Channel> = emptyList(),

    /**
     * Hearts whose change has not come back yet, and what they should show
     * meanwhile.
     *
     * Read **before** [favoritedChannelIds] and dropped the moment the call
     * settles, in either direction: kept on success it would outlive the change it
     * stood in for, and a heart would stay filled after another device removed the
     * favourite.
     */
    val pendingFavorites: Map<String, Boolean> = emptyMap(),

    /** The channel whose group picker is open, or null. */
    val sheetChannel: Channel? = null,

    /** The last favourite write that failed. Offline is the ordinary case here. */
    val favoriteError: LumoError? = null,

    /**
     * The programme on air per channel id, for the pages the grid has shown
     * (S7-04). Absent for a channel with nothing on, and the card then draws
     * nothing under the name — no placeholder, no "unavailable" (S7-03).
     */
    val onAir: Map<String, EpgProgramme> = emptyMap(),

    /**
     * The programme windows the Guide has read, per channel id (S9-04-05).
     *
     * The same grouped pages that fill [onAir], kept whole so that the Guide can
     * show the programme after the current one. Absent for a channel with no
     * guide — no `tvg_id`, no guide on the source, not loaded — and the Guide
     * row then draws no programme line at all (S7-03, S9-04).
     */
    val guideProgrammes: Map<String, List<EpgProgramme>> = emptyMap(),
) {

    /**
     * This screen, pointed at [source] (US-018).
     *
     * **Same source, nothing moves.** A source's status changes while it is on
     * screen — importing, then ready — and a category somebody picked has to
     * survive that.
     *
     * [cachedItems] is how many channels of [source] Room holds. It only matters
     * for a source that was never ingested — see `CatalogueSource.face`.
     *
     * **Another source, and everything that belonged to the old one goes**: the
     * filter, the categories it was picked from, the refresh outcome, the
     * favourites and recent channels drawn from it, a group sheet open on one of
     * its channels. A category id means nothing in another catalogue, and a
     * filter left behind would open the new source on an empty grid with no chip
     * lit to explain why.
     *
     * What survives is what belongs to the account: the groups, the hearts Room
     * holds, a favourite write still in flight and its outcome.
     */
    fun browsing(source: CatalogueSource, cachedItems: Int = 0): LiveState {
        val step = when (source.face(cachedItems)) {
            CatalogueFace.Loading -> LiveStep.Loading
            CatalogueFace.NoSource -> LiveStep.NoSource
            CatalogueFace.NeedsChoice -> LiveStep.NeedsChoice
            CatalogueFace.Importing -> LiveStep.Importing
            CatalogueFace.ImportFailed -> LiveStep.ImportFailed
            CatalogueFace.Unreachable -> LiveStep.Unreachable
            CatalogueFace.Browsing -> LiveStep.Browsing
        }

        if (source.sourceId == sourceId) return copy(step = step)

        // The guide goes with the source too: `onAir` and `guideProgrammes`
        // reset on the next page shown, and a programme of the old source under
        // a new card in between is the GD-03 failure.
        return LiveState(
            step = step,
            sourceId = source.sourceId,
            // The active source's, written by its own collector: whichever of the
            // two runs first, the notice on screen is never the old source's.
            notice = notice,
            groups = groups,
            favoritedChannelIds = favoritedChannelIds,
            pendingFavorites = pendingFavorites,
            favoriteError = favoriteError,
        )
    }

    /** The open category, for a screen that draws only category chips. */
    val selectedCategoryId: String?
        get() = (filter as? CatalogueFilter.Category)?.id

    /** The open group, for the television's strip. */
    val selectedGroupId: String?
        get() = (filter as? CatalogueFilter.Group)?.id

    /**
     * The groups worth offering a chip, which is the ones that hold something.
     *
     * An empty group's chip filters onto nothing, and a grid that goes blank after
     * an `OK` looks like a breakage rather than an empty shelf. On a phone the
     * favourites tab can afford to say "this group is empty"; a strip on a
     * television has no room to say anything.
     */
    val groupsWithChannels: List<FavoriteGroup>
        get() = groups.filter { group -> favorites.any { it.groupId == group.id } }

    /** What the heart on this channel should show right now. */
    fun isFavorited(channelId: String): Boolean =
        pendingFavorites[channelId] ?: (channelId in favoritedChannelIds)

    /** The groups this channel is filed in, by id. */
    fun groupsOf(channelId: String): Set<String> =
        favorites.filter { it.channel.id == channelId }.map { it.groupId }.toSet()

    /**
     * "En ce moment" and "Ensuite" for one channel of the Guide, at [now]
     * (S9-04-05).
     *
     * Pure, over [guideProgrammes] and the clock: both null when the guide has
     * nothing for this channel, and its row is then the channel's name alone.
     */
    fun nowAndNext(channelId: String, now: Instant): NowAndNext =
        currentAndNext(guideProgrammes[channelId].orEmpty(), now)

    /**
     * After removing this channel from this group, is it still starred elsewhere?
     *
     * What the heart should show while the removal is in flight. A channel filed
     * in "Documentaire" and in "Ciné" and taken out of one is still a favourite,
     * and emptying its heart would be a lie the next Room emission corrects a
     * moment later — a flicker on a list somebody is looking at.
     */
    fun stillFavoritedWithout(channelId: String, groupId: String): Boolean =
        favorites.any { it.channel.id == channelId && it.groupId != groupId }
}

/**
 * How many channels one guide request covers on the television's grid.
 *
 * Six rows of four: three screens of a two-row grid, so that a viewer stepping
 * down the catalogue costs one request every three screens and not one per row.
 * Well under the contract's cap of 100.
 */
const val EPG_PAGE_SIZE: Int = 24
