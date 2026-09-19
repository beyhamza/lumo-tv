package tv.lumo.android.core.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.lumo.android.core.auth.SessionManager
import tv.lumo.android.core.data.ActiveSourceResolver
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.internal.ActiveSourceStore
import tv.lumo.android.core.data.knownSources
import tv.lumo.android.network.generated.model.ErrorCode

/**
 * The source this device browses, for the account that is signed in (US-018).
 *
 * <h2>Why every catalogue screen reads this instead of the list</h2>
 *
 * Until this existed, four view models each took "the first source the API
 * returns". With one source that is the right answer by accident; with two it is
 * a choice nobody made, made four times, and free to differ between two screens
 * the day the server orders its list differently. One singleton means one answer,
 * and a change of source reaches every open screen through [state] at once.
 *
 * <h2>An interface, for the screens' sake</h2>
 *
 * The features depend on this and not on the implementation, so a view model can
 * be driven by a fake that moves from one state to another — which is the whole
 * behaviour worth testing in them — without a session, a DataStore or a server.
 */
interface ActiveSourceRepository {

    /**
     * What this device browses right now.
     *
     * A `StateFlow`: a screen opening mid-session gets the current answer
     * immediately rather than a loading frame it has no reason to show.
     */
    val state: StateFlow<ActiveSourceState>

    /**
     * Browses another source, from now on and on this device only.
     *
     * Applied at once and without confirmation (US-018). An identifier the known
     * list does not hold — a source added a moment ago — is checked against a
     * fresh list first, so a wrong one can neither stick nor cost the choice that
     * was there.
     */
    suspend fun select(sourceId: String)

    /**
     * Reads `GET /sources` again and re-decides.
     *
     * The call to make after adding or deleting a source, and when coming back to
     * the foreground. It is what makes the first source an account adds active on
     * this device, and what stops an additional one from stealing the selection
     * (US-024): both fall out of the resolver's rule, given a recorded choice.
     *
     * A failed fetch changes nothing. See [ActiveSourceResolver.withoutList].
     */
    suspend fun refresh()

    /**
     * The server answered `404 SOURCE_NOT_FOUND` for this source.
     *
     * The second of the two proofs of deletion (c4-previous-catalogue.md §P6),
     * and the one that reaches a device whose source was deleted from another:
     * one source left is selected, several ask, none sends to "add a source".
     */
    suspend fun onSourceGone(sourceId: String)
}

/**
 * Routes a failed call that named [sourceId] to [ActiveSourceRepository.onSourceGone],
 * when — and only when — it is a proof of deletion.
 *
 * `SOURCE_NOT_FOUND` and nothing else. Not [LumoError.Offline], not a `5xx`
 * arriving as `INTERNAL_ERROR`, not an unreadable body: US-018 is explicit that
 * an unavailable network proves nothing, and treating a timeout as a deletion
 * would take the choice away from somebody whose Wi-Fi blinked.
 *
 * Here rather than in each view model, so that the three catalogues cannot come
 * to disagree about what counts as proof.
 *
 * @return true when the failure was a deletion and has been handled.
 */
suspend fun ActiveSourceRepository.onFailureNaming(sourceId: String, error: LumoError): Boolean {
    val gone = error is LumoError.Api && error.code == ErrorCode.SOURCE_NOT_FOUND
    if (gone) onSourceGone(sourceId)
    return gone
}

/**
 * [ActiveSourceRepository] over the session, the source list and the device's
 * own store.
 *
 * <h2>The account is read, never remembered</h2>
 *
 * Every operation asks the session who is signed in at the moment it runs. A
 * field holding "the current account" would be one more thing to keep in step
 * with a session that can disappear without any screen asking — a token reuse
 * detected server-side drops it — and the failure mode is writing one person's
 * choice under another person's key.
 *
 * <h2>One operation at a time</h2>
 *
 * [mutex] serialises them. A `select` racing a `refresh` would otherwise be able
 * to publish the refresh's older answer over the newer choice; the network call
 * sits inside the lock for that reason, and it is one short request.
 */
internal class DefaultActiveSourceRepository(
    private val session: SessionManager,
    private val sources: SourceRepository,
    private val store: ActiveSourceStore,
    scope: CoroutineScope,
) : ActiveSourceRepository {

    private val _state = MutableStateFlow<ActiveSourceState>(ActiveSourceState.Loading)
    override val state: StateFlow<ActiveSourceState> = _state.asStateFlow()

    private val mutex = Mutex()

    /** The account [_state] was resolved for. Guarded by [mutex]. */
    private var resolvedFor: String? = null

    init {
        scope.launch {
            session.sessions
                .map { it?.userId }
                // A token rotation rewrites the session roughly hourly and must
                // not re-decide anything — the guard `AppStartDecision` needs,
                // for the same reason.
                .distinctUntilChanged()
                .collectLatest { refresh() }
        }
    }

    override suspend fun refresh() = mutex.withLock {
        val account = currentAccount() ?: return@withLock
        val stored = store.read(account)

        when (val result = sources.sources()) {
            is LumoResult.Success -> publish(account, stored, ActiveSourceResolver.resolve(result.value, stored))
            is LumoResult.Failure ->
                _state.value = ActiveSourceResolver.withoutList(previousFor(account), stored)
        }
        resolvedFor = account
    }

    override suspend fun select(sourceId: String) = mutex.withLock {
        val account = currentAccount() ?: return@withLock
        val stored = store.read(account)

        val known = previousFor(account).knownSources
        // Not in the list this device knows: it was added since, or it does not
        // exist. A fresh list settles which — and the recorded choice is only
        // replaced once the new one is known to be real, so a wrong identifier
        // cannot cost somebody the choice they had.
        val all = if (known.any { it.id.toString() == sourceId }) {
            known
        } else {
            (sources.sources() as? LumoResult.Success)?.value ?: return@withLock
        }

        val wanted = sourceId.takeIf { id -> all.any { it.id.toString() == id } } ?: stored
        publish(account, stored, ActiveSourceResolver.resolve(all, wanted))
        resolvedFor = account
    }

    override suspend fun onSourceGone(sourceId: String) = mutex.withLock {
        val account = currentAccount() ?: return@withLock

        // The `404` is the proof, so the recorded choice goes first and whatever
        // the list says: nothing below may bring a deleted source back.
        val stored = store.read(account).takeUnless { it == sourceId }
        if (stored == null) store.clear(account)

        val known = when (val result = sources.sources()) {
            // The list is the better witness of what is left, and the `404` of
            // what is gone: a list that still carries the source a moment after
            // its deletion is filtered by the resolver all the same.
            is LumoResult.Success -> result.value
            is LumoResult.Failure -> previousFor(account).knownSources.ifEmpty { null }
        }

        if (known == null) {
            // Browsing from a bare identifier, offline, and the list is still out
            // of reach: what is left cannot be said, and `None` would be a claim
            // nobody verified.
            _state.value = ActiveSourceState.Unavailable
        } else {
            publish(account, stored, ActiveSourceResolver.withoutSource(known, stored, sourceId))
        }
        resolvedFor = account
    }

    /**
     * Publishes a decision and brings the store in line with it.
     *
     * Written only on a difference, so an ordinary refresh costs no disk write.
     * Cleared when the decision is not a selection **and** came from proof — a
     * successful list or a `404` — which is the only way this is reached:
     * [ActiveSourceResolver.withoutList] never comes through here.
     */
    private suspend fun publish(account: String, stored: String?, next: ActiveSourceState) {
        when (next) {
            is ActiveSourceState.Selected -> if (next.sourceId != stored) store.write(account, next.sourceId)
            is ActiveSourceState.None, is ActiveSourceState.NeedsChoice ->
                if (stored != null) store.clear(account)
            else -> Unit
        }
        _state.value = next
    }

    /**
     * Who is signed in, or null — after saying so.
     *
     * Signed out is [ActiveSourceState.Loading] and not [ActiveSourceState.None]:
     * nothing is known about an account that is not there, and `None` would tell
     * a shell still on screen for a frame to open "add a source".
     */
    private suspend fun currentAccount(): String? {
        val account = session.current()?.userId
        if (account == null) {
            _state.value = ActiveSourceState.Loading
            resolvedFor = null
        }
        return account
    }

    /** What is on display, unless it belongs to somebody else. */
    private fun previousFor(account: String): ActiveSourceState =
        if (resolvedFor == account) _state.value else ActiveSourceState.Loading
}
