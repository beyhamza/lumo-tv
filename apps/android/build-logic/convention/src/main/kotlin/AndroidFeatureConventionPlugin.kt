import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project
import tv.lumo.buildlogic.libraryOf
import tv.lumo.buildlogic.libs

/**
 * What every `feature:*` module is.
 *
 * A feature module holds the state and the two UI surfaces of one user-facing
 * area. The rule it enforces by construction: a feature never depends on
 * another feature. If two features need the same thing, that thing belongs in
 * `core:` — which is also why this plugin wires `core:designsystem` and
 * `core:common` in for free, and nothing else. A feature that needs
 * `core:network` or `core:database` declares it, so its build file states what
 * it actually touches.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("lumo.android.library")
        pluginManager.apply("lumo.android.library.compose")
        pluginManager.apply("lumo.android.hilt")

        dependencies {
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))

            add("implementation", libs.libraryOf("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.libraryOf("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.libraryOf("androidx-navigation-compose"))
            add("implementation", libs.libraryOf("androidx-hilt-navigation-compose"))
            add("implementation", libs.libraryOf("androidx-hilt-lifecycle-viewmodel-compose"))
        }
    }
}
