import com.antbtv.balarm.buildlogic.libs
import com.antbtv.balarm.buildlogic.version
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** detekt + ktlint; оба подвешены к `check`. Применяется другими convention-плагинами. */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dev.detekt")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")

        val configDir = rootProject.layout.projectDirectory.dir("config/detekt")
        extensions.configure<DetektExtension> {
            buildUponDefaultConfig.set(true)
            config.setFrom(configDir.file("detekt.yml"))
            baseline.set(layout.projectDirectory.file("detekt-baseline.xml"))
            parallel.set(true)
        }

        extensions.configure<KtlintExtension> {
            version.set(libs.version("ktlint"))
            android.set(true)
            filter {
                exclude { it.file.path.contains("/build/") }
            }
        }
    }
}
