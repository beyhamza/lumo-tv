package tv.lumo.androidtv

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import tv.lumo.android.core.common.locale.InterfaceLanguage
import tv.lumo.android.core.data.AppStart
import tv.lumo.android.core.data.AppStartDecision
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


    /** The interface language chosen in Settings, below Android 13 (S13-04). From 13 the platform applies it. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(InterfaceLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val startState by appStart.stream.collectAsStateWithLifecycle(AppStart.Loading)

            LumoTvTheme {
                LumoTvApp(startState = startState)
            }
        }
    }
}
