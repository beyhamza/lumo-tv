package tv.lumo.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies

/**
 * The unit-test toolkit every module gets.
 *
 * AGENTS.md §5: domain logic ships with its tests, UI does not need exhaustive
 * coverage. So the default set is deliberately JVM-only — JUnit, Truth, Turbine
 * for Flows, and the coroutines test dispatchers. A module that genuinely needs
 * an instrumented test adds it itself; making every module pay for an emulator
 * is how a test suite stops being run.
 */
internal fun Project.configureUnitTests() {
    tasks.withType(Test::class.java).configureEach {
        // Gradle 9 fails a test task that has sources but discovers no tests.
        // Every module here has "sources" whether or not anyone wrote a test:
        // Hilt's KSP processor generates code into the unit-test source set. A
        // module with no tests of its own would fail the build for having none,
        // which would make `./gradlew build` unusable on a UI-only module.
        failOnNoDiscoveredTests.set(false)
    }

    dependencies {
        add("testImplementation", libs.libraryOf("junit"))
        add("testImplementation", libs.libraryOf("truth"))
        add("testImplementation", libs.libraryOf("turbine"))
        add("testImplementation", libs.libraryOf("kotlinx-coroutines-test"))
    }
}
