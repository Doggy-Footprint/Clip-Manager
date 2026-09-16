import com.doggy.clip_manager.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("clip.android.library.compose")
        pluginManager.apply("clip.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:designsystem"))
            add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("androidx-navigation-compose").get())
            add("implementation", libs.findLibrary("kotlinx-serialization-json").get())
            add("testImplementation", project(":core:testing"))
        }
    }
}
