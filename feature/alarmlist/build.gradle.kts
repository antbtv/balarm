plugins {
    id("balarm.android.feature")
}

// Список знает только контракт движка и чистый домен (:core:domain); других :feature:* не знает (ADR-009).
dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)

    testImplementation(testFixtures(projects.core.domain))
}
