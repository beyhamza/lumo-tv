import tv.lumo.buildlogic.SyncGeneratedSources
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import tv.lumo.buildlogic.lumoEnv

plugins {
    alias(libs.plugins.lumo.android.library)
    alias(libs.plugins.lumo.android.hilt)
    alias(libs.plugins.openapi.generator)
}

android {
    namespace = "tv.lumo.android.core.network"

    buildFeatures {
        // The API base URL and timeout come from .env at build time rather than
        // from a constant somebody has to remember to change before a release.
        buildConfig = true
    }

    defaultConfig {
        buildConfigField(
            "int",
            "API_TIMEOUT_SECONDS",
            lumoEnv("LUMO_API_TIMEOUT_SECONDS", default = "30"),
        )
    }

    buildTypes {
        debug {
            // 10.0.2.2 is how the emulator reaches the host machine
            // (docs/architecture.md §7). `localhost` inside the emulator is the
            // emulator itself — the single most common setup mistake.
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${lumoEnv("LUMO_API_BASE_URL_DEBUG", default = "http://10.0.2.2:8080/v1")}\"",
            )
        }
        release {
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${lumoEnv("LUMO_API_BASE_URL_RELEASE", default = "https://api.lumo.tv/v1")}\"",
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Contract-first (ADR 0001).
//
// The Retrofit client is GENERATED from packages/contracts/openapi.yaml at
// build time, into build/, exactly as apps/api generates its server interfaces.
// Nothing under core/network/src is allowed to describe a request or a response
// shape: change the contract, regenerate, adapt.
//
// The generator options come from the SAME file the npm pipeline uses
// (packages/contracts/config/kotlin.yaml), so `./gradlew assemble` and
// `npm run generate` cannot produce different clients.
// ---------------------------------------------------------------------------
val contractsDir = rootProject.layout.projectDirectory.dir("../../packages/contracts")
val contractFile = contractsDir.file("openapi.yaml")
val generatorConfig = contractsDir.file("config/kotlin.yaml")
val generatedClientDir = layout.buildDirectory.dir("generated/openapi")

val openApiGenerate = tasks.named<GenerateTask>("openApiGenerate") {
    configFile = generatorConfig.asFile.absolutePath
    inputSpec = contractFile.asFile.absolutePath
    outputDir = generatedClientDir.map { it.asFile.absolutePath }

    // Re-run when the contract or the generator options change, and only then.
    inputs.file(contractFile)
    inputs.file(generatorConfig)
}

// AGP 9 registers generated sources through the variant API, which wires itself
// to a task output directory. openapi-generator publishes its output as a
// String property, so SyncGeneratedSources republishes it as a real
// DirectoryProperty — that is what gives Kotlin compilation a declared
// dependency on generation rather than a lucky task ordering.
val syncGeneratedClient = tasks.register<SyncGeneratedSources>("syncGeneratedApiClient") {
    dependsOn(openApiGenerate)
    source = generatedClientDir.map { it.dir("src/main/kotlin") }
}

androidComponents.onVariants { variant ->
    variant.sources.kotlin?.addGeneratedSourceDirectory(
        syncGeneratedClient,
        SyncGeneratedSources::destination,
    )
}

dependencies {
    implementation(projects.core.common)
    // The token store and the single-flight refresh live in core:auth; this
    // module supplies the transport that refresh needs (see TokenRefresher).
    implementation(projects.core.auth)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.core)
    // Reflection-based adapters: the generated models carry @Json but no
    // @JsonClass(generateAdapter = true), so Moshi builds their adapters at
    // runtime. consumer-rules.pro keeps what that reflection needs.
    implementation(libs.moshi.kotlin)

    testImplementation(libs.okhttp.mockwebserver)
}
