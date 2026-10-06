import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import vn.sysclean.buildlogic.configureKotlinJvm
import vn.sysclean.buildlogic.lib
import vn.sysclean.buildlogic.libs

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureKotlinJvm()
        dependencies {
            add("testImplementation", libs.lib("junit"))
        }
    }
}
