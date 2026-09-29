import com.antbtv.balarm.buildlogic.configureKotlinJvm
import com.antbtv.balarm.buildlogic.configureTestJvm
import com.antbtv.balarm.buildlogic.library
import com.antbtv.balarm.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Чистый Kotlin без Android — для `:core:model` и `:core:domain`. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("balarm.quality")

        configureKotlinJvm()
        configureTestJvm()

        dependencies {
            add("testImplementation", libs.library("junit4"))
            add("testImplementation", libs.library("truth"))
        }
    }
}
