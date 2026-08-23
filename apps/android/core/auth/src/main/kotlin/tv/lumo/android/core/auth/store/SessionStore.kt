package tv.lumo.android.core.auth.store

import androidx.datastore.core.DataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import tv.lumo.android.core.auth.SessionTokens

/**
 * Where the session lives.
 *
 * An interface because [tv.lumo.android.core.auth.SessionManager] is the piece
 * whose behaviour has to be tested — single refresh in flight, rotation, sign
 * out — and testing it against a real DataStore would mean an instrumented test
 * for a rule that is pure logic.
 */
interface SessionStore {
    val sessions: Flow<SessionTokens?>

    suspend fun current(): SessionTokens?

    suspend fun save(tokens: SessionTokens)

    suspend fun clear()
}

@Singleton
class DataStoreSessionStore @Inject constructor(
    private val dataStore: DataStore<SessionTokens?>,
) : SessionStore {

    override val sessions: Flow<SessionTokens?> = dataStore.data

    override suspend fun current(): SessionTokens? = dataStore.data.first()

    override suspend fun save(tokens: SessionTokens) {
        dataStore.updateData { tokens }
    }

    override suspend fun clear() {
        dataStore.updateData { null }
    }
}
