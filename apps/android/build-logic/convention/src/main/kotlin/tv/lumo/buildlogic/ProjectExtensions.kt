package tv.lumo.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * The project's version catalogue, by the name it is registered under in
 * `settings.gradle.kts`.
 *
 * Convention plugins cannot use the generated `libs.` accessors — those are
 * produced for build scripts, not for plugin code — so every version a
 * convention plugin needs is looked up through here. It is still the same
 * `gradle/libs.versions.toml`, so there is exactly one place a version is
 * written down.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow {
        IllegalStateException("Missing version '$alias' in gradle/libs.versions.toml")
    }.requiredVersion

internal fun VersionCatalog.intVersion(alias: String): Int = version(alias).toInt()
