import com.android.build.api.dsl.ApplicationExtension
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
                // never be told the phone app is newer than the TV app.
                versionCode = 1
                versionName = "0.1.0"
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
    }
}
