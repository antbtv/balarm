package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.AlarmId
import java.time.Instant

/** Тип срабатывания, передаваемый в интенте. [RESUME] — перезапуск звонка после падения, не персистится. */
enum class FireKind { REGULAR, SNOOZE, CATCH_UP, RESUME }

data class ScheduleRequest(val alarmId: AlarmId, val triggerAt: Instant, val kind: FireKind)

/** Обёртка над `AlarmManager.setAlarmClock` (`:core:alarm`, ADR-006 §2). Один будильник — один интент. */
interface AlarmScheduler {
    /** `false` — система отказала (нет права на точные будильники); исключение наружу не выходит. */
    fun schedule(request: ScheduleRequest): Boolean

    fun cancel(alarmId: AlarmId)
}
