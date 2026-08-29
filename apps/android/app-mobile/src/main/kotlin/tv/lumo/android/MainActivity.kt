package tv.lumo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import tv.lumo.android.core.data.AppStart
import tv.lumo.android.core.data.AppStartDecision
import tv.lumo.android.core.data.CatalogueSections
import tv.lumo.android.core.designsystem.theme.LumoMobileTheme
import tv.lumo.android.ui.LumoMobileApp

/**
 * The single Activity of the phone application.
 *
 * One Activity, one Compose tree, navigation inside it. A second Activity would
 * mean a second place where the theme, the back stack and the player surface all
 * have to be got right.
 *
 * The start state is collected here rather than in a ViewModel because there is
 * nothing to hold: [AppStartDecision] is a singleton exposing a flow, the shell
 * below is a pure function of it, and a ViewModel whose only job is to forward a
 * flow is a class that has to be written twice — once here and once on the
 * television — for no behaviour at all.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appStart: AppStartDecision

    /**
     * Whether to offer a films tab (US-13).
     *
     * Collected here beside the start state and for the same reason: it is a
     * singleton exposing a flow, the shell below is a pure function of it, and a
     * ViewModel whose only job is to forward a flow would have to be written
     * twice — once here and once on the television.
     */
    @Inject
    lateinit var sections: CatalogueSections

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge before setContent: video wants the whole panel, and
        // handling the insets in Compose is what lets a full-screen player and a
        // padded list live in the same Activity.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val startState by appStart.stream.collectAsStateWithLifecycle(AppStart.Loading)
            // False until something says otherwise: a tab that appears is a
            // better first frame than one that disappears.
            val hasFilms by sections.hasFilms.collectAsStateWithLifecycle(false)

            LumoMobileTheme {
                LumoMobileApp(startState = startState, hasFilms = hasFilms)
            }
        }
    }
}
