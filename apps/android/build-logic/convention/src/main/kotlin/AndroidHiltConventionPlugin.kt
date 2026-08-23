import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import tv.lumo.buildlogic.libraryOf
import tv.lumo.buildlogic.libs

/**
 * Hilt, wired through KSP rather than kapt.
 *
 * kapt runs the Java annotation processor against stubs it generates from
 * Kotlin, which roughly doubles the cost of every build that touches a module
 * with a graph in it. KSP reads the Kotlin directly.
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        dependencies {
            add("implementation", libs.libraryOf("hilt-android"))
            add("ksp", libs.libraryOf("hilt-compiler"))
        }
    }
}
