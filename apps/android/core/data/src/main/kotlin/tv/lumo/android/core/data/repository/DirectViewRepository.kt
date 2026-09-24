package tv.lumo.android.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import tv.lumo.android.core.data.DirectView
import tv.lumo.android.core.data.internal.DirectViewStore

/**
 * Which view of Direct a source was left on, on this device (S9-04, GD-02).
 *
 * <h2>One thing is remembered, and only one</h2>
 *
 * This is the *between-sessions* memory of the Direct destination, and it holds
 * exactly one fact per source: Channels or Guide. Search and filter are session
 * state — they are shared by the two views while a source is open, and they are
 * gone the moment the app closes or the source changes (GD-02, GD-03). Keeping
 * them out of this interface is the rule, not an omission: a repository that
 * could also store a query is one a screen eventually stores a query in.
 *
 * <h2>An interface, for the screens' sake</h2>
 *
 * The view models depend on this and not on the DataStore, so a screen's own
 * logic — seed the view, remember a switch, drop everything on a switch of
 * source — can be driven by a fake that needs neither Android nor a file.
 */
interface DirectViewRepository {

    /**
     * The view to open [sourceId] on.
     *
     * [DirectView.Default] — Channels — when this device has no memory of the
     * source, which is the first open and every source added since (GD-02).
     */
    suspend fun viewFor(sourceId: String): DirectView

    /**
     * Remembers the view this source was left on.
     *
     * Called on a switch between Channels and Guide, not on every frame: the
     * answer is read back on the next open. A change of source is not a write —
     * the new source's own memory is read, and the old source keeps its.
     */
    suspend fun remember(sourceId: String, view: DirectView)
}

/**
 * [DirectViewRepository] over the device's own store.
 *
 * Thin on purpose: the rule it carries — a source remembers nothing but its
 * view, and only between sessions — is entirely in what it exposes, so there is
 * no branch here for a test to pin. What is worth testing is the key scheme
 * underneath, and that test runs the real DataStore over a real file
 * (`DirectViewStoreTest`).
 */
@Singleton
internal class DefaultDirectViewRepository @Inject constructor(
    private val store: DirectViewStore,
) : DirectViewRepository {

    override suspend fun viewFor(sourceId: String): DirectView =
        store.read(sourceId) ?: DirectView.Default

    override suspend fun remember(sourceId: String, view: DirectView) =
        store.write(sourceId, view)
}
