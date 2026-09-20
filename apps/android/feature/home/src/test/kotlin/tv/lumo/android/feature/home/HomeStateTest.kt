package tv.lumo.android.feature.home

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Test
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.SourceNotice
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.ContinueItem
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.core.data.model.FavoriteGroup
import tv.lumo.android.core.data.model.HOME_RAIL_SIZE
import tv.lumo.android.core.data.model.ResumableFilm
import tv.lumo.android.core.data.model.VodItem
import tv.lumo.android.core.data.model.WatchProgress
import tv.lumo.android.network.generated.model.IngestionErrorCode
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus
import tv.lumo.android.network.generated.model.SyncStep

/**
 * What the home screen shows, and when it says there is nothing to show (US-017).
 *
 * <h2>What would be invisible until it was wrong</h2>
 *
 * **The order of the rails.** Continue, Favourites, Live was validated as a
 * layout; a list built in any other order compiles, renders and looks deliberate.
 *
 * **An empty rail.** Hiding it is an acceptance criterion, and the failure mode is
 * a heading over nothing — which nobody notices on an account that has everything.
 *
 * **"Nothing here yet", said too early.** The three reads settle at different
 * speeds. An invitation to explore drawn before the slowest one has answered is a
 * sentence that is false for a second on every launch, on exactly the accounts
 * that have the most to show.
 *
 * **The source.** With one source every filter is a no-op (US-018).
 *
 * Everything here is fictional: no real title, no real channel (AGENTS.md §1).
 */
class HomeStateTest {

    // ---- sections ------------------------------------------------------------

    @Test
    fun `the rails come in the validated order`() {
        val state = browsing(
            continueWatching = listOf(film("film-a")),
            favorites = listOf(favorite("channel-a")),
            recent = listOf(channel("channel-b")),
        )

        assertThat(state.sections.map { it::class }).containsExactly(
            HomeSection.Continue::class,
            HomeSection.Favorites::class,
            HomeSection.Live::class,
        ).inOrder()
    }

    @Test
    fun `a rail with nothing in it is not there at all`() {
        val state = browsing(recent = listOf(channel("channel-b")))

        assertThat(state.sections).hasSize(1)
        assertThat(state.sections.single()).isInstanceOf(HomeSection.Live::class.java)
        assertThat(state.blank).isFalse()
    }

    @Test
    fun `favourites and recent channels are the active source's, and capped`() {
        val mine = (1..HOME_RAIL_SIZE + 3).map { favorite("channel-$it", position = it) }
        val theirs = favorite("channel-other", source = OTHER, position = 0)
        val watched = (1..HOME_RAIL_SIZE + 3).map { channel("recent-$it") } +
            channel("recent-other", source = OTHER)

        val state = browsing(favorites = listOf(theirs) + mine, recent = watched)

        val favorites = state.sections.filterIsInstance<HomeSection.Favorites>().single()
        assertThat(favorites.channels).hasSize(HOME_RAIL_SIZE)
        assertThat(favorites.channels.map { it.channel.sourceId }.toSet()).containsExactly(SOURCE)
        // The cap cuts the end of the user's own order, never the beginning.
        assertThat(favorites.channels.first().channel.id).isEqualTo("channel-1")

        val live = state.sections.filterIsInstance<HomeSection.Live>().single()
        assertThat(live.channels).hasSize(HOME_RAIL_SIZE)
        assertThat(live.channels.map { it.sourceId }.toSet()).containsExactly(SOURCE)
        assertThat(live.channels.first().id).isEqualTo("recent-1")
    }

    @Test
    fun `a channel filed in two groups is one card of the rail`() {
        val state = browsing(
            favorites = listOf(
                favorite("channel-a", group = "group-1", favoriteId = "f1"),
                favorite("channel-a", group = "group-2", favoriteId = "f2"),
            ),
        )

        val favorites = state.sections.filterIsInstance<HomeSection.Favorites>().single()
        assertThat(favorites.channels.map { it.channel.id }).containsExactly("channel-a")
    }

    @Test
    fun `nothing is drawn as a rail while there is no source to browse`() {
        val state = browsing(favorites = listOf(favorite("channel-a")))
            .copy(step = HomeStep.NeedsChoice)

        assertThat(state.sections).isEmpty()
        assertThat(state.blank).isFalse()
    }

    // ---- the invitation to explore -------------------------------------------

    @Test
    fun `a source with no activity invites to explore, once every read has answered`() {
        val state = browsing()

        assertThat(state.sections).isEmpty()
        assertThat(state.blank).isTrue()
        assertThat(state.waiting).isFalse()
    }

    @Test
    fun `nothing is concluded while the progress list has not answered`() {
        val state = browsing().copy(continueLoaded = false)

        assertThat(state.blank).isFalse()
        assertThat(state.waiting).isTrue()
    }

    @Test
    fun `a rail that is already there is shown without waiting for the others`() {
        val state = browsing(recent = listOf(channel("channel-b"))).copy(continueLoaded = false)

        assertThat(state.waiting).isFalse()
        assertThat(state.sections).hasSize(1)
    }

    // ---- following the active source -----------------------------------------

    @Test
    fun `another source drops the progress cards and keeps the account's lists`() {
        val onA = browsing(
            continueWatching = listOf(film("film-a")),
            favorites = listOf(favorite("channel-a"), favorite("channel-b", source = OTHER)),
        )

        val onB = onA.showing(HomeSource(HomeStep.Browsing, sourceId = OTHER))

        assertThat(onB.continueWatching).isEmpty()
        assertThat(onB.continueLoaded).isFalse()
        // Filtered on the way out, never deleted: the other source's favourite
        // was there all along, and is what the rail now shows.
        val favorites = onB.sections.filterIsInstance<HomeSection.Favorites>().single()
        assertThat(favorites.channels.map { it.channel.id }).containsExactly("channel-b")
    }

    @Test
    fun `the same source changing status keeps its rails on screen`() {
        val importing = browsing(continueWatching = listOf(film("film-a")))
            .copy(notice = SourceNotice.Refreshing(SyncStep.PARSING_CHANNELS))

        val ready = importing.showing(HomeSource(HomeStep.Browsing, sourceId = SOURCE))

        assertThat(ready.notice).isNull()
        assertThat(ready.continueWatching).hasSize(1)
        assertThat(ready.continueLoaded).isTrue()
    }

    // ---- reading the active source -------------------------------------------

    @Test
    fun `each state of the active source has its own face`() {
        assertThat(ActiveSourceState.Loading.asHomeSource().step).isEqualTo(HomeStep.Loading)
        assertThat(ActiveSourceState.None.asHomeSource().step).isEqualTo(HomeStep.NoSource)
        assertThat(ActiveSourceState.NeedsChoice(emptyList()).asHomeSource().step)
            .isEqualTo(HomeStep.NeedsChoice)
        // Not folded into "no source": nothing is known, so nobody is sent to a form.
        assertThat(ActiveSourceState.Unavailable.asHomeSource().step).isEqualTo(HomeStep.Unavailable)
    }

    @Test
    fun `an importing source says which step it is at, and is still browsed`() {
        val home = selected(source(SourceStatus.SYNCING, step = SyncStep.PARSING_VOD)).asHomeSource()

        assertThat(home.step).isEqualTo(HomeStep.Browsing)
        assertThat(home.notice).isEqualTo(SourceNotice.Refreshing(SyncStep.PARSING_VOD))
    }

    @Test
    fun `a source accepted and not started is importing, with no step yet`() {
        val home = selected(source(SourceStatus.PENDING)).asHomeSource()

        assertThat(home.notice).isEqualTo(SourceNotice.Refreshing(null))
    }

    @Test
    fun `a failed source says why, and is still browsed`() {
        val home = selected(
            source(SourceStatus.ERROR, error = IngestionErrorCode.SOURCE_AUTH_FAILED),
        ).asHomeSource()

        assertThat(home.step).isEqualTo(HomeStep.Browsing)
        assertThat(home.notice).isEqualTo(SourceNotice.Failed(IngestionErrorCode.SOURCE_AUTH_FAILED))
    }

    @Test
    fun `a ready source, and a choice with no list behind it, say nothing`() {
        assertThat(selected(source(SourceStatus.READY)).asHomeSource().notice).isNull()

        // Offline: the device knows which source, the server could not be asked.
        val offline = ActiveSourceState.Selected(SOURCE, source = null, sources = emptyList())
        assertThat(offline.asHomeSource()).isEqualTo(HomeSource(HomeStep.Browsing, SOURCE, null))
    }

    @Test
    fun `a sibling source changing does not change what the home screen reads`() {
        val mine = source(SourceStatus.READY)
        val before = ActiveSourceState.Selected(SOURCE, mine, listOf(mine, source(SourceStatus.SYNCING, id = OTHER)))
        val after = ActiveSourceState.Selected(SOURCE, mine, listOf(mine, source(SourceStatus.READY, id = OTHER)))

        // Equal, so `distinctUntilChanged` swallows it and the rails do not reload.
        assertThat(after.asHomeSource()).isEqualTo(before.asHomeSource())
    }

    // ---- fixtures ------------------------------------------------------------

    private fun browsing(
        continueWatching: List<ContinueItem> = emptyList(),
        favorites: List<FavoriteChannel> = emptyList(),
        recent: List<Channel> = emptyList(),
    ) = HomeState(
        step = HomeStep.Browsing,
        sourceId = SOURCE,
        continueWatching = continueWatching,
        groups = listOf(group("group-1", 0), group("group-2", 1)),
        favorites = favorites,
        recent = recent,
        continueLoaded = true,
        favoritesLoaded = true,
        recentLoaded = true,
    )

    private fun group(id: String, position: Int) =
        FavoriteGroup(id = id, name = id, position = position, isDefault = position == 0)

    private fun channel(id: String, source: String = SOURCE) = Channel(
        id = id,
        sourceId = source,
        categoryId = null,
        name = "Channel $id",
        logoUrl = null,
        number = null,
        quality = null,
        isAdult = false,
    )

    private fun favorite(
        channelId: String,
        source: String = SOURCE,
        group: String = "group-1",
        position: Int = 0,
        favoriteId: String = "favorite-$channelId",
    ) = FavoriteChannel(
        favoriteId = favoriteId,
        groupId = group,
        position = position,
        channel = channel(channelId, source),
    )

    private fun film(id: String) = ContinueItem.Film(
        ResumableFilm(
            film = VodItem(
                id = id,
                sourceId = SOURCE,
                categoryId = null,
                name = "Film $id",
                posterUrl = null,
                year = null,
                durationSeconds = null,
                rating = null,
                plot = null,
                isAdult = false,
            ),
            progress = WatchProgress(SOURCE, id, positionMs = 1_000, durationMs = null),
        ),
    )

    private fun selected(source: Source) =
        ActiveSourceState.Selected(source.id.toString(), source, listOf(source))

    private fun source(
        status: SourceStatus,
        step: SyncStep? = null,
        error: IngestionErrorCode? = null,
        id: String = SOURCE,
    ) = Source(
        id = UUID.fromString(id),
        label = "Test source",
        kind = SourceKind.M3U_URL,
        status = status,
        autoSync = true,
        syncStep = step,
        errorCode = error,
    )

    private companion object {
        const val SOURCE = "00000000-0000-0000-0000-00000000000a"
        const val OTHER = "00000000-0000-0000-0000-00000000000b"
    }
}
