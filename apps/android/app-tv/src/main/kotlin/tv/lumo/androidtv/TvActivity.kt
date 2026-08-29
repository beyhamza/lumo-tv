package tv.lumo.androidtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import tv.lumo.android.core.data.AppStart
import tv.lumo.android.core.data.AppStartDecision
import tv.lumo.android.core.data.CatalogueSections
import tv.lumo.android.core.designsystem.theme.LumoTvTheme
import tv.lumo.androidtv.ui.LumoTvApp

/**
 * The single Activity of the television application.
 *
 * No `enableEdgeToEdge()` here, unlike the phone: a television has no system
 * bars to draw behind, and the margin that matters is overscan — handled inside
 * Compose by `Modifier.tvOverscan()`, because it is a fraction of the panel
 * rather than a system inset.
 *
 * The start state comes from the same singleton the phone reads, so "signed in
 * with a source" means the same thing on both, and only the destination it maps
 * to differs.
 */
@AndroidEntryPoint
class TvActivity : ComponentActivity() {

    @Inject
    lateinit var appStart: AppStartDecision

    /** Whether to offer a films entry in the rail (US-13). The phone reads the same one. */
    @Inject
    lateinit var sections: CatalogueSections

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val startState by appStart.stream.collectAsStateWithLifecycle(AppStart.Loading)
            // False until something says otherwise: a rail that gains an entry is
            // a better first frame than one that loses a stop under the D-pad.
            val hasFilms by sections.hasFilms.collectAsStateWithLifecycle(false)

            LumoTvTheme {
                LumoTvApp(startState = startState, hasFilms = hasFilms)
            }
        }
    }
}
