import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryNdkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("clip.android.library")
        extensions.configure<LibraryExtension> {
            defaultConfig {
                ndk {
                    // Prebuilt FFmpeg libs in src/main/jniLibs exist only for these ABIs.
                    abiFilters += listOf("arm64-v8a", "x86_64")
                }
                externalNativeBuild {
                    cmake {
                        arguments += "-DANDROID_STL=c++_shared"
                    }
                }
            }
            externalNativeBuild {
                cmake {
                    path = file("src/main/cpp/CMakeLists.txt")
                }
            }
            packaging {
                jniLibs {
                    // jniLibs/ carries ffmpeg-kit's libc++_shared.so, which collides with the NDK copy.
                    pickFirsts += "**/libc++_shared.so"
                }
            }
        }
    }
}
