package com.antbtv.balarm.debug

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.antbtv.balarm.core.alarm.LogcatAlarmEventLog
import com.antbtv.balarm.core.alarm.SafeRescheduler
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.alarm.RescheduleReason
import com.antbtv.balarm.core.domain.alarm.RingingController
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Debug-команды для сценариев надёжности (скилл verify-alarm-reliability). Только debug-сборка.
 * Результат — строки `DEBUG_* key=value` в logcat с тегом `Balarm`; метки будильников не логируются.
 */
class DebugAlarmCommands @Inject constructor(
    private val engine: AlarmEngine,
    private val repository: AlarmRepository,
    private val rescheduler: SafeRescheduler,
    private val ringing: RingingController,
    private val clock: Clock,
) {
    /**
     * Будильник через [delay] (минимум 1 с). Время будильника — целые минуты, поэтому момент
     * округляется вверх до следующей минуты; фактическое время — в логе.
     */
    suspend fun scheduleIn(delay: Duration, label: String, days: Set<DayOfWeek>): AlarmId {
        val target = LocalDateTime.now(clock).plus(delay.coerceAtLeast(Duration.ofSeconds(1)))
        val time = target.truncatedTo(ChronoUnit.MINUTES)
            .let { if (it.isBefore(target)) it.plusMinutes(1) else it }
            .toLocalTime()
        val id = engine.save(Alarm(time = time, repeatDays = days, label = label.take(Alarm.MAX_LABEL_LENGTH)))
        log("DEBUG_SCHEDULED id=${id.value} time=$time days=${days.joinToString(",")}")
        return id
    }

    suspend fun list() {
        val all = repository.loadAll()
        log("DEBUG_LIST count=${all.size}")
        all.forEach { (alarm, runtime) ->
            log(
                "DEBUG_ALARM id=${alarm.id.value} time=${alarm.time} days=${alarm.repeatDays.joinToString(",")} " +
                    "enabled=${alarm.enabled} next=${runtime?.nextTriggerAt} kind=${runtime?.nextTriggerKind} " +
                    "snoozes=${runtime?.snoozeCount ?: 0}",
            )
        }
    }

    fun dismiss() = command("DISMISS") { ringing.dismiss() }

    fun snooze() = command("SNOOZE") { ringing.snooze() }

    /** Без идущего звонка контроллер молча игнорирует команду — пишем это явно, чтобы не принять за баг. */
    private fun command(name: String, block: () -> Unit) {
        if (ringing.state.value is RingingState.Ringing) block() else log("DEBUG_$name ignored=no_ringing")
    }

    suspend fun rescheduleAll() = rescheduler.reschedule(RescheduleReason.DEBUG)

    suspend fun clearAll() {
        val ids = repository.loadAll().map { it.alarm.id }
        ids.forEach { engine.delete(it) }
        log("DEBUG_CLEARED count=${ids.size}")
    }

    /** Необработанное исключение на главном потоке — проверка crash re-arm (NFR-5, ADR-007 §7). */
    fun crash() {
        log("DEBUG_CRASH")
        Handler(Looper.getMainLooper()).post { throw IllegalStateException("Debug CRASH command") }
    }

    private fun log(line: String) {
        Log.i(LogcatAlarmEventLog.TAG, line)
    }

    companion object {
        /** `MON,TUE` → дни недели; неизвестные значения игнорируются. */
        fun parseDays(raw: String?): Set<DayOfWeek> = raw.orEmpty().split(',')
            .map { it.trim().uppercase() }
            .filter { it.length >= 2 }
            .mapNotNull { token -> DayOfWeek.entries.firstOrNull { it.name.startsWith(token) } }
            .toSet()
    }
}
