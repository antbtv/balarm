plugins {
    id("balarm.android.feature")
}

// Миссия Math (FR-MIS, PRD §3.5): генератор примеров, конфиг (JSON), экран и редактор настроек.
dependencies {
    implementation(projects.core.model)
    implementation(projects.feature.missions.api)
    implementation(libs.kotlinx.serialization.json)
}
