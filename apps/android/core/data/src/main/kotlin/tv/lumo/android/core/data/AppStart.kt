package tv.lumo.android.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.data.repository.SourceRepository

/**
 * Where the application should open.
 *
 * Deliberately not a route. Routes belong to feature modules, features may not be
 * referenced from `core:`, and the two applications are entitled to answer the
 * same question with different screens — the television's answer to
 * [SignedOut] is a device-code activation, because typing an email and a password
 * on a remote control is the first place people give up (US-05). So this names
 * the *situation*, and each application maps it to a destination it owns.
 */
sealed interface AppStart {

    /**
     * The session has not been read yet.
     *
     * A real state and not a placeholder: the session lives in an encrypted
     * DataStore, reading it is asynchronous, and the alternative is to assume
     * "signed out" for one frame — which shows the onboarding screen to a signed
     * in user every single launch.
     */
    data object Loading : AppStart

    /** No session. */
    data object SignedOut : AppStart

    /** Signed in, and the account has no source: there is nothing to watch yet. */
    data object NeedsSource : AppStart

    /** Signed in, with at least one source. Open on the catalogue. */
    data object Ready : AppStart
}

/**
 * Decides [AppStart], once, for both applications.
 *
 * <h2>Why a flow and not a suspend function</h2>
 *
 * Signing out has to move the user, and the only thing that knows a session has
 * gone is the session itself — a token reuse detected server-side revokes the
 * device chain, and `SessionManager` drops the session without any screen asking
 * it to. A one-shot read at launch would leave that user sitting on a catalogue
 * that answers 401 to everything.
 *
 * <h2>The `distinctUntilChanged` is load-bearing</h2>
 *
 * `SessionManager.isSignedIn` maps over the stored session, so it re-emits `true`
 * every time the session is *written* — which includes every token rotation, and
 * those happen while the application is being used. Without the guard, each
 * refresh would re-run the source lookup and re-emit a start state, and an
 * application that recreates its graph on a start state change (both of them do)
 * would throw the user back to the first screen every hour.
 */
@Singleton
class AppStartDecision @Inject internal constructor(
    private val session: SessionManager,
    private val sources: SourceRepository,
) {

    val stream: Flow<AppStart> = session.isSignedIn
        .distinctUntilChanged()
        .map { signedIn -> if (signedIn) whereSignedIn() else AppStart.SignedOut }
        .onStart { emit(AppStart.Loading) }
        .distinctUntilChanged()

    /**
     * A signed-in account with no source has nothing to show, so it goes to the
     * form that gives it one (US-06, US-07).
     *
     * **A failed lookup answers [AppStart.Ready], not [AppStart.NeedsSource].**
     * The two failures are not symmetric: sending a user who has sources to "add
     * a source" because the network blinked is a wrong screen and an alarming
     * one, while sending a user who has none to a catalogue shows an empty state
     * that tells them what to do next. When the answer is unknown, the mistake
     * that costs less is the one to make — and the catalogue is offline-first, so
     * it may well have something to show anyway.
     */
    private suspend fun whereSignedIn(): AppStart = when (val result = sources.sources()) {
        is LumoResult.Success -> if (result.value.isEmpty()) AppStart.NeedsSource else AppStart.Ready
        is LumoResult.Failure -> AppStart.Ready
    }
}
