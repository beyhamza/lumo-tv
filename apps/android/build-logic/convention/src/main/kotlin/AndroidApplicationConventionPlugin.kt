import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import tv.lumo.buildlogic.configureKotlinAndroid
import tv.lumo.buildlogic.configureUnitTests
import tv.lumo.buildlogic.hasLumoEnv
import tv.lumo.buildlogic.intVersion
import tv.lumo.buildlogic.libraryOf
import tv.lumo.buildlogic.libs
import tv.lumo.buildlogic.lumoEnv

/**
 * Applied by `app-mobile` and `app-tv`, and by nothing else.
 *
 * Both applications get identical build behaviour — signing, shrinking,
 * versioning — because the only thing that legitimately differs between them is
 * the UI and the `applicationId` (ADR 0004).
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)

            defaultConfig {
                targetSdk = libs.intVersion("targetSdk")

                // One version stream for both listings. Two applicationIds do
                // not mean two release trains: a user with both installed should
                // never be told the phone app is newer than the TV app. Both
                // modules read the same two variables, so one upload run gives
                // both bundles the same numbers. The release machine sets them
                // (S16B-04); the defaults are for development only.
                versionCode = lumoVersionCode()
                versionName = lumoEnv("LUMO_VERSION_NAME", default = DEFAULT_VERSION_NAME)
            }

            // Release signing material never lives in the repository. Without a
            // keystore configured the release build falls back to the debug key
            // and is deliberately not publishable, which is better than a build
            // that cannot run at all on a machine that has no secrets.
            val keystorePath = lumoEnv("LUMO_KEYSTORE_PATH")
            if (keystorePath.isNotBlank()) {
                signingConfigs.create("release") {
                    storeFile = rootProject.file(keystorePath)
                    storePassword = lumoEnv("LUMO_KEYSTORE_PASSWORD")
                    keyAlias = lumoEnv("LUMO_KEY_ALIAS")
                    keyPassword = lumoEnv("LUMO_KEY_PASSWORD")
                }
            }

            buildTypes {
                debug {
                    applicationIdSuffix = ".debug"
                    versionNameSuffix = "-debug"
                }
                release {
                    val minify = lumoEnv("LUMO_MINIFY_RELEASE", default = "true").toBoolean()
                    isMinifyEnabled = minify
                    isShrinkResources = minify
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                    signingConfig = signingConfigs.findByName("release")
                        ?: signingConfigs.getByName("debug")
                }
            }

            packaging {
                resources {
                    // Duplicate licence files from transitive dependencies.
                    excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    excludes += "/META-INF/DEPENDENCIES"
                }
            }
        }

        configureUnitTests()

        dependencies {
            add("implementation", libs.libraryOf("androidx-core-ktx"))
            add("implementation", libs.libraryOf("kotlinx-coroutines-android"))
        }

        if (!hasLumoEnv("LUMO_KEYSTORE_PATH")) {
            logger.info(
                "No LUMO_KEYSTORE_PATH configured: release builds of ${project.path} " +
                    "will be signed with the debug key and cannot be published.",
            )
        }

        guardUploadBundle()
    }

    /**
     * Google Play refuses a second upload with a versionCode it has already
     * seen, and refuses a bundle signed with a debug key. The debug-key fallback
     * above keeps `assembleRelease` usable on a machine without secrets; a
     * release *bundle* exists only to be uploaded, so it is refused instead of
     * producing a file the Play Console rejects minutes later.
     *
     * Asked for by name on the command line, it fails during configuration,
     * before R8 spends minutes on two applications. Reached any other way, the
     * task itself fails before writing anything. Only strings are captured,
     * which keeps both configuration-cache safe.
     */
    private fun Project.guardUploadBundle() {
        val missing = buildList {
            if (!hasLumoEnv("LUMO_KEYSTORE_PATH")) add("LUMO_KEYSTORE_PATH")
            if (!hasLumoEnv("LUMO_VERSION_CODE")) add("LUMO_VERSION_CODE")
        }
        if (missing.isEmpty()) return
        val message = "bundleRelease of $path needs ${missing.joinToString()} " +
            "(apps/android/RELEASE.md). Without them the bundle cannot be uploaded."

        // `bundleRelease` runs in every project, `:app-tv:bundleRelease` in one.
        val requested = gradle.startParameter.taskNames.any { name ->
            val task = name.substringAfterLast(':')
            val owner = name.substringBeforeLast(':', missingDelimiterValue = "")
            (task == "bundleRelease" || task == "bundle") && (owner.isEmpty() || owner == path)
        }
        if (requested) throw GradleException(message)
        tasks.matching { it.name == "bundleRelease" }.configureEach {
            doFirst { throw GradleException(message) }
        }
    }

    /**
     * `LUMO_VERSION_CODE`, a positive integer that grows with every upload.
     * Play caps it at 2 100 000 000; anything else is a typo worth a red build.
     */
    private fun Project.lumoVersionCode(): Int {
        val raw = lumoEnv("LUMO_VERSION_CODE", default = DEFAULT_VERSION_CODE.toString())
        val code = raw.toIntOrNull()
        if (code == null || code !in 1..MAX_VERSION_CODE) {
            throw GradleException(
                "LUMO_VERSION_CODE must be an integer between 1 and $MAX_VERSION_CODE, got '$raw'.",
            )
        }
        return code
    }

    private companion object {
        const val DEFAULT_VERSION_CODE = 1
        const val DEFAULT_VERSION_NAME = "0.4.0"
        const val MAX_VERSION_CODE = 2_100_000_000
    }
}
