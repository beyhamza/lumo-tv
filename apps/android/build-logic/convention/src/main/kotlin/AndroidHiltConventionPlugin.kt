import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.dependencies
import tv.lumo.buildlogic.libraryOf
import tv.lumo.buildlogic.libs

/**
 * Hilt, wired through KSP rather than kapt.
 *
 * kapt runs the Java annotation processor against stubs it generates from
 * Kotlin, which roughly doubles the cost of every build that touches a module
 * with a graph in it. KSP reads the Kotlin directly.
 *
 * Full binding graph validation is on. By default Dagger inspects only the
 * bindings it can reach from an entry point, which in a codebase whose screens
 * are still placeholders is almost none of them: two modules can bind the same
 * type, and the build stays green until the first screen asks for it and the
 * error arrives with a stack trace instead of a file and a line.
 *
 * Measured, not assumed. Two `@Provides` methods returning the same unused type
 * compile without complaint with the option off, and fail with
 * `[Dagger/DuplicateBindings]` with it on. What it still does not catch is a
 * missing dependency of an unreachable binding - Dagger reports MissingBinding
 * only along a path something actually requests, so the real guard there is
 * having at least one genuine entry point in the graph (see
 * feature:settings/SettingsViewModel).
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        // On the Hilt aggregating task and nowhere else. That task is where the
        // component is actually assembled - a plain JavaCompile named
        // hiltJavaCompileDebug - so it is the only compilation with the whole
        // graph in front of it. Setting the same option on KSP looks right and
        // changes nothing; it was tried, and a module carrying a deliberately
        // unsatisfiable binding still compiled.
        tasks.withType(JavaCompile::class.java).matching { it.name.startsWith("hilt") }
            .configureEach { options.compilerArgs.add("-Adagger.fullBindingGraphValidation=ERROR") }

        dependencies {
            add("implementation", libs.libraryOf("hilt-android"))
            add("ksp", libs.libraryOf("hilt-compiler"))
        }
    }
}
