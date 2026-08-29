package tv.lumo.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import tv.lumo.android.core.designsystem.image.lumoImageLoader

/**
 * `tv.lumo.android` — the phone and tablet application (ADR 0004).
 *
 * Still empty of behaviour: whatever an application class would do — start a
 * sync, warm a cache, register a listener — is behaviour both applications need,
 * and behaviour both applications need lives in a `core:` module.
 *
 * The single override is the exception that proves it. Coil's singleton is
 * reached by a framework callback on `Application` and nowhere else, so the hook
 * has to sit here; what it returns is the loader both applications share, defined
 * once in `core:designsystem` (US-13, S5-07).
 */
@HiltAndroidApp
class LumoApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        lumoImageLoader(this)
}
