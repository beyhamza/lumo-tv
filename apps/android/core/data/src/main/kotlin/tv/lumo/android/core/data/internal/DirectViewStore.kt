package tv.lumo.android.core.data.internal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import tv.lumo.android.core.data.DirectView
import tv.lumo.android.core.data.di.DirectViewPreferences

/**
 * Where a device remembers which view of Direct each source was left on (S9-04).
 *
 * An interface, as [ActiveSourceStore] is, so the rule the repository states —
 * **one entry per source, and only the view** — is in the signatures rather than
 * an implementation detail. An in-memory implementation is what the repository
 * tests use.
 *
 * What is deliberately absent is as important as what is here: there is no
 * search and no filter, because GD-02 keeps those to the session. A store that
 * could persist them would invite a screen to do it.
 */
internal interface DirectViewStore {

    suspend fun read(sourceId: String): DirectView?

    suspend fun write(sourceId: String, view: DirectView)
}

/**
 * The Preferences DataStore behind [DirectViewStore].
 *
 * <h2>Keyed by source, on this device</h2>
 *
 * The choice belongs to the device and to the source: changing view on the
 * television must not move the phone, and each source opens on the view it was
 * left on — the same rule as the active source (US-018), one level down.
 *
 * <h2>Not encrypted, deliberately</h2>
 *
 * What is stored is one of two words. Losing it costs one default view, and the
 * source id is already an opaque identifier the device holds elsewhere.
 *
 * <h2>An unreadable file is "no memory", not a crash</h2>
 *
 * The worst outcome of losing this file is opening a source on Channels instead
 * of Guide. That is not worth taking the screen down for, so a read that fails
 * answers null and the caller applies [DirectView.Default].
 */
internal class DataStoreDirectViewStore @Inject constructor(
    @DirectViewPreferences private val preferences: DataStore<Preferences>,
) : DirectViewStore {

    override suspend fun read(sourceId: String): DirectView? =
        preferences.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .first()[keyFor(sourceId)]
            ?.let(DirectView::fromStored)

    override suspend fun write(sourceId: String, view: DirectView) = tolerating {
        preferences.edit { it[keyFor(sourceId)] = view.name }
    }

    /**
     * A full disk makes the memory last for this run only. The screen has
     * already moved to the chosen view by then, and failing the gesture over a
     * preference that could not be saved would be the worse of the two outcomes.
     */
    private suspend fun tolerating(write: suspend () -> Unit) {
        try {
            write()
        } catch (_: IOException) {
            // Nothing to report: the view is not worth a log line, and the next
            // launch simply opens on the default again.
        }
    }

    private fun keyFor(sourceId: String) = stringPreferencesKey(KEY_PREFIX + sourceId)

    private companion object {
        const val KEY_PREFIX = "direct_view_"
    }
}
