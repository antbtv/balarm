plugins {
    id("balarm.android.library")
    id("balarm.android.compose")
    id("balarm.hilt")
}

// Статусы разрешений/здоровья (ADR-012) и «Исправить»: платформенные API, интенты настроек, тексты пунктов.
// Не directBootAware и в цепочке звонка не участвует; зависимость на :core:alarm — только id канала звонка.
dependencies {
    api(projects.core.domain)
    implementation(projects.core.alarm)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
