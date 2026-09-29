package com.antbtv.balarm.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.intVersion(alias: String): Int = version(alias).toInt()

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).get()

/** `:feature:missions:math` → `com.antbtv.balarm.feature.missions.math` (ADR-003 §5). */
internal fun Project.namespaceFromPath(): String =
    path.split(':').filter { it.isNotEmpty() }.joinToString(".", prefix = "$BASE_NAMESPACE.") {
        it.replace('-', '_')
    }

internal const val BASE_NAMESPACE = "com.antbtv.balarm"
