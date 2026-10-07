package com.antbtv.balarm.core.permissions

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.antbtv.balarm.core.alarm.notification.AlarmNotificationChannels
import com.antbtv.balarm.core.domain.health.PermissionHealthChecker
import com.antbtv.balarm.core.domain.health.PermissionSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Читает статусы разрешений у платформы (ADR-012 §3). Синхронно и дёшево: несколько binder-вызовов,
 * вызывается на каждый `ON_RESUME`.
 */
class AndroidPermissionHealthChecker @Inject constructor(@ApplicationContext private val context: Context) :
    PermissionHealthChecker {

    override fun snapshot(): PermissionSnapshot {
        val notifications = context.requireService<NotificationManager>()
        val power = context.requireService<PowerManager>()
        val audio = context.requireService<AudioManager>()
        return PermissionSnapshot(
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            ringingChannelEnabled = ringingChannelEnabled(notifications),
            exactAlarms = context.requireService<AlarmManager>().canScheduleExactAlarms(),
            fullScreenIntent = notifications.canUseFullScreenIntent(),
            overlay = Settings.canDrawOverlays(context),
            ignoringBatteryOptimizations = power.isIgnoringBatteryOptimizations(context.packageName),
            backgroundRestricted = context.requireService<ActivityManager>().isBackgroundRestricted,
            alarmsAllowedByDnd = alarmsAllowedByDnd(notifications),
            alarmVolumeMuted = audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0,
        )
    }

    /** Канал звонка нужен с важностью HIGH: иначе нет full-screen intent и экрана звонка (ADR-007 §8). */
    private fun ringingChannelEnabled(notifications: NotificationManager): Boolean {
        AlarmNotificationChannels.ensureCreated(context)
        val channel = notifications.getNotificationChannel(AlarmNotificationChannels.RINGING)
        return channel != null && channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    /** «Полная тишина» глушит будильники; «Только важные» — пока в политике есть категория будильников. */
    private fun alarmsAllowedByDnd(notifications: NotificationManager): Boolean =
        when (notifications.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE -> false

            NotificationManager.INTERRUPTION_FILTER_PRIORITY ->
                notifications.notificationPolicy.priorityCategories and
                    NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS != 0

            else -> true
        }

    private inline fun <reified T : Any> Context.requireService(): T =
        requireNotNull(getSystemService(T::class.java)) { "${T::class.java.simpleName} unavailable" }
}
