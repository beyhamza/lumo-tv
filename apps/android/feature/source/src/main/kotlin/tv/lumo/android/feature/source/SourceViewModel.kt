package tv.lumo.android.feature.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.SyncOutcome
import tv.lumo.android.core.data.asSyncOutcome
import tv.lumo.android.core.data.repository.AccountRepository
import tv.lumo.android.core.data.repository.ActiveSourceRepository
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.core.data.repository.onFailureNaming
import tv.lumo.android.core.data.selectedSourceId
import tv.lumo.android.core.data.valueOrNull
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * Registering a source, and then watching what becomes of it (US-06, US-07).
 *
 * <h2>Nothing here re-implements the server's rules</h2>
 *
 * Which fields a kind needs, what a valid host looks like, whether a playlist is
 * a playlist — all of it belongs to the server, is checked there, and comes back
 * per field or as a code. A copy on the device would be one more place to forget
 * the day the contract gains a kind, and it would be the copy that is wrong.
 *
 * In particular the Xtream **host is not validated and not cleaned up here**. The
 * contract normalises it server-side, with or without a scheme, with or without a
 * port, with or without a trailing slash, and asks clients to tolerate rather than
 * reject (US-06). A regex here would refuse addresses the server accepts, and the
 * user would have no way of knowing which of us was wrong.
 *
 * <h2>Two error surfaces, and this screen owns both ends of them</h2>
 *
 * The contract splits them deliberately. Reachability and credentials are checked
 * **before** the server answers, so a refusal arrives as a `422` while the user is
 * still looking at the form. Catalogue parsing happens afterwards and its failures
 * are not HTTP errors at all: the source moves to `ERROR` and carries the reason,
 * which is what [watch] reads.
 *
 * <h2>Why it polls</h2>
 *
 * Because the contract says to: `202 PENDING`, then `GET /sources/{id}` until
 * `READY` or `ERROR`, which are the only terminal states. There is no push
 * channel, and the interval lives here rather than in the repository — a
 * repository that owned a timer would be a repository nobody could test without
 * one.
 *
 * <h2>A flow that is entered, not a tab that follows</h2>
 *
 * Until "My sources" existed (US-024) this view model *was* the source screen: it
 * followed the active source and showed the form when there was none. The list is
 * now `MySourcesViewModel`'s, and this is what it opens — to add a source
 * ([startAdding]) or to correct one whose import failed ([startFixing]) — and
 * what [close] leaves. It starts [AddSourceStep.Idle] and shows nothing until
 * asked.
 *
 * <h2>After adding (US-024, "Après ajout")</h2>
 *
 * This screen is what tells [ActiveSourceRepository] that the list has changed,
 * and the repository's rule does the rest: the account's **first** source becomes
 * active on this device, an **additional** one leaves the selection alone. What
 * the screen then proposes follows from that and from nothing else —
 * [AddSourceState.watchedIsActive]: *Discover my catalogue* for the source being
 * browsed, *Use this source* for one that is not. The import can be left at any
 * moment; a source that failed is kept, with its error and its way out, and is
 * never a reason to fill the form again.
 */
@HiltViewModel
class SourceViewModel @Inject constructor(
    private val sources: SourceRepository,
    private val account: AccountRepository,
    private val activeSource: ActiveSourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddSourceState())
    val state: StateFlow<AddSourceState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        // Which source this device browses, and nothing else about it: it decides
        // what is proposed once the source being watched here is ready.
        viewModelScope.launch {
            activeSource.state
                .map { it.selectedSourceId }
                .distinctUntilChanged()
                .collect { activeId -> _state.update { it.copy(activeSourceId = activeId) } }
        }
    }

    /**
     * Opens the form, or the sentence that says the plan is full.
     *
     * @param knownCount how many sources the account has, when the list could be
     * read. Null — the list failed — never closes the form: an unknown count is
     * not a full plan, and the server is the one that refuses.
     */
    fun startAdding(knownCount: Int?) {
        pollJob?.cancel()
        _state.update { AddSourceState(step = AddSourceStep.Loading, activeSourceId = it.activeSourceId) }
        viewModelScope.launch { offerForm(knownCount) }
    }

    /**
     * Opens the correction form for a source whose import failed, from the list.
     *
     * The source is kept, with its error (US-024): what was right is still in the
     * form, and the `PATCH` restarts the import by itself.
     */
    fun startFixing(source: Source) {
        pollJob?.cancel()
        _state.update {
            AddSourceState(
                step = AddSourceStep.Watching(source.id.toString()),
                source = source,
                view = viewOf(source),
                activeSourceId = it.activeSourceId,
            )
        }
        fixInput()
    }

    /**
     * Leaves the flow, back to the list.
     *
     * Allowed at any moment, **an import in progress included** (US-024): the
     * source goes on importing on the server, the list shows its real step, and
     * nothing here needs to stay open for that. The password goes with the state.
     */
    fun close() {
        pollJob?.cancel()
        _state.update { AddSourceState(step = AddSourceStep.Idle, activeSourceId = it.activeSourceId) }
    }

    /** *Use this source*, for a source added beside the one being browsed. */
    fun useWatched() {
        val sourceId = (_state.value.step as? AddSourceStep.Watching)?.sourceId ?: return
        viewModelScope.launch { activeSource.select(sourceId) }
    }

    /**
     * The form, or the sentence that says the plan is full.
     *
     * The plan's ceiling is read from `Entitlement.max_sources` and never from a
     * constant — the day the free plan allows two, this follows without a release
     * (ADR 0003). The server refuses anyway; this is the courtesy, and the
     * contract says as much.
     *
     * @param used how many sources the account has, or null when the list could
     * not be read. An unknown count never closes the form: the server is the one
     * that refuses.
     */
    private suspend fun offerForm(used: Int?) {
        val max = account.entitlement().valueOrNull()?.maxSources

        _state.update {
            // Closed while the entitlement was being read: stay closed.
            if (it.step != AddSourceStep.Loading) return@update it

            // A null maximum means unlimited in the contract, not unknown.
            if (max != null && used != null && used >= max) {
                it.copy(step = AddSourceStep.NoRoom(max))
            } else {
                it.copy(step = AddSourceStep.ChoosingKind)
            }
        }
    }

    // ---- the form ----------------------------------------------------------

    fun onKindChosen(kind: SourceKind) =
        _state.update { it.copy(step = AddSourceStep.Filling(kind), failure = null) }

    /** Back to the two cards, keeping nothing: the fields differ per kind. */
    fun onBack() = _state.update {
        AddSourceState(step = AddSourceStep.ChoosingKind, activeSourceId = it.activeSourceId)
    }

    fun onLabelChange(value: String) = _state.update { it.copy(label = value, failure = null) }

    fun onHostChange(value: String) = _state.update { it.copy(host = value, failure = null) }

    fun onUsernameChange(value: String) =
        _state.update { it.copy(username = value, failure = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, failure = null) }

    fun onPlaylistUrlChange(value: String) =
        _state.update { it.copy(playlistUrl = value, failure = null) }

    fun onEpgUrlChange(value: String) = _state.update { it.copy(epgUrl = value, failure = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        when (val step = current.step) {
            is AddSourceStep.Filling -> create(current, step.kind)
            is AddSourceStep.Fixing -> correct(current, step)
            else -> Unit
        }
    }

    private fun create(current: AddSourceState, kind: SourceKind) {
        _state.update { it.copy(submitting = true, failure = null) }

        viewModelScope.launch {
            val result = when (kind) {
                SourceKind.XTREAM -> sources.create(
                    label = current.label,
                    kind = kind,
                    host = current.host,
                    username = current.username,
                    password = current.password,
                    epgUrl = current.epgUrl.ifBlank { null },
                )

                else -> sources.create(
                    label = current.label,
                    kind = SourceKind.M3U_URL,
                    m3uUrl = current.playlistUrl,
                    epgUrl = current.epgUrl.ifBlank { null },
                )
            }

            accept(result)
        }
    }

    /**
     * Sends only the field that was wrong.
     *
     * Any ingestion-affecting property in a `PATCH` moves the source back to
     * `PENDING` and starts a fresh import, by contract — which is exactly what is
     * wanted here and exactly why nothing else is sent. A stray host in a
     * password correction would be a re-ingestion nobody asked for.
     */
    private fun correct(current: AddSourceState, step: AddSourceStep.Fixing) {
        _state.update { it.copy(submitting = true, failure = null) }

        viewModelScope.launch {
            val result = when (step.kind) {
                SourceKind.XTREAM -> sources.update(
                    id = step.sourceId,
                    host = current.host,
                    username = current.username,
                    password = current.password,
                )

                else -> sources.update(id = step.sourceId, m3uUrl = current.playlistUrl)
            }

            accept(result)
        }
    }

    private fun accept(result: LumoResult<Source>) {
        when (result) {
            is LumoResult.Success -> {
                // Closed while the server was checking: the source exists all the
                // same, so the list is told — and the flow stays closed.
                if (_state.value.step == AddSourceStep.Idle) {
                    viewModelScope.launch { activeSource.refresh() }
                    return
                }

                // The password leaves this object the moment it is no longer
                // needed. It is never redisplayed — the API does not return it,
                // and no screen holds it any longer than the request did.
                _state.update { it.copy(password = "", submitting = false) }
                show(result.value)
                watch(result.value.id.toString())

                // The list has changed, and the repository is what decides what
                // that means: the account's first source becomes active on this
                // device, an additional one does not steal the selection (US-024).
                viewModelScope.launch { activeSource.refresh() }
            }

            is LumoResult.Failure -> _state.update {
                it.copy(submitting = false, failure = result.error.asFailure())
            }
        }
    }

    // ---- watching ----------------------------------------------------------

    private fun show(source: Source) = _state.update {
        it.copy(
            step = AddSourceStep.Watching(source.id.toString()),
            source = source,
            view = viewOf(source),
            failure = null,
        )
    }

    /**
     * Polls until the source reaches a terminal state.
     *
     * Stops on `READY` or `ERROR` — the only two the contract calls terminal —
     * and gives up after [MAX_CONSECUTIVE_FAILURES] answers it could not get. A
     * screen that polled a dead server forever would keep a radio awake in
     * someone's pocket to learn nothing.
     */
    private fun watch(sourceId: String) {
        pollJob?.cancel()

        pollJob = viewModelScope.launch {
            var failures = 0

            while (isActive) {
                delay(POLL_INTERVAL_MILLIS)

                when (val result = sources.source(sourceId)) {
                    is LumoResult.Success -> {
                        failures = 0
                        show(result.value)
                        val status = result.value.status
                        if (status == SourceStatus.READY || status == SourceStatus.ERROR) {
                            // The catalogue screens read the status from the
                            // repository. Without this they would go on saying
                            // "import in progress" about a source that is ready.
                            // Only when it moved: this poll also runs once over a
                            // source that was ready all along.
                            val known = (activeSource.state.value as? ActiveSourceState.Selected)
                                ?.source
                                ?.takeIf { it.id == result.value.id }
                            if (known?.status != status) activeSource.refresh()
                            return@launch
                        }
                    }

                    is LumoResult.Failure -> {
                        // Deleted from the website or another device while this
                        // screen watched it. That is not a poll that failed: the
                        // repository picks what is browsed next, and this screen
                        // follows it. Nothing else counts as proof (US-018).
                        if (activeSource.onFailureNaming(sourceId, result.error)) return@launch

                        failures++
                        if (failures >= MAX_CONSECUTIVE_FAILURES) {
                            // The last known state stays on screen. Replacing it
                            // with an error would throw away the only true thing
                            // this screen knows.
                            _state.update { it.copy(failure = result.error.asFailure()) }
                            return@launch
                        }
                    }
                }
            }
        }
    }

    /**
     * *Try again*, on a source that is kept with its error (US-024): the same
     * source, asked to import again — never a second one made from the form.
     *
     * The outcomes are the list's (`SyncOutcome`): already running is what was
     * asked for, a rate limit is a wait worded with the server's own delay, and a
     * source deleted elsewhere closes the flow onto whatever is left.
     */
    fun retry() {
        val sourceId = (_state.value.step as? AddSourceStep.Watching)?.sourceId ?: return

        viewModelScope.launch {
            when (val outcome = sources.sync(sourceId).asSyncOutcome()) {
                is SyncOutcome.Accepted -> {
                    show(outcome.source)
                    watch(sourceId)
                    activeSource.refresh()
                }

                SyncOutcome.AlreadyRunning -> watch(sourceId)

                is SyncOutcome.RateLimited -> _state.update {
                    it.copy(failure = AddSourceFailure.TooManyAttempts(outcome.retryAfterSeconds))
                }

                SyncOutcome.Gone -> {
                    close()
                    activeSource.onSourceGone(sourceId)
                }

                is SyncOutcome.Failed -> _state.update {
                    it.copy(failure = outcome.error.asFailure())
                }
            }
        }
    }

    /**
     * Back to the form with what was right still in it (US-06).
     *
     * The host and the username come from the source itself — the API returns
     * both. The password cannot be prefilled even in principle: it is write-only
     * in the contract and is returned to nobody, including its owner.
     */
    fun fixInput() {
        val current = _state.value
        val source = current.source ?: return

        _state.update {
            it.copy(
                step = AddSourceStep.Fixing(source.kind, source.id.toString()),
                label = source.label,
                host = source.host.orEmpty(),
                username = source.username.orEmpty(),
                password = "",
                playlistUrl = source.m3uUrl.orEmpty(),
                epgUrl = source.epgUrl.orEmpty(),
                failure = null,
            )
        }
    }
}

/** Where the user is in the flow. */
sealed interface AddSourceStep {

    /** The flow is not open: "My sources" shows its list. */
    data object Idle : AddSourceStep

    /** Asking what the plan allows. */
    data object Loading : AddSourceStep

    /** The plan is full. [max] comes from the server, never from a constant. */
    data class NoRoom(val max: Int) : AddSourceStep

    data object ChoosingKind : AddSourceStep

    data class Filling(val kind: SourceKind) : AddSourceStep

    /**
     * Correcting a source that failed to import.
     *
     * A separate step from [Filling] because it ends in a `PATCH` rather than a
     * `POST`, and because it starts with the fields the server already has.
     */
    data class Fixing(val kind: SourceKind, val sourceId: String) : AddSourceStep

    /** A source exists; what it is doing is in [AddSourceState.view]. */
    data class Watching(val sourceId: String) : AddSourceStep
}

data class AddSourceState(
    val step: AddSourceStep = AddSourceStep.Idle,
    val label: String = "",
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val playlistUrl: String = "",
    val epgUrl: String = "",
    val submitting: Boolean = false,
    val failure: AddSourceFailure? = null,
    /** The last answer from the server, kept for the correction form's prefill. */
    val source: Source? = null,
    /** What that answer looks like on screen. Null until there is a source. */
    val view: SourceView? = null,
    /** The source this device browses, as `ActiveSourceRepository` says it (US-018). */
    val activeSourceId: String? = null,
) {
    /**
     * Whether the source being watched here is the one this device browses.
     *
     * The whole of "after adding" (US-024) hangs on it. True for the account's
     * first source — the repository selects it without a question — and the
     * screen proposes *Discover my catalogue*. False for an additional one, which
     * does not steal the selection, and the screen proposes *Use this source*;
     * pressing that makes this true, and the proposal follows.
     */
    val watchedIsActive: Boolean
        get() = (step as? AddSourceStep.Watching)?.sourceId.let { it != null && it == activeSourceId }

    /**
     * Enough to be worth a round trip.
     *
     * The per-kind requirements are the server's — it answers `VALIDATION_FAILED`
     * with a pointer to the field — so this is the empty-form check and nothing
     * more.
     */
    val canSubmit: Boolean
        get() = when {
            submitting -> false
            else -> when (val current = step) {
                is AddSourceStep.Filling -> label.isNotBlank() && filledFor(current.kind)
                // The label is not part of a correction: it is already set, and
                // the form does not offer it.
                is AddSourceStep.Fixing -> filledFor(current.kind)
                else -> false
            }
        }

    private fun filledFor(kind: SourceKind): Boolean = when (kind) {
        SourceKind.XTREAM -> host.isNotBlank() && username.isNotBlank() && password.isNotEmpty()
        SourceKind.M3U_URL -> playlistUrl.isNotBlank()
        else -> false
    }
}

/**
 * What the form can say when the server refuses **synchronously**.
 *
 * The ingestion codes are named one by one rather than folded into "something
 * went wrong", and the contract is explicit that no client may do otherwise on
 * this surface: the user's next action is completely different between "the
 * server is down" and "your password is wrong".
 */
sealed interface AddSourceFailure {

    /** `SOURCE_UNREACHABLE` — the address did not answer. Worth retrying. */
    data object Unreachable : AddSourceFailure

    /** `SOURCE_AUTH_FAILED` — the panel refused the credentials. */
    data object CredentialsRefused : AddSourceFailure

    /** `SOURCE_INVALID_FORMAT` — something answered, but not a playlist. */
    data object NotAPlaylist : AddSourceFailure

    /** `SOURCE_EMPTY` — a playlist with no channel in it. */
    data object Empty : AddSourceFailure

    /** `SOURCE_TOO_LARGE` — past the size the server will read. */
    data object TooLarge : AddSourceFailure

    /**
     * `SOURCE_MAX_CONNECTIONS` — the **user's own** subscription is already
     * streaming as much as it allows, so validation could not open one more.
     *
     * Their provider's rule, not ours, and the message has to say so: a limit
     * presented without an owner reads as Lumo refusing. Worth trying again once
     * something else stops playing, which is why it is not folded into the
     * unreachable case.
     */
    data object TooManyStreams : AddSourceFailure

    /** `SOURCE_EXPIRED` — the user's own subscription ran out. */
    data object SubscriptionExpired : AddSourceFailure

    /** `SOURCE_LIMIT_REACHED` — the backstop behind [AddSourceStep.NoRoom]. */
    data object NoRoomLeft : AddSourceFailure

    /** `VALIDATION_FAILED` — a field the server would not take. */
    data object Invalid : AddSourceFailure

    data class TooManyAttempts(val seconds: Int?) : AddSourceFailure

    data object Offline : AddSourceFailure

    data object Unexpected : AddSourceFailure
}

/**
 * The contract's codes, mapped to what this screen says.
 *
 * `internal` rather than private so a test can walk every `IngestionErrorCode`
 * and prove none of them falls through to "something went wrong". That is not
 * tidiness: the contract states that no client may use a generic message on this
 * surface, because the user's next action differs completely between "the server
 * is down" and "your password is wrong".
 */
internal fun LumoError.asFailure(): AddSourceFailure = when (this) {
    is LumoError.Offline -> AddSourceFailure.Offline
    is LumoError.Api -> when (code) {
        ErrorCode.SOURCE_UNREACHABLE -> AddSourceFailure.Unreachable
        ErrorCode.SOURCE_AUTH_FAILED -> AddSourceFailure.CredentialsRefused
        ErrorCode.SOURCE_INVALID_FORMAT -> AddSourceFailure.NotAPlaylist
        ErrorCode.SOURCE_EMPTY -> AddSourceFailure.Empty
        ErrorCode.SOURCE_TOO_LARGE -> AddSourceFailure.TooLarge
        ErrorCode.SOURCE_MAX_CONNECTIONS -> AddSourceFailure.TooManyStreams
        ErrorCode.SOURCE_EXPIRED -> AddSourceFailure.SubscriptionExpired
        ErrorCode.SOURCE_LIMIT_REACHED -> AddSourceFailure.NoRoomLeft
        ErrorCode.VALIDATION_FAILED -> AddSourceFailure.Invalid
        ErrorCode.RATE_LIMITED -> AddSourceFailure.TooManyAttempts(retryAfterSeconds)
        else -> AddSourceFailure.Unexpected
    }
    is LumoError.UnknownCode, is LumoError.Unreadable -> AddSourceFailure.Unexpected
}

/** Often enough to feel live, rarely enough not to matter to a battery. */
private const val POLL_INTERVAL_MILLIS = 2_000L

/** Three unanswered polls is a server that is not coming back within this screen. */
private const val MAX_CONSECUTIVE_FAILURES = 3
