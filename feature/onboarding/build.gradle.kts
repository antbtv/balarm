plugins {
    id("balarm.android.feature")
}

// Онбординг 7 шагов (ADR-013, PRD §3.7). Знает домен здоровья и :core:permissions (тексты, «Исправить»);
// других :feature:* и навигацию не знает (ADR-009).
dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.permissions)

    testImplementation(testFixtures(projects.core.domain))
}
