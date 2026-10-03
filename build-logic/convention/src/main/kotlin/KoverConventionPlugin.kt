import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Порог покрытия строк для доменного слоя (NFR-6); `koverVerify` входит в `check`. */
class KoverConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlinx.kover")

        extensions.configure<KoverProjectExtension> {
            reports {
                verify {
                    rule {
                        minBound(MIN_LINE_COVERAGE_PERCENT)
                    }
                }
            }
        }
        tasks.named("check").configure { dependsOn("koverVerify") }
    }

    private companion object {
        const val MIN_LINE_COVERAGE_PERCENT = 80
    }
}
