package tv.lumo.android.core.data.internal

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import tv.lumo.android.network.generated.model.DeviceRegistration
import tv.lumo.android.network.generated.model.Platform

/**
 * What this installation calls itself when it asks for a session.
 *
 * Every operation that opens a session carries one of these, so the account
 * screen can list "Pixel 8 · signed in yesterday" and revoke the right one — and
 * so the plan's device ceiling counts installations rather than sign-ins.
 *
 * An interface for the same reason [tv.lumo.android.core.auth.store.SessionStore]
 * is one: what deserves a test is the repository around it, and reading
 * `android.os.Build` is not something a JVM test can do.
 */
internal interface DeviceDescriber {
    fun registration(): DeviceRegistration
}

/**
 * The real one.
 *
 * <h2>Nothing here identifies a person</h2>
 *
 * The manufacturer, the model and the application's version number. No advertising
 * id, no serial, nothing that survives a reinstall or follows the user to another
 * application. The device id that matters is the one the **server** issues in
 * return, which is scoped to the account and revocable from it.
 *
 * <h2>The platform comes from the application module</h2>
 *
 * It is the one fact `core:` cannot know: the same code builds `tv.lumo.android`
 * and `tv.lumo.androidtv` (ADR 0004), and each of them is entitled to say which it
 * is. Detecting it here — a leanback system feature, a screen size — would be
 * inferring at runtime something the build already knows, and it would answer
 * wrongly in exactly the case the AGENTS.md mentions: `app-tv` running on a phone
 * emulator, which is a supported way to check that it starts.
 */
@Singleton
internal class AndroidDeviceDescriber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val platform: Platform,
) : DeviceDescriber {

    override fun registration(): DeviceRegistration = DeviceRegistration(
        platform = platform,
        // What a person recognises in a list of their own devices.
        name = Build.MODEL,
        model = "${Build.MANUFACTURER} ${Build.MODEL}",
        appVersion = appVersion(),
    )

    /**
     * Read from the package manager rather than from `BuildConfig`.
     *
     * `BuildConfig.VERSION_NAME` belongs to whichever module declares it, and
     * this one is shared by two applications with two version numbers. The
     * package manager answers for the application that is actually running, which
     * is the number a support conversation needs.
     */
    private fun appVersion(): String? = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (missing: PackageManager.NameNotFoundException) {
        // Cannot happen for one's own package, and is not worth a crash if it
        // somehow does: a session without a version number is still a session.
        null
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DeviceModule {

    @Binds
    @Singleton
    abstract fun deviceDescriber(impl: AndroidDeviceDescriber): DeviceDescriber
}
