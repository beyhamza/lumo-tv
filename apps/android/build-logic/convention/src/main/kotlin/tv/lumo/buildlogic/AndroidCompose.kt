package tv.lumo.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Compose configuration shared by every module that draws something.
 *
 * Note what is NOT here: Material 3 and Compose for TV. Those are the two
 * divergent design languages and they are declared once, in `core:designsystem`,
 * which re-exports them. A feature that reaches for `androidx.tv.material3`
 * directly instead of a `core:designsystem` component is how a TV screen ends up
 * with a focus state nobody can see (docs/architecture.md §3).
 */
internal fun Project.configureAndroidCompose(
    commonExtension: CommonExtension,
) {
    commonExtension.buildFeatures.compose = true

    val bom = libs.libraryOf("androidx-compose-bom")

    dependencies {
        add("implementation", platform(bom))
        add("implementation", libs.libraryOf("androidx-compose-runtime"))
        add("implementation", libs.libraryOf("androidx-compose-ui"))
        add("implementation", libs.libraryOf("androidx-compose-ui-graphics"))
        add("implementation", libs.libraryOf("androidx-compose-foundation"))
        add("implementation", libs.libraryOf("androidx-compose-ui-tooling-preview"))

        // @Preview rendering and the Compose layout inspector, debug builds only.
        add("debugImplementation", platform(bom))
        add("debugImplementation", libs.libraryOf("androidx-compose-ui-tooling"))

        add("androidTestImplementation", platform(bom))
        add("androidTestImplementation", libs.libraryOf("androidx-compose-ui-test-junit4"))
        add("debugImplementation", libs.libraryOf("androidx-compose-ui-test-manifest"))
    }
}
