package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.Alarm
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * Состояние текущего звонка для UI (ADR-007). Реализация — в `RingingService` (`:core:alarm`);
 * экран звонка только отображает состояние и отправляет команды — звук от него не зависит.
 */
interface RingingController {
    val state: StateFlow<RingingState>

    fun dismiss()

    fun snooze()
}

sealed interface RingingState {
    data object Idle : RingingState

    data class Ringing(
        val alarm: Alarm,
        val startedAt: Instant,
        val canSnooze: Boolean,
        val snoozesLeft: Int?,
        /** Сколько будильников ждут своей очереди (FR-RING-7). */
        val queued: Int = 0,
    ) : RingingState
}

/** Политика сессии звонка (ADR-007). */
object RingingPolicy {
    /** Автостоп без реакции пользователя (FR-RING-6); в M1 — константа, настройка — с экраном настроек. */
    val AUTO_STOP_AFTER: Duration = Duration.ofMinutes(30)
}
