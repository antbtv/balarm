package com.antbtv.balarm.core.domain.settings

import com.antbtv.balarm.core.model.AppSettings

/** Допустимые значения глобальных настроек и умолчания (ADR-021 п. 2). Значение вне набора → умолчание. */
object SettingsPolicy {
    val DEFAULTS = AppSettings()

    /** Бездействие в миссии, секунды: 10–60 с, шаг 5 (FR-MIS-2). */
    val IDLE_TIMEOUT_SECONDS: List<Long> = (10L..60L step 5L).toList()

    /** Приглушение звука на время миссии, %: 10–50, шаг 10. */
    val DUCK_PERCENTS: List<Int> = (10..50 step 10).toList()

    fun sanitize(settings: AppSettings): AppSettings = settings.copy(
        missionIdleTimeout = settings.missionIdleTimeout
            .takeIf { it.seconds in IDLE_TIMEOUT_SECONDS && it.nano == 0 }
            ?: DEFAULTS.missionIdleTimeout,
        missionDuckPercent = settings.missionDuckPercent.takeIf { it in DUCK_PERCENTS }
            ?: DEFAULTS.missionDuckPercent,
    )

    /** Целевой коэффициент усиления плеера для [AppSettings.missionDuckPercent]. */
    fun duckGain(settings: AppSettings): Float = settings.missionDuckPercent / PERCENT

    private const val PERCENT = 100f
}
