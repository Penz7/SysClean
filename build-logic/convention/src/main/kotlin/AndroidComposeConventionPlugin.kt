import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import vn.sysclean.buildlogic.lib
import vn.sysclean.buildlogic.libs

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        val android: CommonExtension<*, *, *, *, *, *> =
            extensions.findByType<ApplicationExtension>()
                ?: extensions.findByType<LibraryExtension>()
                ?: error("sysclean.android.compose must be applied after an Android plugin")
        android.buildFeatures.compose = true

        dependencies {
            val bom = platform(libs.lib("androidx-compose-bom"))
            add("implementation", bom)
            add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
            add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
        }
    }
}
