package tv.lumo.android.feature.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.repository.AccountRepository
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.core.data.valueOrNull
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.SourceKind

/**
 * Registering a source (US-06, US-07).
 *
 * <h2>Nothing here re-implements the server's rules</h2>
 *
 * Which fields a kind needs, what a valid host looks like, whether a playlist is
 * a playlist — all of it belongs to the server, is checked there, and comes back
 * per field or as a code. A copy on the device would be one more place to forget
 * the day the contract gains a kind, and it would be the copy that is wrong.
 *
 * In particular the Xtream **host is not validated and not cleaned up here**. The
 * contract says it is normalised server-side, with or without a scheme, with or
 * without a port, with or without a trailing slash, and asks clients to tolerate
 * rather than reject (US-06). A regex here would reject addresses the server
 * accepts, and the user would have no way to know which of us was wrong.
 *
 * What *is* checked before sending is the obviously empty form: a round trip to
 * be told what is already on screen is a round trip for nothing.
 *
 * <h2>Two error surfaces, and this screen owns the first</h2>
 *
 * The contract splits them deliberately. Reachability and credentials are checked
 * **before** the server answers, so a refusal arrives as a `422` while the user is
 * still looking at the form — which is what US-06 asks for, and why the form keeps
 * the host so only the wrong part has to be corrected. Catalogue parsing happens
 * afterwards and its failures are not HTTP errors at all: the source goes to
 * `ERROR` and carries the reason, which the status screen reads (`S2-09`).
 */
@HiltViewModel
class AddSourceViewModel @Inject constructor(
    private val sources: SourceRepository,
    private val account: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddSourceState())
    val state: StateFlow<AddSourceState> = _state.asStateFlow()

    init {
        checkRoom()
    }

    /**
     * Asks whether the plan has room before offering the form.
     *
     * The ceiling is read from `Entitlement.max_sources` and never from a
     * constant here: the day the free plan allows two, this screen follows
     * without a release (ADR 0003). The server refuses anyway — this is the
     * courtesy, not the enforcement, and the contract says as much.
     */
    private fun checkRoom() {
        _state.update { it.copy(step = AddSourceStep.Loading) }

        viewModelScope.launch {
            val entitlement = account.entitlement().valueOrNull()
            val existing = sources.sources().valueOrNull()

            // A null maximum means unlimited in the contract, not unknown.
            val max = entitlement?.maxSources
            val used = existing?.size

            _state.update {
                if (max != null && used != null && used >= max) {
                    it.copy(step = AddSourceStep.NoRoom(max))
                } else {
                    it.copy(step = AddSourceStep.ChoosingKind)
                }
            }
        }
    }

    fun onKindChosen(kind: SourceKind) =
        _state.update { it.copy(step = AddSourceStep.Filling(kind), failure = null) }

    /** Back to the two cards, keeping nothing: the fields differ per kind. */
    fun onBack() = _state.update {
        AddSourceState(step = AddSourceStep.ChoosingKind)
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
        val kind = (current.step as? AddSourceStep.Filling)?.kind ?: return
        if (!current.canSubmit) return

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

            when (result) {
                is LumoResult.Success -> _state.update {
                    // The password leaves this object the moment it is no longer
                    // needed. It is never redisplayed — not here, not on an edit
                    // screen, and the API does not return it either.
                    it.copy(
                        step = AddSourceStep.Registered(result.value.id.toString()),
                        password = "",
                        submitting = false,
                    )
                }

                is LumoResult.Failure -> _state.update {
                    it.copy(submitting = false, failure = result.error.asFailure())
                }
            }
        }
    }
}

/** Where the user is in the flow. */
sealed interface AddSourceStep {

    /** Asking the plan whether there is room. */
    data object Loading : AddSourceStep

    /** The plan is full. [max] comes from the server, never from a constant. */
    data class NoRoom(val max: Int) : AddSourceStep

    data object ChoosingKind : AddSourceStep

    data class Filling(val kind: SourceKind) : AddSourceStep

    /**
     * Accepted, and being imported.
     *
     * The server answered `202` with a `PENDING` source: reachability and
     * credentials passed, the catalogue is being read in the background. Showing
     * what that source becomes — the steps, the counts, the four failures — is
     * `S2-09`'s screen; this is the handoff.
     */
    data class Registered(val sourceId: String) : AddSourceStep
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
            label.isBlank() -> false
            else -> when ((step as? AddSourceStep.Filling)?.kind) {
                SourceKind.XTREAM ->
                    host.isNotBlank() && username.isNotBlank() && password.isNotEmpty()
                SourceKind.M3U_URL -> playlistUrl.isNotBlank()
                else -> false
            }
        }
}

/**
 * What the form can say when the server refuses.
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
