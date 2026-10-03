package com.antbtv.balarm.core.alarm.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import com.antbtv.balarm.core.alarm.AlarmUiIntents
import com.antbtv.balarm.core.alarm.R
import com.antbtv.balarm.core.domain.alarm.RingingPolicy
import com.antbtv.balarm.core.model.AlarmId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Уведомления звонка (ADR-007 §2.1, §6). Экран звонка открывается full-screen intent'ом; без
 * `POST_NOTIFICATIONS` уведомление не видно, но FGS и звук работают.
 */
@Singleton
class AlarmNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uiIntents: AlarmUiIntents,
) {
    /**
     * Уведомление FGS звонка. Сначала показывается без [time] (сервис стартует до чтения БД),
     * потом обновляется с временем и [label].
     */
    fun ringing(time: LocalTime? = null, label: String = "", actions: RingingActions? = null): Notification {
        AlarmNotificationChannels.ensureCreated(context)
        val screen = activityIntent(uiIntents.ringingScreen())
        return NotificationCompat.Builder(context, AlarmNotificationChannels.RINGING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(label.ifBlank { context.getString(R.string.alarm_notification_title) })
            .setContentText(time?.let(::format) ?: context.getString(R.string.alarm_notification_ringing))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true) // обновление метки не должно повторно поднимать heads-up
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .apply {
                actions?.snooze?.let { addAction(0, context.getString(R.string.alarm_notification_snooze), it) }
                actions?.dismiss?.let { addAction(0, context.getString(R.string.alarm_notification_dismiss), it) }
            }
            .build()
    }

    /**
     * Звонок без сервиса: звук и повтор — у системы (канал [AlarmNotificationChannels.FALLBACK]).
     * Останавливается смахиванием, нажатием или автостопом [RingingPolicy.AUTO_STOP_AFTER] (FR-RING-6):
     * экрана звонка нет — сервис, который им управляет, не запустился.
     */
    fun fallback(): Notification {
        AlarmNotificationChannels.ensureCreated(context)
        val list = activityIntent(uiIntents.alarmList())
        return NotificationCompat.Builder(context, AlarmNotificationChannels.FALLBACK)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.alarm_notification_title))
            .setContentText(context.getString(R.string.alarm_notification_fallback_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(RingingPolicy.AUTO_STOP_AFTER.toMillis())
            .setFullScreenIntent(list, true)
            .setContentIntent(list)
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }
    }

    /** Будильник замолчал по автостопу без реакции пользователя (FR-RING-6). */
    fun missed(time: LocalTime, label: String): Notification {
        AlarmNotificationChannels.ensureCreated(context)
        return NotificationCompat.Builder(context, AlarmNotificationChannels.MISSED)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.alarm_notification_missed_title))
            .setContentText(listOf(format(time), label).filter { it.isNotBlank() }.joinToString(" · "))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(activityIntent(uiIntents.alarmList()))
            .build()
    }

    private fun activityIntent(intent: Intent): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun format(time: LocalTime): String {
        val locale = context.resources.configuration.locales[0] // с учётом языка приложения
        val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
        // ICU-шаблон может не подойти java.time в редких локалях — тогда стандартный формат, без падения.
        val formatter = runCatching {
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        }
            .getOrElse { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
        return time.format(formatter)
    }

    companion object {
        const val RINGING_ID = 1
        const val FALLBACK_ID = 2
        const val MISSED_ID = 3

        /** Тег пропущенного: по одному уведомлению на будильник. */
        fun missedTag(id: AlarmId) = "missed:${id.value}"
    }
}
