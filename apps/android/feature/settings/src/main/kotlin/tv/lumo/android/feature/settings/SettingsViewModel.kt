package tv.lumo.android.feature.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tv.lumo.android.core.auth.SessionManager

/**
 * The first real consumer of the dependency graph.
 *
 * Until this existed, `@HiltAndroidApp` sat on two Application classes and every
 * `@Module` in `core:` was written, compiled — and never asked for anything.
 * Dagger only validates the bindings reachable from an entry point, so a module
 * that could not satisfy its own dependencies would have compiled quietly and
 * failed the first time a screen needed it, which is to say in the first story
 * of the first sprint rather than here.
 *
 * It reads session state and nothing else, because that is all a placeholder can
 * honestly show. What it proves is the chain: Application → SingletonComponent →
 * AuthModule → encrypted DataStore → ViewModel → both surfaces. The same
 * instance backs the phone and the television; only the rendering differs
 * (AGENTS.md §2 — a duplicated piece of logic between app-mobile and app-tv is a
 * defect).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    sessionManager: SessionManager,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        sessionManager.isSignedIn
            .map(::SettingsUiState)
            .stateIn(
                scope = viewModelScope,
                // Survives a rotation and a brief trip to the background without
                // re-reading the encrypted store, and stops collecting when the
                // screen is really gone.
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SettingsUiState(signedIn = false),
            )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** What the settings surfaces render. Deliberately smaller than it will end up. */
data class SettingsUiState(val signedIn: Boolean)

/**
 * Maps the state to a string resource, once, for both surfaces.
 *
 * The mapping is here rather than in each screen for the same reason the view
 * model is shared: a phone and a television that disagree about what "signed in"
 * looks like is a bug waiting for a translator to find it.
 */
@StringRes
internal fun sessionLabelOf(state: SettingsUiState): Int =
    if (state.signedIn) R.string.feature_settings_session_open else R.string.feature_settings_session_none
