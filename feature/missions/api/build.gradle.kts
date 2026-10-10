plugins {
    id("balarm.android.feature")
}

// Контракт миссий (FR-MIS-4, ADR-018): определение миссии, сессия, реестр. Конкретные миссии — в :feature:missions:*.
dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
}
