package com.antbtv.balarm.core.alarm.notification

import android.app.NotificationManager
import android.content.Context
import com.antbtv.balarm.core.domain.alarm.MissedAlarmNotifier
import com.antbtv.balarm.core.model.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

/**
 * «Пропущен будильник» после перезагрузки/смены времени (FR-REL-6). Тот же тег и id, что у автостопа:
 * по одному уведомлению на будильник, следующий звонок его снимает. Без `POST_NOTIFICATIONS` система
 * молча ничего не покажет — о праве сообщает баннер здоровья.
 */
class NotificationMissedAlarmNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifications: AlarmNotifications,
) : MissedAlarmNotifier {
    override fun onMissed(alarm: Alarm, missedAt: Instant) {
        context.getSystemService(NotificationManager::class.java).notify(
            AlarmNotifications.missedTag(alarm.id),
            AlarmNotifications.MISSED_ID,
            notifications.missed(alarm.time, alarm.label),
        )
    }
}
