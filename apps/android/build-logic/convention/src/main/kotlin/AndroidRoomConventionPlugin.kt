import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import tv.lumo.buildlogic.libraryOf
import tv.lumo.buildlogic.libs

/**
 * Room for `core:database`.
 *
 * The exported schema JSON is committed. It is what makes a migration
 * reviewable — a diff of the schema file says exactly what changed, and Room's
 * migration tests read it. Without it, "did this release need a migration?" is
 * answered by crashing on a user's device.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")

        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        dependencies {
            add("implementation", libs.libraryOf("androidx-room-runtime"))
            add("implementation", libs.libraryOf("androidx-room-ktx"))
            add("ksp", libs.libraryOf("androidx-room-compiler"))
        }
    }
}
