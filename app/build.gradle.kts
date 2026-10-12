import java.util.Properties

plugins {
    id("balarm.android.application")
    id("balarm.android.compose")
    id("balarm.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.antbtv.balarm"

    defaultConfig {
        applicationId = "com.antbtv.balarm"
        versionCode = 1
        versionName = "0.0.1"
    }

    // Подпись release: keystore.properties (storeFile, storePassword, keyAlias, keyPassword) лежит вне git.
    // Без файла собирается неподписанный APK (CI, чужие машины), а не падает сборка. См. docs/RELEASE.md.
    val keystoreProps = rootProject.file("keystore.properties")
        .takeIf { it.exists() }
        ?.inputStream()?.use { Properties().apply { load(it) } }
    val keystoreFile = keystoreProps?.getProperty("storeFile")?.let { rootProject.file(it) }
    if (keystoreProps != null && keystoreFile?.exists() != true) {
        logger.warn("keystore.properties: ключ $keystoreFile не найден — release соберётся неподписанным")
    }
    fun Properties.required(name: String): String =
        requireNotNull(getProperty(name)) { "keystore.properties: нет $name" }
    if (keystoreProps != null && keystoreFile?.exists() == true) {
        signingConfigs.create("release") {
            storeFile = keystoreFile
            storePassword = keystoreProps.required("storePassword")
            keyAlias = keystoreProps.required("keyAlias")
            keyPassword = keystoreProps.required("keyPassword")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.alarm)
    implementation(projects.core.permissions)
    implementation(projects.feature.alarmlist)
    implementation(projects.feature.alarmedit)
    implementation(projects.feature.ringing)
    implementation(projects.feature.settings)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.sounds)
    implementation(projects.feature.missions.api)
    implementation(projects.feature.missions.math)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.hilt.android.testing)
    testImplementation(testFixtures(projects.core.domain))
    kspTest(libs.hilt.compiler)
}
