package com.antbtv.balarm.core.model.feature

/**
 * Единственная точка проверки feature flags (PRD §3.12).
 * Release — значения из config/features.properties; debug — плюс переопределения с экрана флагов.
 */
fun interface FeatureFlagProvider {
    fun isEnabled(feature: Feature): Boolean
}

/** Значения ровно из конфига сборки. */
object ConfigFeatureFlagProvider : FeatureFlagProvider {
    override fun isEnabled(feature: Feature): Boolean = feature.defaultEnabled
}
