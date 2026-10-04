plugins {
    id("balarm.android.feature")
}

// ADR-007 §9: экран звонка знает только контракт RingingController (:core:domain), не :core:alarm.
dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.format)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    testImplementation(testFixtures(projects.core.domain))
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
