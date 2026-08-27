package tv.lumo.android.core.data.repository

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.map
import tv.lumo.android.network.generated.api.AccountApi
import tv.lumo.android.network.generated.api.AuthApi
import tv.lumo.android.network.generated.model.Device
import tv.lumo.android.network.generated.model.Entitlement
import tv.lumo.android.network.generated.model.RefreshRequest
import tv.lumo.android.network.generated.model.User

/**
 * The account: who is signed in, what their plan allows, which devices hold a
 * session, and signing out.
 *
 * <h2>Entitlements are read, never computed</h2>
 *
 * `GET /me/entitlement` is the only thing that decides what the plan allows
 * (ADR 0003). Nothing on the device asks a store whether the user is premium, and
 * nothing here carries a copy of "FREE means one source" — the day the free plan
 * allows two, every screen follows without a release, because the ceilings arrive
 * in [Entitlement.maxSources] and [Entitlement.maxDevices].
 *
 * A client-side copy of those numbers would be an access right computed on the
 * device, which is the thing the architecture forbids by name.
 *
 * <h2>Signing out succeeds even when the network does not</h2>
 *
 * The server is told first, so the session is revoked and the device seat freed.
 * But the local session is dropped either way: a user who taps "sign out" on a
 * train and stays signed in has been told a lie by the application, and the
 * refresh token they are still holding is the one thing that made the tap
 * urgent.
 */
@Singleton
class AccountRepository @Inject internal constructor(
    private val account: AccountApi,
    private val auth: AuthApi,
    private val calls: ApiCaller,
    private val session: SessionManager,
) {

    /** Emits false the moment the session is dropped, wherever that happened. */
    val isSignedIn: Flow<Boolean> = session.isSignedIn

    suspend fun me(): LumoResult<User> = calls.call { account.getCurrentUser() }

    suspend fun entitlement(): LumoResult<Entitlement> =
        calls.call { account.getEntitlement() }

    suspend fun devices(): LumoResult<List<Device>> =
        calls.call { account.listDevices() }.map { it.items }

    /**
     * Ends another device's session, freeing its seat on the plan.
     *
     * Revoking the current device is a sign-out with extra steps; the screen that
     * offers this list marks which entry is `is_current` precisely so a user does
     * not do that by accident.
     */
    suspend fun revokeDevice(id: String): LumoResult<Unit> =
        calls.empty { account.revokeDevice(UUID.fromString(id)) }

    /**
     * @return the outcome of telling the server. The local session is already
     * gone whatever it says — a caller that navigates on success only would
     * strand an offline user on a signed-out account.
     */
    suspend fun signOut(): LumoResult<Unit> {
        // The refresh token is what identifies the session to revoke, so it has
        // to be read before the local session is dropped.
        val refreshToken = session.current()?.refreshToken

        val told = if (refreshToken == null) {
            // Nothing to revoke — already signed out, or a session that could
            // not be read. Not a failure to report.
            LumoResult.Success(Unit)
        } else {
            calls.empty { auth.logout(RefreshRequest(refreshToken = refreshToken)) }
        }

        session.signOut()
        return told
    }
}
