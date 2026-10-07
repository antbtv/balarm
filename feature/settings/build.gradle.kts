plugins {
    id("balarm.android.feature")
}

// Настройки, «Здоровье будильника», «О приложении» (ADR-014 §5). Знает домен и :core:permissions (тексты, «Исправить»);
// других :feature:* и навигацию не знает (ADR-009).
dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.permissions)

    testImplementation(testFixtures(projects.core.domain))
}
