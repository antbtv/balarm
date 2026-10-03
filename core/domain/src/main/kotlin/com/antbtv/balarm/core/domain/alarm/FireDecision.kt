package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Что делать, когда `AlarmManager` разбудил приложение (ADR-006 §6). */
sealed interface FireDecision {
    /**
     * Звонить. [snoozesLeft] `null` — без ограничения; [canSnooze] учитывает флаг фичи, настройки и лимит.
     * [degraded] — хранилище недоступно, звоним с настройками по умолчанию («в сомнении — звони»).
     */
    data class Ring(val alarm: Alarm, val canSnooze: Boolean, val snoozesLeft: Int?, val degraded: Boolean = false) :
        FireDecision {
        companion object {
            /** Звонок без данных будильника: время — текущая минута, без «Отложить». */
            fun degraded(id: AlarmId, now: LocalTime) = Ring(
                alarm = Alarm(id = id, time = now.truncatedTo(ChronoUnit.MINUTES)),
                canSnooze = false,
                snoozesLeft = 0,
                degraded = true,
            )
        }
    }

    data class Skip(val reason: SkipReason) : FireDecision
}

enum class SkipReason {
    /** Срабатывание старше [AlarmEngine.LATE_GRACE] — часы переведены вперёд или alarm «залежался». */
    STALE,

    /** Будильник удалён, а интент успел сработать. */
    DELETED,

    /** Повторная доставка уже обработанного срабатывания. */
    DUPLICATE,

    /** Разовый будильник пропущен дольше [AlarmEngine.LATE_GRACE] — выключен, а не перенесён на завтра. */
    MISSED,
}

sealed interface SnoozeResult {
    data class Snoozed(val until: Instant) : SnoozeResult

    data object NotAllowed : SnoozeResult
}

enum class DismissReason { USER, AUTO_STOP }
