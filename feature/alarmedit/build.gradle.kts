plugins {
    id("balarm.android.feature")
}

// Редактор знает только контракт движка и чистый домен; других :feature:* не знает (ADR-009).
dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)

    testImplementation(testFixtures(projects.core.domain))
}
