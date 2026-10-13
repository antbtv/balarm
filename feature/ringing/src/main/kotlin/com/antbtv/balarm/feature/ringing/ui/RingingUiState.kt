package com.antbtv.balarm.feature.ringing.ui

import androidx.compose.runtime.Immutable
import com.antbtv.balarm.core.domain.alarm.MissionPolicy
import com.antbtv.balarm.feature.missions.api.MissionDefinition
import java.time.Duration
import java.time.LocalDateTime

/**
 * Состояние экрана звонка (FR-RING-2).
 *
 * @property now текущее локальное время с точностью до минуты — часы на экране тикают, а не показывают время
 * срабатывания.
 * @property mission идёт сессия миссии (FR-RING-3) — вместо экрана звонка показывается экран миссии.
 */
@Immutable
data class RingingUiState(
    val now: LocalDateTime,
    val phase: RingingPhase,
    val label: String = "",
    val snooze: SnoozeUi = SnoozeUi.Hidden,
    val mission: MissionUi? = null,
)

/**
 * Экран миссии на звонке (ADR-019): [definition] — плагин из реестра, [config] — его JSON, [seed] и [resetSignal]
 * — из сессии сервиса (`MissionRun`). [done]/[total] — прогресс, о котором сообщила сама миссия; [interactions] —
 * счётчик касаний (перезапуск полоски бездействия).
 */
@Immutable
data class MissionUi(
    val definition: MissionDefinition,
    val config: String,
    val seed: Long,
    val resetSignal: Int,
    val done: Int = 0,
    val total: Int = 0,
    val interactions: Int = 0,
    val idleTimeout: Duration = MissionPolicy.IDLE_TIMEOUT,
)

enum class RingingPhase {
    /** Экран открылся раньше, чем сервис опубликовал звонок (FSI уходит до решения движка, ADR-007 §2.1). */
    WAITING,
    RINGING,

    /** Звонок завершён (отключён, отложен, автостоп) — Activity закрывается. */
    FINISHED,
}

/** Кнопка «Отложить»: скрыта (флаг, настройки, лимит), без счётчика или с остатком. */
@Immutable
sealed interface SnoozeUi {
    data object Hidden : SnoozeUi

    data object Unlimited : SnoozeUi

    data class Limited(val left: Int) : SnoozeUi
}

sealed interface RingingEvent {
    /** «Отключить»: без миссии — отключение, с миссией — начало миссии (FR-RING-3). */
    data object Dismiss : RingingEvent

    data object Snooze : RingingEvent

    /** Любое касание в миссии (FR-MIS-2). */
    data object MissionInteraction : RingingEvent

    data class MissionProgress(val done: Int, val total: Int) : RingingEvent

    data object MissionCompleted : RingingEvent

    /** Back на экране миссии: назад на звонок, звук снова громкий (FR-MIS-3). */
    data object MissionBack : RingingEvent
}

/** Теги для Compose UI-тестов. */
object RingingTestTags {
    const val ROOT = "ringing_root"
    const val DATE = "ringing_date"
    const val TIME = "ringing_time"
    const val LABEL = "ringing_label"
    const val SNOOZE = "ringing_snooze"
    const val DISMISS = "ringing_dismiss"
    const val MISSION = "ringing_mission"
}
