plugins {
    `kotlin-dsl`
}

group = "com.antbtv.balarm.buildlogic"

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
    compileOnly(libs.detekt.gradle.plugin)
    compileOnly(libs.ktlint.gradle.plugin)
    compileOnly(libs.room.gradle.plugin)
    compileOnly(libs.kover.gradle.plugin)

    testImplementation(libs.junit4)
}

tasks.validatePlugins {
    enableStricterValidation = true
    failOnWarning = true
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "balarm.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "balarm.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "balarm.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "balarm.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("hilt") {
            id = "balarm.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("androidRoom") {
            id = "balarm.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("kover") {
            id = "balarm.kover"
            implementationClass = "KoverConventionPlugin"
        }
        register("androidFeature") {
            id = "balarm.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("featureFlags") {
            id = "balarm.featureflags"
            implementationClass = "FeatureFlagsConventionPlugin"
        }
        register("quality") {
            id = "balarm.quality"
            implementationClass = "QualityConventionPlugin"
        }
    }
}
