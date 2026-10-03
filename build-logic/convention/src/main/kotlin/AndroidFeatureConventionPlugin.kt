import com.antbtv.balarm.buildlogic.library
import com.antbtv.balarm.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Feature-модуль: Android library + Compose + Hilt + ViewModel + дизайн-система + тестовый стек UI. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("balarm.android.library")
        pluginManager.apply("balarm.android.compose")
        pluginManager.apply("balarm.hilt")

        dependencies {
            add("implementation", project(":core:designsystem"))
            add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))

            add("testImplementation", libs.library("junit4"))
            add("testImplementation", libs.library("truth"))
            add("testImplementation", libs.library("turbine"))
            add("testImplementation", libs.library("kotlinx-coroutines-test"))
            add("testImplementation", libs.library("robolectric"))
            add("testImplementation", libs.library("androidx-test-ext-junit"))
            add("testImplementation", libs.library("androidx-test-espresso-core"))
            add("testImplementation", libs.library("androidx-compose-ui-test-junit4"))
        }
    }
}
