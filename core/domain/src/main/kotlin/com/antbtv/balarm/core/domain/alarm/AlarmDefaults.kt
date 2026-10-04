package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.SnoozeSettings
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Значения нового будильника в редакторе (ADR-011 §8). */
object AlarmDefaults {
    /** Следующий целый час (23:40 → 00:00), разовый, без метки, snooze по умолчанию. */
    fun newAlarm(now: LocalTime): Alarm = Alarm(
        time = now.truncatedTo(ChronoUnit.HOURS).plusHours(1),
        snooze = SnoozeSettings.DEFAULT,
    )
}
