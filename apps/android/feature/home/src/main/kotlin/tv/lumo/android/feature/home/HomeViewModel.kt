package tv.lumo.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.OnAirTracker
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.ContinueWatchingRepository
import tv.lumo.android.core.data.repository.EpgRepository
import tv.lumo.android.core.data.repository.FavoriteRepository
import tv.lumo.android.core.data.repository.RecentChannelRepository

/**
 * The home screen of both applications (US-017, US-020).
 *
 * <h2>One view model, two surfaces, no rule of its own</h2>
 *
 * Everything that has a right and a wrong answer is a pure function somewhere a
 * test can reach it: which favourites and in what order (`aggregatedFavorites`),
 * which cards and in what order (`continueWatchingOf`), which sections and when to
 * say "nothing yet" ([HomeState]). What is left here is plumbing — follow the
 * active source, read three lists, put them in the state.
 *
 * <h2>Two of the three rails come out of Room, and one cannot</h2>
 *
 * Favourites and recent channels are cache-first, like every catalogue read: they
 * are on screen within a frame and offline. The progress list lives on the server
 * and nowhere else — the argument is on `ProgressRepository` — so "Continue" is a
 * request, arrives after the two others, and is absent offline. That asymmetry is
 * why the rails render as they arrive instead of waiting for each other.
 *
 * <h2>The source is followed, not read once</h2>
 *
 * `LiveViewModel`'s rule (US-018). Changing source from the shell leaves this
 * screen where it is and reloads it for the new one; nothing here navigates.
 *
 * <h2>What is on the Live cards, in one request (US-16, S9-03)</h2>
 *
 * The Live rail holds at most twelve channels of the active source, and their
 * guide is asked for **once for the rail**, through [OnAirTracker] — the same
 * one-request-per-page rule as the television's grid, and never a request per
 * card. What is on is recomputed against the clock each time the screen is
 * shown again ([onShown]), over the window held. A card with nothing draws
 * nothing. The way into the TV guide is S9-04's, and is not drawn here.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val activeSource: ActiveSourceRepository,
    private val continueWatching: ContinueWatchingRepository,
    private val favorites: FavoriteRepository,
    private val recentChannels: RecentChannelRepository,
    epg: EpgRepository,
    clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    private val guide = OnAirTracker(epg, clock, viewModelScope)

    /** The reload in flight, so that a second trigger replaces it instead of racing it. */
    private var loading: Job? = null

    init {
        observeCache()
        observeActiveSource()
        observeGuide()
    }

    /**
     * The Live rail's channels, followed as a list of ids: the rail changes when
     * a channel is played, and the guide is asked for the new id only.
     */
    private fun observeGuide() {
        viewModelScope.launch {
            _state.map { it.sourceId to it.liveChannelIds }
                .distinctUntilChanged()
                .collect { (sourceId, ids) ->
                    if (sourceId != null && ids.isNotEmpty()) guide.show(sourceId, ids)
                }
        }
        viewModelScope.launch {
            guide.onAir.collect { onAir -> _state.update { it.copy(onAir = onAir) } }
        }
    }

    /**
     * The two Room reads. They carry the **account's** lists; the state filters
     * them by source on the way out, which is what makes a change of source
     * instant for these two rails.
     */
    private fun observeCache() {
        viewModelScope.launch {
            favorites.groups().collect { groups -> _state.update { it.copy(groups = groups) } }
        }
        viewModelScope.launch {
            favorites.favorites().collect { rows ->
                _state.update { it.copy(favorites = rows, favoritesLoaded = true) }
            }
        }
        viewModelScope.launch {
            recentChannels.recent().collect { channels ->
                _state.update { it.copy(recent = channels, recentLoaded = true) }
            }
        }
    }

    /**
     * Follows the active source for as long as the screen lives.
     *
     * Reduced to [HomeSource] first, so that the rails reload when the source or
     * its status changes and not when a sibling source is renamed.
     */
    private fun observeActiveSource() {
        viewModelScope.launch {
            activeSource.state
                .map { it.asHomeSource() }
                .distinctUntilChanged()
                .collectLatest { source ->
                    _state.update { it.showing(source) }
                    // Also on a status change of the same source: an import that
                    // just finished is exactly when films this device could not
                    // resolve a moment ago become cards.
                    if (source.step == HomeStep.Browsing) reload()
                }
        }
    }

    /**
     * The screen has come (back) into view.
     *
     * Called by both surfaces each time they enter composition — on launch, on
     * return from a player, on return from another section. A home screen is the
     * one place where what is shown goes stale by *using the application*: the
     * film just watched has a new position, the channel just played is the most
     * recent one. Reloading on the way back in is what keeps "Continue" honest.
     *
     * The source list is asked again as well, for the reason the grids give:
     * resolved a while ago, its status may have moved. Still loading means the
     * repository is already asking.
     */
    fun onShown() {
        refreshSource()
        if (_state.value.step == HomeStep.Browsing && loading?.isActive != true) reload()
        // What was on when the screen was left may have ended: recomputed over
        // the window held, no request.
        guide.tick()
    }

    /**
     * Asks for the list of sources again.
     *
     * Also what "Try again" does when nothing could be fetched, and what the
     * screens call every few seconds **while a synchronisation is on display** —
     * from the composition, not from here, so that the polling stops with the
     * screen instead of keeping a radio awake behind a film.
     */
    fun refreshSource() {
        if (activeSource.state.value is ActiveSourceState.Loading) return
        viewModelScope.launch { activeSource.refresh() }
    }

    private fun reload() {
        val sourceId = _state.value.sourceId ?: return

        loading?.cancel()
        loading = viewModelScope.launch {
            // Fill the cache behind the two Room reads. Their result is not
            // reported: a failure leaves the rails exactly as the device last knew
            // them, which is the behaviour offline is supposed to have.
            launch { favorites.refresh() }
            launch { recentChannels.refresh() }

            val cards = continueWatching.all(sourceId)

            _state.update {
                // The answer belongs to the source that asked, which may no
                // longer be the one on screen.
                if (it.sourceId == sourceId) {
                    it.copy(continueWatching = cards, continueLoaded = true)
                } else {
                    it
                }
            }
        }
    }
}
