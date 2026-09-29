import com.android.build.api.dsl.ApplicationExtension
import com.antbtv.balarm.buildlogic.configureAndroidCommon
import com.antbtv.balarm.buildlogic.intVersion
import com.antbtv.balarm.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("balarm.quality")

        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            defaultConfig.targetSdk = libs.intVersion("targetSdk")
            androidResources.generateLocaleConfig = true
            buildTypes.getByName("release").isMinifyEnabled = false // R8 — в M8
        }
    }
}
