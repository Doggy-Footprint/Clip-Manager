import com.doggy.clip_manager.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class RoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")
        dependencies {
            add("testImplementation", libs.findLibrary("robolectric").get())
            add("testImplementation", libs.findLibrary("roborazzi").get())
            add("testImplementation", libs.findLibrary("roborazzi-compose").get())
            add("testImplementation", libs.findLibrary("roborazzi-junitRule").get())
            add("testImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
        }

        // In record mode roborazzi overwrites goldens without comparing, so screenshot tests pass
        // regardless of what they render. A run in that mode has verified nothing and must not be
        // reported as green; scripts/record-goldens.sh opts in when overwriting is the intent.
        val recording = providers.gradleProperty("roborazzi.test.record").map { it.toBoolean() }.orElse(false)
        val sanctioned = providers.gradleProperty("clip.goldens.record").map { it.toBoolean() }.orElse(false)
        val verifying = providers.gradleProperty("roborazzi.test.verify").map { it.toBoolean() }.orElse(false)
        tasks.withType<Test>().configureEach {
            doFirst {
                check(!(recording.get() && !sanctioned.get())) {
                    "roborazzi.test.record overwrites goldens instead of comparing them, so this run " +
                        "would assert nothing. Use scripts/record-goldens.sh to update goldens."
                }
                check(recording.get() || verifying.get()) {
                    "roborazzi.test.verify is not set, so screenshot tests would only write goldens " +
                        "and could never fail. Restore it in gradle.properties."
                }
            }
        }
    }
}
