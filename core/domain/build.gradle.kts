plugins {
    id("balarm.jvm.library")
    id("balarm.kover")
    // Фейки движка (репозиторий, планировщик, часы, лог) — общие для тестов :core:domain и :core:alarm.
    `java-test-fixtures`
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
