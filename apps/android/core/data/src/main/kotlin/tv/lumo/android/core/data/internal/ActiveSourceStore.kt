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
import tv.lumo.android.core.data.di.ActiveSourcePreferences

/**
 * Where the device remembers which source each account browses (US-018).
 *
 * An interface so the repository's rules can be tested against memory, and so
 * the one property that matters here — **one entry per account** — is stated in
 * the signatures rather than left to an implementation detail.
 */
internal interface ActiveSourceStore {

    suspend fun read(accountId: String): String?

    suspend fun write(accountId: String, sourceId: String)

    suspend fun clear(accountId: String)
}

/**
 * The Preferences DataStore behind [ActiveSourceStore].
 *
 * <h2>Keyed by account, and never wiped on sign-out</h2>
 *
 * A shared television is signed in by more than one person over its life. The
 * key carries the account id, so a second account can neither see nor overwrite
 * the first one's choice — and because of that, signing out has nothing to
 * clear: the entry is unreachable from any other account, and it is exactly what
 * the same person expects to find when they sign back in.
 *
 * <h2>Not encrypted, deliberately</h2>
 *
 * A source id is an opaque UUID the server hands to this account only; it opens
 * nothing without the session, and the session is what `core:auth` encrypts.
 *
 * <h2>An unreadable file is "no choice", not a crash</h2>
 *
 * The worst outcome of losing this file is being asked once which source to
 * browse. That is not worth taking the application down for, so a read that
 * fails answers null and the resolver does the rest.
 */
internal class DataStoreActiveSourceStore @Inject constructor(
    @ActiveSourcePreferences private val preferences: DataStore<Preferences>,
) : ActiveSourceStore {

    override suspend fun read(accountId: String): String? =
        preferences.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .first()[keyFor(accountId)]

    override suspend fun write(accountId: String, sourceId: String) = tolerating {
        preferences.edit { it[keyFor(accountId)] = sourceId }
    }

    override suspend fun clear(accountId: String) = tolerating {
        preferences.edit { it.remove(keyFor(accountId)) }
    }

    /**
     * A full disk makes the choice last for this run only. The screen has already
     * moved to the chosen source by then, and failing the gesture over a
     * preference that could not be saved would be the worse of the two outcomes.
     */
    private suspend fun tolerating(write: suspend () -> Unit) {
        try {
            write()
        } catch (_: IOException) {
            // Nothing to report: the identifier is not worth a log line, and the
            // next launch simply resolves again from the list.
        }
    }

    private fun keyFor(accountId: String) = stringPreferencesKey(KEY_PREFIX + accountId)

    private companion object {
        const val KEY_PREFIX = "active_source_"
    }
}
