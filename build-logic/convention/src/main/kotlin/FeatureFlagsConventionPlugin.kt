import com.antbtv.balarm.buildlogic.featureflags.GenerateFeatureFlagsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Генерирует `enum class Feature` из config/features.properties. Применяется только в `:core:model`. */
class FeatureFlagsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val generate = tasks.register<GenerateFeatureFlagsTask>("generateFeatureFlags") {
            configFile.set(rootProject.layout.projectDirectory.file("config/features.properties"))
            packageName.set("com.antbtv.balarm.core.model.feature")
            outputDir.set(layout.buildDirectory.dir("generated/featureflags"))
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            extensions.configure<KotlinJvmProjectExtension> {
                sourceSets.getByName("main").kotlin.srcDir(generate)
            }
        }
    }
}
