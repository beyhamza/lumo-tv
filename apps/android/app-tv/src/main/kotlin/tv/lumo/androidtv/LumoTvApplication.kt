package tv.lumo.androidtv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * `tv.lumo.androidtv` — the Android TV application (ADR 0004).
 *
 * Empty, like its phone counterpart, and for the same reason: anything an
 * application class would set up is shared behaviour, and shared behaviour lives
 * in `core:`.
 */
@HiltAndroidApp
class LumoTvApplication : Application()
