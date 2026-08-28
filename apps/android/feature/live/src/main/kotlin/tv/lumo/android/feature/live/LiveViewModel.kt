package tv.lumo.android.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.model.Cached
import tv.lumo.android.core.data.model.Category
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.data.repository.CatalogueRepository
import tv.lumo.android.core.data.repository.FavoriteRepository
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.core.data.valueOrNull
import tv.lumo.android.network.generated.model.SourceStatus

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
 * The one exception is a source that is still importing: there is nothing to
 * cache yet, and the screen says so rather than showing an empty list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val catalogue: CatalogueRepository,
    private val sources: SourceRepository,
    private val favorites: FavoriteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state

    /**
     * The channels of the chosen category, paginated straight out of SQLite.
     *
     * `cachedIn` keeps the loaded windows across a rotation; without it, turning
     * the phone re-reads the first page and jumps the user back to the top.
     */
    val channels: Flow<PagingData<Channel>> = _state
        .map { ChannelQuery(it.sourceId, it.filter, it.favorites) }
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

        else -> catalogue.channels(
            query.sourceId,
            (query.filter as? CatalogueFilter.Category)?.id,
        )
    }

    init {
        load()
        observeFavorites()
    }

    private fun load() {
        viewModelScope.launch {
            val source = sources.sources().valueOrNull()?.firstOrNull()

            if (source == null) {
                _state.update { it.copy(step = LiveStep.NoSource) }
                return@launch
            }

            val sourceId = source.id.toString()

            if (source.status != SourceStatus.READY) {
                // Nothing has been ingested yet. An empty channel list here would
                // read as "this source has no channels", which is a different and
                // much more alarming thing than "it is still importing".
                _state.update { it.copy(sourceId = sourceId, step = LiveStep.NotReadyYet) }
                return@launch
            }

            _state.update { it.copy(sourceId = sourceId, step = LiveStep.Browsing) }
            observeCategories(sourceId)

            if (catalogue.cachedChannelCount(sourceId) == 0) {
                refresh()
            }
        }
    }

    private fun observeCategories(sourceId: String) {
        viewModelScope.launch {
            catalogue.categories(sourceId).collect { cached ->
                _state.update {
                    it.copy(categories = cached.value, origin = cached.origin)
                }
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
            _state.update {
                it.copy(
                    refreshing = false,
                    // Only a failure is worth saying out loud. A success is
                    // visible in the list itself and in the origin indicator.
                    refreshFailed = result is LumoResult.Failure,
                )
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

    private fun observeFavorites() {
        viewModelScope.launch {
            favorites.groups().collect { groups -> _state.update { it.copy(groups = groups) } }
        }
        viewModelScope.launch {
            favorites.favorites().collect { rows -> _state.update { it.copy(favorites = rows) } }
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
}

/** What the grid's contents depend on, gathered so the flow restarts on a real change. */
private data class ChannelQuery(
    val sourceId: String?,
    val filter: CatalogueFilter,
    val favorites: List<FavoriteChannel>,
)

sealed interface LiveStep {

    data object Loading : LiveStep

    /** No source registered yet. The source tab is where that starts. */
    data object NoSource : LiveStep

    /** A source exists but has not finished importing, or failed to. */
    data object NotReadyYet : LiveStep

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

    // ---- favourites (US-12) ------------------------------------------------

    /** The account's groups, for the picker. Empty until the first one exists. */
    val groups: List<FavoriteGroup> = emptyList(),

    /** Every favourite of the account, which is what says *which* groups a channel is in. */
    val favorites: List<FavoriteChannel> = emptyList(),

    /** Starred channel ids, as Room holds them. The grid draws its hearts from this. */
    val favoritedChannelIds: Set<String> = emptySet(),

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
) {

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
