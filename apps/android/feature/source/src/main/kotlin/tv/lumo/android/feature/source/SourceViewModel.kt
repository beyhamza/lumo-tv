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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.repository.AccountRepository
import tv.lumo.android.core.data.repository.SourceRepository
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
 */
@HiltViewModel
class SourceViewModel @Inject constructor(
    private val sources: SourceRepository,
    private val account: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddSourceState())
    val state: StateFlow<AddSourceState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        load()
    }

    /**
     * Decides what this tab is showing: a source, or the form that makes one.
     *
     * The plan's ceiling is read from `Entitlement.max_sources` and never from a
     * constant — the day the free plan allows two, this follows without a release
     * (ADR 0003). The server refuses anyway; this is the courtesy, and the
     * contract says as much.
     */
    private fun load() {
        _state.update { it.copy(step = AddSourceStep.Loading) }

        viewModelScope.launch {
            val existing = sources.sources().valueOrNull()
            val first = existing?.firstOrNull()

            if (first != null) {
                show(first)
                watch(first.id.toString())
                return@launch
            }

            val max = account.entitlement().valueOrNull()?.maxSources
            val used = existing?.size

            _state.update {
                // A null maximum means unlimited in the contract, not unknown.
                if (max != null && used != null && used >= max) {
                    it.copy(step = AddSourceStep.NoRoom(max))
                } else {
                    it.copy(step = AddSourceStep.ChoosingKind)
                }
            }
        }
    }

    // ---- the form ----------------------------------------------------------

    fun onKindChosen(kind: SourceKind) =
        _state.update { it.copy(step = AddSourceStep.Filling(kind), failure = null) }

    /** Back to the two cards, keeping nothing: the fields differ per kind. */
    fun onBack() = _state.update { AddSourceState(step = AddSourceStep.ChoosingKind) }

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
                // The password leaves this object the moment it is no longer
                // needed. It is never redisplayed — the API does not return it,
                // and no screen holds it any longer than the request did.
                _state.update { it.copy(password = "", submitting = false) }
                show(result.value)
                watch(result.value.id.toString())
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
                        if (status == SourceStatus.READY || status == SourceStatus.ERROR) return@launch
                    }

                    is LumoResult.Failure -> {
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

    /** `SOURCE_UNREACHABLE`, and only that: ask the server to try the same thing again. */
    fun retry() {
        val sourceId = (_state.value.step as? AddSourceStep.Watching)?.sourceId ?: return

        viewModelScope.launch {
            // A 409 here means a synchronisation is already running, which is what
            // was asked for. Polling picks it up either way.
            sources.sync(sourceId)
            watch(sourceId)
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

    /** Asking what this account already has. */
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
    val step: AddSourceStep = AddSourceStep.Loading,
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
) {
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
