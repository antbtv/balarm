package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AutoStop
import com.antbtv.balarm.core.model.MissionSpec
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

    /** «Отключить» при миссии (FR-RING-3): начинает сессию миссии, звук приглушается; `dismiss()` не сработает. */
    fun startMission()

    /** Любое касание в миссии: сбрасывает таймер бездействия (FR-MIS-2). */
    fun missionInteraction()

    /** Back из миссии (FR-MIS-3): назад на экран звонка, звук снова громкий. */
    fun leaveMission()

    /** Все задачи решены: единственный способ отключить звонок с миссией. */
    fun completeMission()

    /** Экран миссии больше не виден (Home, Recents, выключение экрана): звук сразу громкий. */
    fun missionScreenHidden()
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
        /** Миссия, которую надо пройти (уже с учётом флага); `null` — отключается сразу. */
        val mission: MissionSpec? = null,
        /** Идёт сессия миссии; `null` — экран звонка. */
        val missionRun: MissionRun? = null,
    ) : RingingState
}

/** Сессия миссии: [seed] — для задач, [idleResets] растёт при бездействии (FR-MIS-2) — миссия сбрасывает ввод. */
data class MissionRun(val seed: Long, val idleResets: Int = 0)

/** Политика сессии звонка (ADR-007). */
object RingingPolicy {
    /** Автостоп без реакции пользователя (FR-RING-6); в M1 — константа, настройка — с экраном настроек. */
    val AUTO_STOP_AFTER: Duration = Duration.ofMinutes(30)

    /** Задержка возобновления звонка после падения процесса (crash re-arm, ADR-007 §7; `rescheduleAll`, ADR-020 §4). */
    val CRASH_RESUME_DELAY: Duration = Duration.ofSeconds(3)

    /** Потолок окна возобновления звонка: при автостопе «никогда» звонок старше часа не оживает (ADR-021 §6). */
    val RESUME_WINDOW_CAP: Duration = Duration.ofMinutes(60)

    /** Сколько после начала звонка его ещё возвращают при перепланировании. */
    fun resumeWindow(autoStop: AutoStop): Duration =
        autoStop.duration?.coerceAtMost(RESUME_WINDOW_CAP) ?: RESUME_WINDOW_CAP
}
