package com.antbtv.balarm.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Общая настройка Android-модулей: SDK, Java/Kotlin target, lint. Kotlin — встроенный в AGP 9. */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    val javaVersion = JavaVersion.toVersion(libs.version("jvmTarget"))
    extension.apply {
        compileSdk = libs.intVersion("compileSdk")
        defaultConfig.apply {
            minSdk = libs.intVersion("minSdk")
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions.apply {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }
        lint.apply {
            abortOnError = true
            checkDependencies = true
            warningsAsErrors = false
            // Версии зафиксированы осознанно (ADR-003), обновления — отдельными задачами.
            disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        }
        testOptions.apply {
            unitTests.isIncludeAndroidResources = true
        }
    }
    configureTestJvm()
}

/** Тестовые JVM: ограниченная память (ноутбук 15 ГБ) и доступ Robolectric к внутренностям java.io. */
internal fun Project.configureTestJvm() {
    tasks.withType<Test>().configureEach {
        maxHeapSize = "1g"
        maxParallelForks = 1
        jvmArgs(
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        )
    }
}

internal fun Project.configureKotlinJvm() {
    val jvmTarget = libs.version("jvmTarget")
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.toVersion(jvmTarget)
        targetCompatibility = JavaVersion.toVersion(jvmTarget)
    }
    // Демон работает на JDK 21: без -Xjdk-release чистый Kotlin мог бы вызвать API JDK 21
    // (например, List.removeLast()), которого нет на Android API 26–33.
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jvmTarget))
        compilerOptions.freeCompilerArgs.add("-Xjdk-release=$jvmTarget")
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(jvmTarget.toInt())
    }
}
