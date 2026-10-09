plugins {
    id("balarm.android.feature")
}

// Пикер мелодий и библиотека «Мои мелодии» (FR-SND-2/3, ADR-016 §8). Знает домен (SoundRepository, SoundPreview),
// модель и названия встроенных мелодий (:core:format); других :feature:* и навигацию не знает (ADR-009).
// SAF-пикер — activity-compose (rememberLauncherForActivityResult).
dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.format)
    implementation(projects.core.model)
    implementation(libs.androidx.activity.compose)
}
