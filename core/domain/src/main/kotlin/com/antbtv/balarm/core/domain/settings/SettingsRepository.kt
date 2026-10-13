package com.antbtv.balarm.core.domain.settings

import com.antbtv.balarm.core.model.AppSettings
import kotlinx.coroutines.flow.Flow

/** Глобальные настройки (ADR-021). Чтение не падает: сбой хранилища даёт значения по умолчанию. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** Применяет [transform] к текущим настройкам; результат приводится к допустимому виду (`SettingsPolicy`). */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
