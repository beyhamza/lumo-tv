package tv.lumo.buildlogic

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Copies a directory of generated sources into a directory AGP owns.
 *
 * The reason this exists rather than a plain `srcDir(…)`: AGP 9 wants generated
 * sources registered through the variant API
 * (`variant.sources.kotlin.addGeneratedSourceDirectory`), and that API wires
 * itself to a task's `DirectoryProperty` output. openapi-generator's
 * `GenerateTask` exposes its output as a `Property<String>`, which AGP cannot
 * wire to, so this task republishes it as a real output directory.
 *
 * What that buys, beyond satisfying a signature: the Kotlin compilation now has
 * a declared dependency on generation instead of an ordering that happens to
 * work. A build where the client is compiled before it is generated fails on a
 * clean checkout and on CI, and nowhere else.
 */
@CacheableTask
abstract class SyncGeneratedSources : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: DirectoryProperty

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun sync() {
        fileSystem.sync {
            from(source)
            into(destination)
        }
    }
}
