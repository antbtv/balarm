package com.antbtv.balarm.core.model

import java.time.Instant

/** Почему запланировано ближайшее срабатывание (ADR-006 §5). */
enum class TriggerKind { REGULAR, SNOOZE, CATCH_UP }

/**
 * Служебное состояние будильника, которое переживает перезагрузку (ADR-004 `alarm_runtime`).
 * [nextTriggerAt] — ровно то, что отдано `setAlarmClock`; `null` — ничего не запланировано.
 * [scheduleFailed] — система отказала в `setAlarmClock` (ADR-015): [nextTriggerAt] тогда лишь момент «для повтора»,
 * в `AlarmManager` его нет. Снимается успешным планированием (любой `rescheduleAll`) или выключением.
 */
data class AlarmRuntimeState(
    val alarmId: AlarmId,
    val nextTriggerAt: Instant? = null,
    val nextTriggerKind: TriggerKind = TriggerKind.REGULAR,
    val snoozeCount: Int = 0,
    val lastFiredAt: Instant? = null,
    val scheduleFailed: Boolean = false,
) {
    init {
        require(alarmId.isSaved && !alarmId.isTest) { "Runtime state belongs to a stored alarm" }
        require(snoozeCount >= 0) { "snoozeCount must not be negative" }
    }

    /** Звонок, назначенный на [at] (или позже), уже был — повторная доставка того же срабатывания не должна звонить. */
    fun hasFiredFor(at: Instant): Boolean = lastFiredAt?.let { !it.isBefore(at) } == true
}
