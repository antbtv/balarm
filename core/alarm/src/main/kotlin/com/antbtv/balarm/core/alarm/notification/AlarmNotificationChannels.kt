package com.antbtv.balarm.core.alarm.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import com.antbtv.balarm.core.alarm.R
import com.antbtv.balarm.core.alarm.sound.MediaAlarmSoundPlayer

/**
 * Каналы уведомлений будильника (ADR-007). Идентификаторы стабильны: звук и важность канала после
 * создания меняет только пользователь, поэтому новые атрибуты — только с новым id.
 */
object AlarmNotificationChannels {
    /** Уведомление FGS звонка: экран звонка через FSI; звук играет сервис, у канала его нет. */
    const val RINGING = "alarm_ringing"

    /** Сервис не стартовал (ADR-002 §6): звонит сама система — `USAGE_ALARM` + `FLAG_INSISTENT`. */
    const val FALLBACK = "alarm_fallback"

    /** Будильник замолчал по автостопу (FR-RING-6). */
    const val MISSED = "alarm_missed"

    /** Идемпотентно; дёшево вызывать перед каждым показом, в т.ч. до разблокировки. */
    fun ensureCreated(context: Context) {
        val ringing = context.channel(RINGING, HIGH, R.string.alarm_channel_ringing_name).apply {
            description = context.getString(R.string.alarm_channel_ringing_description)
            setSound(null, null)
            enableVibration(false)
        }
        val fallback = context.channel(FALLBACK, HIGH, R.string.alarm_channel_fallback_name).apply {
            description = context.getString(R.string.alarm_channel_fallback_description)
            // Встроенный звук из APK: системный рингтон до разблокировки может быть недоступен (ADR-008 §2).
            setSound(MediaAlarmSoundPlayer.defaultSoundUri(context), MediaAlarmSoundPlayer.ALARM_ATTRIBUTES)
            enableVibration(true)
        }
        val missed = context.channel(MISSED, DEFAULT, R.string.alarm_channel_missed_name).apply {
            description = context.getString(R.string.alarm_channel_missed_description)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(ringing, fallback, missed))
    }

    private const val HIGH = NotificationManager.IMPORTANCE_HIGH
    private const val DEFAULT = NotificationManager.IMPORTANCE_DEFAULT

    private fun Context.channel(id: String, importance: Int, @StringRes name: Int) =
        NotificationChannel(id, getString(name), importance).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
}
