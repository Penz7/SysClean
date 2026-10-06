import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import vn.sysclean.buildlogic.lib
import vn.sysclean.buildlogic.libs

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("androidx.room")
        pluginManager.apply("com.google.devtools.ksp")

        // Exported schemas are committed so every future migration can be tested against them.
        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        dependencies {
            add("implementation", libs.lib("room-runtime"))
            add("implementation", libs.lib("room-ktx"))
            add("ksp", libs.lib("room-compiler"))
        }
    }
}
