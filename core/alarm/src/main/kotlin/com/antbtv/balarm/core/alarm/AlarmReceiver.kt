package com.antbtv.balarm.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antbtv.balarm.core.alarm.notification.AlarmNotifications
import com.antbtv.balarm.core.alarm.ring.RingingService
import com.antbtv.balarm.core.alarm.ring.RingingWakeLocks
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Точка входа срабатывания `setAlarmClock` (ADR-007 §1). Окно на старт FGS после доставки alarm короткое,
 * поэтому сразу WakeLock и `startForegroundService` — без чтения БД; всё остальное делает сервис.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var notifications: Lazy<AlarmNotifications>

    @Inject lateinit var log: AlarmEventLog

    @Suppress("TooGenericExceptionCaught") // любой отказ старта сервиса → fallback-уведомление, а не тишина
    override fun onReceive(context: Context, intent: Intent) {
        val request = AlarmIntents.parse(intent) ?: return
        RingingWakeLocks.acquireDelivery(context)
        try {
            context.startForegroundService(AlarmIntents.ring(context, request))
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException и т.п.: звонит сама система (ADR-002 §6).
            RingingService.notifyFallback(context) { notifications.get() } // первым: звонок важнее лога
            RingingWakeLocks.releaseDelivery()
            log.log(AlarmEvent.ForegroundStartFailed(request.alarmId, e.javaClass.simpleName))
        }
    }
}
