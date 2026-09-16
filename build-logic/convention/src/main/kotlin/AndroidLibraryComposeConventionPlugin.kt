import com.android.build.api.dsl.LibraryExtension
import com.doggy.clip_manager.addComposeDependencies
import com.doggy.clip_manager.configureCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("clip.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<LibraryExtension> { configureCompose() }
        addComposeDependencies()
    }
}
