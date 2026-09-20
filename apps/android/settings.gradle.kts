// One Gradle build, two applications (ADR 0004).
//
// The module list below IS docs/architecture.md §3. Keep them in step: if you
// add a module here without adding it there, the next agent reads a lie.

pluginManagement {
    // The convention plugins live in an included build rather than in
    // `buildSrc`. A change to buildSrc invalidates the whole build's
    // configuration cache; an included build only invalidates what depends on
    // it. With 17 modules that difference is felt on every edit.
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle fetch the JDK 17 toolchain if the machine does not already
    // have one, so a fresh clone builds without a manual JDK install.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // A module declaring its own repository is a supply-chain hole and a source
    // of "works on my machine". Fail instead.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "lumo-android"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// ---- Applications -----------------------------------------------------------
// Two applicationIds, two Play listings (ADR 0004). UI only: no business logic
// belongs in either of these two modules.
include(":app-mobile")
include(":app-tv")

// ---- Core -------------------------------------------------------------------
// Shared by both applications, without exception.
include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:auth")
include(":core:database")
// The only module a feature talks to when it wants data. It owns the one
// translation of an API error and decides, per read, whether the answer comes
// from the network or from the cache.
include(":core:data")
include(":core:player")

// ---- Features ---------------------------------------------------------------
// A feature owns its state and its two UI surfaces (mobile and TV). It never
// depends on another feature — shared behaviour moves down into core/.
include(":feature:onboarding")
// What a signed-in user lands on (US-017): the three rails of the active source.
// It reads films, series, favourites and recent channels, and depends on none of
// the features that own those screens — everything it shares with them lives in
// core:data.
include(":feature:home")
include(":feature:auth")
include(":feature:source")
include(":feature:live")
// Beside the catalogue rather than inside it: a group of favourites belongs to
// the account and can hold channels from two sources, so it has no source id to
// hang off (US-12).
include(":feature:favorites")
include(":feature:vod")
include(":feature:series")
include(":feature:search")
include(":feature:settings")
