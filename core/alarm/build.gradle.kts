plugins {
    id("balarm.android.library")
    id("balarm.hilt")
}

dependencies {
    api(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(testFixtures(projects.core.domain))
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
