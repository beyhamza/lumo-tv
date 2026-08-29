package tv.lumo.androidtv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import tv.lumo.android.core.designsystem.image.lumoImageLoader

/**
 * `tv.lumo.androidtv` — the Android TV application (ADR 0004).
 *
 * Still empty of behaviour, and for the stated reason: anything an application
 * class would set up is shared behaviour, and shared behaviour lives in `core:`.
 *
 * The one thing it does is hand Coil its loader, and that is not an exception to
 * the rule — it is the rule applied. Coil's singleton is reached by a framework
 * callback on `Application` and nowhere else, so the *hook* has to be here; the
 * loader itself, with the cache ceilings a poster grid needs, is one function in
 * `core:designsystem` that both applications call (US-13, S5-07).
 */
@HiltAndroidApp
class LumoTvApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        lumoImageLoader(this)
}
