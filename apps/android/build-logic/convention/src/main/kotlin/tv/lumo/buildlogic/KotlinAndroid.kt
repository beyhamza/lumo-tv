package tv.lumo.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.kotlin.dsl.dependencies

/**
 * Android + Kotlin configuration shared by every module of the build.
 *
 * This is the only place `compileSdk`, `minSdk`, the JVM target and desugaring
 * are set. A module that needs a different value has an argument to make, not a
 * line to add to its own build file.
 *
 * Written in property style (`defaultConfig.minSdk = …`) rather than block style
 * (`defaultConfig { … }`): AGP 9 dropped the type parameters from
 * `CommonExtension` and moved the block-form DSL methods down onto
 * `ApplicationExtension` and `LibraryExtension`, so blocks no longer resolve on
 * the common type. The properties are the same objects.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    with(commonExtension) {
        compileSdk = libs.intVersion("compileSdk")
        defaultConfig.minSdk = libs.intVersion("minSdk")

        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17

        // The generated API client models use java.time (OffsetDateTime,
        // LocalDate), which Android only ships natively from API 26. We support
        // API 24 because Android TV boxes stay on old releases far longer than
        // phones do, so the desugared library is not optional.
        compileOptions.isCoreLibraryDesugaringEnabled = true

        lint.abortOnError = true
        // Missing translations fail the build rather than warn: FR and EN ship
        // together from the first screen (AGENTS.md §4), and a warning nobody
        // reads is how a release goes out half-translated.
        lint.fatal.add("MissingTranslation")
        lint.checkDependencies = true
    }

    // No `kotlin { compilerOptions { jvmTarget = … } }` here, and no
    // `org.jetbrains.kotlin.android` plugin anywhere in this build: AGP 9
    // compiles Kotlin itself and defaults the Kotlin jvmTarget to
    // `compileOptions.targetCompatibility`, set just above. Setting it twice is
    // how the two drift apart.

    dependencies {
        add("coreLibraryDesugaring", libs.libraryOf("desugar-jdk-libs"))
    }
}


internal fun VersionCatalog.libraryOf(alias: String) =
    findLibrary(alias).orElseThrow {
        IllegalStateException("Missing library '$alias' in gradle/libs.versions.toml")
    }
