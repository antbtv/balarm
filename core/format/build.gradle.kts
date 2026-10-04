plugins {
    id("balarm.android.library")
    id("balarm.android.compose")
}

// Единое форматирование времени/дат/дней для уведомлений и экранов (ADR-011 §6). Compose — только remember*-хелперы.
dependencies {
    api(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
