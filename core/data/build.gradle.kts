plugins {
    id("balarm.android.library")
    id("balarm.android.room")
    id("balarm.hilt")
}

dependencies {
    api(projects.core.domain)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
