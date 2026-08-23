package tv.lumo.androidtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import tv.lumo.android.core.designsystem.theme.LumoTvTheme
import tv.lumo.androidtv.ui.LumoTvApp

/**
 * The single Activity of the television application.
 *
 * No `enableEdgeToEdge()` here, unlike the phone: a television has no system
 * bars to draw behind, and the margin that matters is overscan — handled inside
 * Compose by `Modifier.tvOverscan()`, because it is a fraction of the panel
 * rather than a system inset.
 */
@AndroidEntryPoint
class TvActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LumoTvTheme {
                LumoTvApp()
            }
        }
    }
}
