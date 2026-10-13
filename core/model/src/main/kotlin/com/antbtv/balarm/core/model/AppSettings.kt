package com.antbtv.balarm.core.model

import java.time.DayOfWeek
import java.time.Duration

/** Автостоп звонка без реакции пользователя (FR-RING-6). [NEVER] — звонит, пока его не отключат. */
@Suppress("MagicNumber") // значения набора — сама суть определения
enum class AutoStop(val duration: Duration?) {
    MIN_5(Duration.ofMinutes(5)),
    MIN_10(Duration.ofMinutes(10)),
    MIN_15(Duration.ofMinutes(15)),
    MIN_30(Duration.ofMinutes(30)),
    NEVER(null),
}

/**
 * Глобальные настройки звонка (PRD §3.9, ADR-021). Допустимые значения и умолчания — `SettingsPolicy`.
 * [firstDayOfWeek] `null` — по локали; на расписание не влияет.
 */
@Suppress("MagicNumber") // умолчания настроек (ADR-021)
data class AppSettings(
    val autoStop: AutoStop = AutoStop.MIN_30,
    val missionIdleTimeout: Duration = Duration.ofSeconds(15),
    val missionDuckPercent: Int = 30,
    val firstDayOfWeek: DayOfWeek? = null,
)
