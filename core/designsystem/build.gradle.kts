plugins {
    id("balarm.android.library")
    id("balarm.android.compose")
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
}
