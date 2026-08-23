package tv.lumo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import tv.lumo.android.core.designsystem.theme.LumoMobileTheme
import tv.lumo.android.ui.LumoMobileApp

/**
 * The single Activity of the phone application.
 *
 * One Activity, one Compose tree, navigation inside it. A second Activity would
 * mean a second place where the theme, the back stack and the player surface all
 * have to be got right.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge before setContent: video wants the whole panel, and
        // handling the insets in Compose is what lets a full-screen player and a
        // padded list live in the same Activity.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            LumoMobileTheme {
                LumoMobileApp()
            }
        }
    }
}
