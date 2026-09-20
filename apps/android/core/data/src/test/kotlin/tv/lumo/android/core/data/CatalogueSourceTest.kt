package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.FavoriteChannel
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * What the three catalogue screens make of the active source (US-018).
 *
 * One mapping for channels, films and series, and the cases worth pinning are
 * the ones a connected single-source account never meets: a source still
 * importing, a choice remembered offline, several sources and no choice.
 */
class CatalogueSourceTest {

    private val a = benchSource("Source A")
    private val b = benchSource("Source B")

    @Test
    fun `a ready source is browsed, and a playlist says so`() {
        val playlist = ActiveSourceState.Selected(a.key, a, listOf(a))
        val panel = a.copy(kind = SourceKind.XTREAM)

        assertThat(playlist.asCatalogueSource())
            .isEqualTo(CatalogueSource.Ready(a.key, isPlaylist = true))
        assertThat(ActiveSourceState.Selected(a.key, panel, listOf(panel)).asCatalogueSource())
            .isEqualTo(CatalogueSource.Ready(a.key, isPlaylist = false))
    }

    @Test
    fun `a source with a previous catalogue is browsed in every status`() {
        // Lot C4: the server lists it and Room caches it while it refreshes and
        // after a failed attempt. Hiding it was the defect.
        val synced = java.time.OffsetDateTime.parse("2026-09-01T10:00:00Z")

        listOf(SourceStatus.PENDING, SourceStatus.SYNCING, SourceStatus.ERROR).forEach { status ->
            val source = a.copy(status = status, lastSyncedAt = synced)

            assertThat(ActiveSourceState.Selected(a.key, source, listOf(source)).asCatalogueSource())
                .isEqualTo(CatalogueSource.Ready(a.key, isPlaylist = true))
        }
    }

    @Test
    fun `a source that was never ingested is a first import, running or failed`() {
        listOf(
            SourceStatus.PENDING to false,
            SourceStatus.SYNCING to false,
            SourceStatus.ERROR to true,
        ).forEach { (status, failed) ->
            val source = a.copy(status = status, lastSyncedAt = null)

            assertThat(ActiveSourceState.Selected(a.key, source, listOf(source)).asCatalogueSource())
                .isEqualTo(CatalogueSource.FirstImport(a.key, isPlaylist = true, failed = failed))
        }
    }

    @Test
    fun `the face of a grid, per status and cache`() {
        val importing = CatalogueSource.FirstImport(a.key, isPlaylist = false, failed = false)
        val failed = CatalogueSource.FirstImport(a.key, isPlaylist = false, failed = true)
        val ready = CatalogueSource.Ready(a.key, isPlaylist = false)

        // Nothing ingested and nothing cached: the only case with no grid, and
        // it says which of the two it is.
        assertThat(importing.face(cachedItems = 0)).isEqualTo(CatalogueFace.Importing)
        assertThat(failed.face(cachedItems = 0)).isEqualTo(CatalogueFace.ImportFailed)

        // The device holds rows: they are shown, whatever the server says.
        assertThat(importing.face(cachedItems = 12)).isEqualTo(CatalogueFace.Browsing)
        assertThat(failed.face(cachedItems = 12)).isEqualTo(CatalogueFace.Browsing)

        // The server holds a catalogue: browsing, with an empty cache too — the
        // screen fills it.
        assertThat(ready.face(cachedItems = 0)).isEqualTo(CatalogueFace.Browsing)
        assertThat(ready.face(cachedItems = 12)).isEqualTo(CatalogueFace.Browsing)

        assertThat(CatalogueSource.Loading.face(0)).isEqualTo(CatalogueFace.Loading)
        assertThat(CatalogueSource.NoSource.face(0)).isEqualTo(CatalogueFace.NoSource)
        assertThat(CatalogueSource.NeedsChoice.face(0)).isEqualTo(CatalogueFace.NeedsChoice)
    }

    @Test
    fun `a refresh moving through its steps does not restart a grid`() {
        val synced = java.time.OffsetDateTime.parse("2026-09-01T10:00:00Z")
        val connecting = a.copy(
            status = SourceStatus.SYNCING,
            lastSyncedAt = synced,
            syncStep = tv.lumo.android.network.generated.model.SyncStep.CONNECTING,
        )
        val parsing = connecting.copy(
            syncStep = tv.lumo.android.network.generated.model.SyncStep.PARSING_CHANNELS,
        )

        assertThat(ActiveSourceState.Selected(a.key, connecting, listOf(connecting)).asCatalogueSource())
            .isEqualTo(ActiveSourceState.Selected(a.key, parsing, listOf(parsing)).asCatalogueSource())
        // ...and ends on the same value once it is ready again.
        assertThat(ActiveSourceState.Selected(a.key, a, listOf(a)).asCatalogueSource())
            .isEqualTo(ActiveSourceState.Selected(a.key, parsing, listOf(parsing)).asCatalogueSource())
    }

    @Test
    fun `a section that failed to load is not presented as empty`() {
        assertThat(emptyGridOf(itemCount = 3, refreshing = false, refreshFailed = true))
            .isEqualTo(EmptyGrid.None)
        assertThat(emptyGridOf(itemCount = 0, refreshing = true, refreshFailed = false))
            .isEqualTo(EmptyGrid.Loading)
        assertThat(emptyGridOf(itemCount = 0, refreshing = false, refreshFailed = true))
            .isEqualTo(EmptyGrid.Unavailable)
        assertThat(emptyGridOf(itemCount = 0, refreshing = false, refreshFailed = false))
            .isEqualTo(EmptyGrid.Empty)
    }

    @Test
    fun `a choice remembered offline is browsed from the cache`() {
        // No `Source`, because the server could not be reached. The identifier is
        // all a cache-first grid needs, and refusing to draw it would turn
        // "offline" into "no catalogue".
        val offline = ActiveSourceState.Selected(a.key, source = null, sources = emptyList())

        assertThat(offline.asCatalogueSource())
            .isEqualTo(CatalogueSource.Ready(a.key, isPlaylist = false))
    }

    @Test
    fun `several sources and no choice is its own answer, not no source`() {
        assertThat(ActiveSourceState.NeedsChoice(listOf(a, b)).asCatalogueSource())
            .isEqualTo(CatalogueSource.NeedsChoice)
        assertThat(ActiveSourceState.None.asCatalogueSource()).isEqualTo(CatalogueSource.NoSource)
        assertThat(ActiveSourceState.Loading.asCatalogueSource()).isEqualTo(CatalogueSource.Loading)
    }

    @Test
    fun `a grid is not restarted by what it does not read`() {
        // The whole reason for the narrower type: a sibling that finishes
        // importing changes the state and must not change what a grid keys on.
        val before = ActiveSourceState.Selected(a.key, a, listOf(a, b.copy(status = SourceStatus.SYNCING)))
        val after = ActiveSourceState.Selected(a.key, a, listOf(a, b))

        assertThat(before).isNotEqualTo(after)
        assertThat(before.asCatalogueSource()).isEqualTo(after.asCatalogueSource())
    }

    // ---- favourites and recent channels ---------------------------------------

    @Test
    fun `favourites are filtered to the source, never removed`() {
        val all = listOf(
            favorite("f1", channel("c1", "Chaîne 01", sourceId = "source-a")),
            favorite("f2", channel("c2", "Chaîne 02", sourceId = "source-b")),
        )

        assertThat(all.ofSource("source-a").map { it.favoriteId }).containsExactly("f1")
        // Coming back to the other source finds its favourite where it was.
        assertThat(all.ofSource("source-b").map { it.favoriteId }).containsExactly("f2")
        assertThat(all).hasSize(2)
    }

    @Test
    fun `no active source shows no favourite and no recent channel`() {
        val channel = channel("c1", "Chaîne 01", sourceId = "source-a")

        assertThat(listOf(favorite("f1", channel)).ofSource(null)).isEmpty()
        assertThat(listOf(channel).channelsOfSource(null)).isEmpty()
        assertThat(listOf(channel).channelsOfSource("source-a")).containsExactly(channel)
    }

    // ---- helpers -----------------------------------------------------------

    private fun channel(id: String, name: String, sourceId: String) = Channel(
        id = id,
        sourceId = sourceId,
        categoryId = null,
        name = name,
        logoUrl = null,
        number = null,
        quality = null,
        isAdult = false,
    )

    private fun favorite(id: String, channel: Channel) = FavoriteChannel(
        favoriteId = id,
        groupId = "group",
        position = 0,
        channel = channel,
    )
}
