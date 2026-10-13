package com.antbtv.balarm.feature.settings

import androidx.compose.runtime.Immutable
import com.antbtv.balarm.core.model.AppSettings
import com.antbtv.balarm.core.model.AutoStop
import java.time.DayOfWeek

/**
 * Значения секции «Общие» (PRD §3.9, ADR-021). Применяются со следующего звонка: сервис берёт снимок настроек
 * при старте звонка (ADR-021 п. 4), поэтому экран только сохраняет их.
 *
 * @param firstDayOfWeek `null` — «Как в регионе».
 */
@Immutable
data class GeneralSettingsUi(
    val autoStop: AutoStop,
    val missionIdleSeconds: Long,
    val missionDuckPercent: Int,
    val firstDayOfWeek: DayOfWeek?,
)

internal fun AppSettings.toGeneralUi(): GeneralSettingsUi = GeneralSettingsUi(
    autoStop = autoStop,
    missionIdleSeconds = missionIdleTimeout.seconds,
    missionDuckPercent = missionDuckPercent,
    firstDayOfWeek = firstDayOfWeek,
)

/** События вкладки «Настройки» (секция «Общие»): каждое изменение сохраняется сразу. */
sealed interface SettingsEvent {
    data class AutoStopChanged(val value: AutoStop) : SettingsEvent

    data class MissionIdleChanged(val seconds: Long) : SettingsEvent

    data class MissionDuckChanged(val percent: Int) : SettingsEvent

    /** `null` — «Как в регионе». */
    data class FirstDayOfWeekChanged(val day: DayOfWeek?) : SettingsEvent

    /** Снекбар об ошибке сохранения показан. */
    data object SaveFailedShown : SettingsEvent
}
