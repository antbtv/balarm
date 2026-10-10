plugins {
    id("balarm.android.feature")
}

// Редактор знает только контракт движка и чистый домен; других :feature:* не знает (ADR-009). Контракт миссий
// (:feature:missions:api) — не «другая фича», а api-модуль (ADR-018 §5): строка «Миссия» и настройки.
// Время, дни, «через …» и snooze-минуты форматирует :core:format (ADR-011 §6); BackHandler — activity-compose.
dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.format)
    implementation(projects.feature.missions.api)
    implementation(libs.androidx.activity.compose)

    testImplementation(testFixtures(projects.core.domain))
}
