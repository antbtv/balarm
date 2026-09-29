package com.antbtv.balarm.debug

import android.content.Context
import androidx.core.content.edit
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Только debug-сборка. Переопределения хранятся в device-protected storage (ADR-001),
 * синхронно через SharedPreferences — инструмент разработчика, не продуктовые данные.
 */
@Singleton
class DebugFeatureFlagProvider @Inject constructor(@ApplicationContext context: Context) : FeatureFlagProvider {

    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isEnabled(feature: Feature): Boolean = prefs.getBoolean(feature.key, feature.defaultEnabled)

    fun isOverridden(feature: Feature): Boolean = prefs.contains(feature.key)

    /** Значение, равное конфигу, не хранится — иначе оно перекрыло бы будущую правку features.properties. */
    fun setOverride(feature: Feature, enabled: Boolean) = prefs.edit {
        if (enabled == feature.defaultEnabled) remove(feature.key) else putBoolean(feature.key, enabled)
    }

    fun resetAll() = prefs.edit { clear() }

    private companion object {
        const val PREFS_NAME = "debug_feature_flags"
    }
}
