package com.antbtv.balarm.feature.ringing.ui

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime

/**
 * Состояние экрана звонка (FR-RING-2).
 *
 * @property now текущее локальное время с точностью до минуты — часы на экране тикают, а не показывают время
 * срабатывания.
 */
@Immutable
data class RingingUiState(
    val now: LocalDateTime,
    val phase: RingingPhase,
    val label: String = "",
    val snooze: SnoozeUi = SnoozeUi.Hidden,
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
    data object Dismiss : RingingEvent

    data object Snooze : RingingEvent
}

/** Теги для Compose UI-тестов. */
object RingingTestTags {
    const val ROOT = "ringing_root"
    const val DATE = "ringing_date"
    const val TIME = "ringing_time"
    const val LABEL = "ringing_label"
    const val SNOOZE = "ringing_snooze"
    const val DISMISS = "ringing_dismiss"
}
