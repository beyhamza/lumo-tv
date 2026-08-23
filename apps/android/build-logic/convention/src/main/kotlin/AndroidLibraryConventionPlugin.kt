import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import tv.lumo.buildlogic.configureKotlinAndroid
import tv.lumo.buildlogic.configureUnitTests
import tv.lumo.buildlogic.libraryOf
import tv.lumo.buildlogic.libs

/** Applied by every `core:*` and `feature:*` module. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)

            defaultConfig {
                // No targetSdk here: AGP 9 removed it from library modules. A
                // library is compiled against whatever the application targets,
                // which is the only value that ever meant anything.
                consumerProguardFiles("consumer-rules.pro")
            }

            // A library module has no business shipping resources it does not
            // use, and R8 cannot always tell. Fail loudly instead.
            testOptions.unitTests.isIncludeAndroidResources = true
        }

        configureUnitTests()

        dependencies {
            add("implementation", libs.libraryOf("androidx-core-ktx"))
            add("implementation", libs.libraryOf("kotlinx-coroutines-core"))
        }
    }
}
