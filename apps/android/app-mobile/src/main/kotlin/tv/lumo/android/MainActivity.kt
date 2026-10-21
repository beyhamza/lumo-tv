package tv.lumo.android

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import tv.lumo.android.core.cast.CastButtonContent
import tv.lumo.android.core.common.locale.InterfaceLanguage
import tv.lumo.android.core.data.AppStart
import tv.lumo.android.core.data.AppStartDecision
import tv.lumo.android.core.designsystem.theme.LumoMobileTheme
import tv.lumo.android.core.player.cast.RemotePlayback
import tv.lumo.android.core.player.ui.LocalCastButton
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
 *
 * A `FragmentActivity` since S16: the Cast device picker is a DialogFragment.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appStart: AppStartDecision

    /**
     * Injected for its side effect: the Cast framework starts with the Activity,
     * so devices are already known when a player opens and the button can appear
     * at once (S16-01). Nothing here calls it.
     */
    @Inject
    lateinit var remotePlayback: RemotePlayback


    /** The interface language chosen in Settings, below Android 13 (S13-04). From 13 the platform applies it. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(InterfaceLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge before setContent: video wants the whole panel, and
        // handling the insets in Compose is what lets a full-screen player and a
        // padded list live in the same Activity.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val startState by appStart.stream.collectAsStateWithLifecycle(AppStart.Loading)

            LumoMobileTheme {
                // The players draw the Cast button through this; the television provides none.
                CompositionLocalProvider(LocalCastButton provides CastButtonContent) {
                    LumoMobileApp(startState = startState)
                }
            }
        }
    }
}
