plugins {
    id("balarm.android.feature")
}

// Список знает только контракт движка и чистый домен (:core:domain); других :feature:* не знает (ADR-009).
// Время, дни и «через …» форматирует :core:format (ADR-011 §6).
dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.format)

    testImplementation(testFixtures(projects.core.domain))
}
