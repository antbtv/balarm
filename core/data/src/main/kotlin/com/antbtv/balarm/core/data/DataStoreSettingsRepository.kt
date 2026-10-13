package com.antbtv.balarm.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.antbtv.balarm.core.domain.settings.SettingsPolicy
import com.antbtv.balarm.core.domain.settings.SettingsRepository
import com.antbtv.balarm.core.model.AppSettings
import com.antbtv.balarm.core.model.AutoStop
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Глобальные настройки в device-protected DataStore (ADR-021, ADR-001): читаются в Direct Boot.
 * Мусор и значения вне допустимого набора → умолчания; сбой чтения → умолчания. Флаг
 * [Feature.GLOBAL_SETTINGS] выключен → читатели видят умолчания, сохранённое не удаляется.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val flags: FeatureFlagProvider,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = store.data
        .map { prefs -> if (flags.isEnabled(Feature.GLOBAL_SETTINGS)) prefs.toSettings() else SettingsPolicy.DEFAULTS }
        .catch { e -> if (e is IOException) emit(SettingsPolicy.DEFAULTS) else throw e }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = SettingsPolicy.sanitize(transform(prefs.toSettings()))
            prefs[AUTO_STOP] = next.autoStop.name
            prefs[IDLE_SECONDS] = next.missionIdleTimeout.seconds.toInt()
            prefs[DUCK_PERCENT] = next.missionDuckPercent
            next.firstDayOfWeek?.let { prefs[FIRST_DAY] = it.value } ?: prefs.remove(FIRST_DAY)
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = SettingsPolicy.DEFAULTS
        return SettingsPolicy.sanitize(
            AppSettings(
                autoStop = this[AUTO_STOP]?.let { name -> AutoStop.entries.firstOrNull { it.name == name } }
                    ?: d.autoStop,
                missionIdleTimeout = this[IDLE_SECONDS]?.let { Duration.ofSeconds(it.toLong()) }
                    ?: d.missionIdleTimeout,
                missionDuckPercent = this[DUCK_PERCENT] ?: d.missionDuckPercent,
                firstDayOfWeek = this[FIRST_DAY]?.let { v -> DayOfWeek.entries.firstOrNull { it.value == v } },
            ),
        )
    }

    private companion object {
        val AUTO_STOP = stringPreferencesKey("settings.auto_stop")
        val IDLE_SECONDS = intPreferencesKey("settings.mission_idle_seconds")
        val DUCK_PERCENT = intPreferencesKey("settings.mission_duck_percent")
        val FIRST_DAY = intPreferencesKey("settings.first_day_of_week")
    }
}
