// build-logic is an included build (see ../settings.gradle.kts), so it has its
// own repositories and its own view of the version catalogue.

dependencyResolutionManagement {
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

    // The SAME catalogue the applications use. The convention plugins must not
    // pin their own versions of AGP or Kotlin: two sources of truth for the
    // toolchain version is exactly the drift this build exists to prevent.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"

include(":convention")
