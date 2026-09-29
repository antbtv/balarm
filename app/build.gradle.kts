plugins {
    id("balarm.android.application")
    id("balarm.android.compose")
    id("balarm.hilt")
}

android {
    namespace = "com.antbtv.balarm"

    defaultConfig {
        applicationId = "com.antbtv.balarm"
        versionCode = 1
        versionName = "0.0.1"
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.designsystem)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.espresso.core)
}
