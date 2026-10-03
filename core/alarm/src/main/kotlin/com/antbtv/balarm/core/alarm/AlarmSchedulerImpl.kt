package com.antbtv.balarm.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.antbtv.balarm.core.domain.alarm.AlarmScheduler
import com.antbtv.balarm.core.domain.alarm.ScheduleRequest
import com.antbtv.balarm.core.model.AlarmId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Только `setAlarmClock` (FR-REL-1): не откладывается Doze, показывает значок будильника в статус-баре,
 * даёт право запустить foreground service из ресивера срабатывания.
 */
@Singleton
class AlarmSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uiIntents: AlarmUiIntents,
) : AlarmScheduler {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(request: ScheduleRequest): Boolean {
        require(request.alarmId.isSaved) { "Only saved alarms can be scheduled" }
        if (!alarmManager.canScheduleExactAlarms()) return false
        val operation = PendingIntent.getBroadcast(
            context,
            0,
            AlarmIntents.fire(context, request),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val show = PendingIntent.getActivity(
            context,
            0,
            uiIntents.alarmList(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(request.triggerAt.toEpochMilli(), show), operation)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    override fun cancel(alarmId: AlarmId) {
        val existing = PendingIntent.getBroadcast(
            context,
            0,
            AlarmIntents.fire(context, alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }
}
