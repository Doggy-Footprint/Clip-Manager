import com.android.build.api.dsl.LibraryExtension
import com.doggy.clip_manager.configureAndroidCommon
import com.doggy.clip_manager.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureAndroidCommon()
        }
        // Hilt/KSP generate test sources even in modules without tests, which Gradle 9 treats as a misconfiguration.
        tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
        }
    }
}
