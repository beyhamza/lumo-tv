package tv.lumo.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * `tv.lumo.android` — the phone and tablet application (ADR 0004).
 *
 * There is nothing in here, and there should never be: whatever an application
 * class would do — start a sync, warm a cache, register a listener — is
 * behaviour both applications need, and behaviour both applications need lives
 * in a `core:` module.
 */
@HiltAndroidApp
class LumoApplication : Application()
