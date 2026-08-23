package tv.lumo.buildlogic

import org.gradle.api.Project

/**
 * Build-time configuration read from `apps/android/.env`, falling back to a real
 * environment variable, falling back to a default.
 *
 * Order matters: CI has no `.env` file and injects secrets as environment
 * variables, while a developer machine has the file and no exported variables.
 * Trying the file first and the environment second serves both without a flag.
 *
 * Every key is documented in `apps/android/.env.example`. The file itself is
 * gitignored and never holds anything that must not leak (AGENTS.md §5) — the
 * signing passwords it can carry are why.
 *
 * Read through Gradle's provider API rather than `File.readText` so the
 * configuration cache invalidates when `.env` changes. Reading the file
 * directly would leave a stale cached configuration with the old API URL baked
 * in, and the resulting "my app still calls the wrong host" is a miserable
 * afternoon.
 */
fun Project.lumoEnv(key: String, default: String = ""): String {
    val fromFile = dotEnv()[key]
    if (!fromFile.isNullOrBlank()) return fromFile

    val fromEnvironment = providers.environmentVariable(key).orNull
    if (!fromEnvironment.isNullOrBlank()) return fromEnvironment

    return default
}

/** True when the key resolves to something non-blank. */
fun Project.hasLumoEnv(key: String): Boolean = lumoEnv(key).isNotBlank()

private fun Project.dotEnv(): Map<String, String> {
    val envFile = rootProject.layout.projectDirectory.file(".env")
    val contents = providers.fileContents(envFile).asText.orNull ?: return emptyMap()

    return contents.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .map { line ->
            val name = line.substringBefore('=').trim()
            val value = line.substringAfter('=').trim().removeSurrounding("\"")
            name to value
        }
        .toMap()
}
