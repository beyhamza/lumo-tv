package tv.lumo.android.feature.live

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.CatalogueSource
import tv.lumo.android.core.data.DirectView

/**
 * The two views of Direct on the phone (S9-04-02), and the explicit way in from
 * the home rail (S9-04-04).
 *
 * <h2>An explicit entry is not a remembered one</h2>
 *
 * "All channels" and "TV guide" name a view; a plain return to Direct respects
 * what the source remembers. The explicit door therefore overwrites the memory
 * for the open it triggered — and resets the query and the filter, because the
 * viewer named a view and not the session around it. What is `DirectViewStore`'s
 * is the write; what is this file's is what the state does with the request.
 *
 * <h2>What this screen owns, and what it does not</h2>
 *
 * The between-sessions memory — one view per device and per source — is
 * `DirectViewRepository`'s, and its key scheme is pinned by `DirectViewStoreTest`
 * in `core:data`. What this screen owns is what a *change of source* does with
 * the session state around it: the filter and the search belong to the source
 * that is open, so a switch drops them and opens the new source on the view that
 * source was left on (GD-02, GD-03). Those are decisions on [LiveState], and that
 * is where they are tested — no Paging, no Hilt, no UI.
 *
 * <h2>The half of the search that is silence</h2>
 *
 * "Found nothing" and "still loading" are the same `itemCount` on a Paging list,
 * and they must not be confused: one gets a sentence and two ways out, the other
 * gets nothing until the answer arrives (S9-04-02).
 */
class LiveDirectViewsTest {

    private val browsing = LiveState(
        step = LiveStep.Browsing,
        sourceId = "source-a",
        filter = CatalogueFilter.Category("news"),
        search = "tf1",
        view = DirectView.Guide,
    )

    // ---- the view a source opens on (GD-02) --------------------------------

    @Test
    fun `a new source opens on its own remembered view`() {
        val next = browsing.browsing(
            CatalogueSource.Ready("source-b", isPlaylist = true),
            rememberedView = DirectView.Guide,
        )

        assertThat(next.view).isEqualTo(DirectView.Guide)
    }

    @Test
    fun `a source this device has no memory of opens on Channels`() {
        val next = browsing.browsing(CatalogueSource.Ready("source-b", isPlaylist = true))

        assertThat(next.view).isEqualTo(DirectView.Channels)
        // The default is a named value and not a coincidence (GD-02).
        assertThat(DirectView.Default).isEqualTo(DirectView.Channels)
    }

    // ---- the explicit entry from home (S9-04-04) ---------------------------

    @Test
    fun `an explicit entry overwrites the remembered view and resets the session`() {
        // The source was left on Guide, with a query and a category narrowing it.
        val next = browsing.explicitEntry(DirectView.Channels)

        // "All channels" asked for Chaînes, and a source remembered on Guide must
        // not ignore it — that is the whole point of the explicit door.
        assertThat(next.view).isEqualTo(DirectView.Channels)
        // The viewer asked for a view, not to keep a query or a filter they did
        // not name (S9-04-04).
        assertThat(next.search).isEmpty()
        assertThat(next.filter).isEqualTo(CatalogueFilter.All)
    }

    @Test
    fun `an explicit entry into the other view is the mirror case`() {
        val channelsState = LiveState(
            step = LiveStep.Browsing,
            sourceId = "source-a",
            filter = CatalogueFilter.Recent,
            search = "arte",
            view = DirectView.Channels,
        )

        val next = channelsState.explicitEntry(DirectView.Guide)

        assertThat(next.view).isEqualTo(DirectView.Guide)
        assertThat(next.search).isEmpty()
        assertThat(next.filter).isEqualTo(CatalogueFilter.All)
    }

    @Test
    fun `what the explicit entry does not own is left alone`() {
        val next = browsing.explicitEntry(DirectView.Channels)

        // It names a view and clears the session state around it; the source on
        // screen and the categories it carries are not its business.
        assertThat(next.sourceId).isEqualTo(browsing.sourceId)
        assertThat(next.step).isEqualTo(browsing.step)
    }

    @Test
    fun `the view travels as a name and comes back as itself`() {
        // The navigation writes `view.name` into the Live entry's saved state and
        // the screen reads it back through `fromStored` (S9-04-04). A mapping that
        // lost a value would drop the viewer on the default instead.
        DirectView.entries.forEach { view ->
            assertThat(DirectView.fromStored(view.name)).isEqualTo(view)
        }
        assertThat(DirectView.fromStored(null)).isEqualTo(DirectView.Default)
    }

    // ---- what a source change drops (GD-03) --------------------------------

    @Test
    fun `a change of source drops the search and the filter`() {
        val next = browsing.browsing(CatalogueSource.Ready("source-b", isPlaylist = true))

        assertThat(next.search).isEmpty()
        assertThat(next.filter).isEqualTo(CatalogueFilter.All)
    }

    @Test
    fun `a status change of the same source costs nobody their search or view`() {
        // A source's status moves while it is on screen — importing, then ready —
        // and the search typed and the view picked have to survive that.
        val importing = browsing.browsing(
            CatalogueSource.FirstImport("source-a", isPlaylist = false, failed = false),
        )

        assertThat(importing.search).isEqualTo("tf1")
        assertThat(importing.filter).isEqualTo(CatalogueFilter.Category("news"))
        assertThat(importing.view).isEqualTo(DirectView.Guide)
    }

    // ---- the search that found nothing (S9-04-02) --------------------------

    @Test
    fun `a blank search is never a search that found nothing`() {
        val state = LiveState(search = "  ")

        assertThat(state.searchFoundNothing(itemCount = 0, loading = false)).isFalse()
    }

    @Test
    fun `a search still loading draws nothing, not a no-result sentence`() {
        val state = LiveState(search = "tf1")

        assertThat(state.searchFoundNothing(itemCount = 0, loading = true)).isFalse()
    }

    @Test
    fun `a search that returned no channel is the state with the two ways out`() {
        val state = LiveState(search = "tf1")

        assertThat(state.searchFoundNothing(itemCount = 0, loading = false)).isTrue()
        // A search that returned something is not that state.
        assertThat(state.searchFoundNothing(itemCount = 4, loading = false)).isFalse()
    }
}
