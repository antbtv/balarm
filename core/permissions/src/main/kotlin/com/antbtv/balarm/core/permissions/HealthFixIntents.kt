package com.antbtv.balarm.core.permissions

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.antbtv.balarm.core.alarm.notification.AlarmNotificationChannels
import com.antbtv.balarm.core.domain.health.HealthItem

/**
 * Куда вести «Исправить» (ADR-012 §4): интенты по убыванию точности, последний — экран приложения в настройках.
 * Пункты без системного экрана ([HealthItem.SCHEDULING]) дают пустой список.
 * [channelOnly] — разрешение на уведомления есть, выключен именно канал звонка.
 */
internal fun healthFixIntents(item: HealthItem, packageName: String, channelOnly: Boolean = false): List<Intent> {
    val packageUri = Uri.fromParts("package", packageName, null)
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    val specific = when (item) {
        HealthItem.NOTIFICATIONS ->
            if (channelOnly) {
                listOf(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmNotificationChannels.RINGING),
                )
            } else {
                listOf(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                )
            }

        HealthItem.EXACT_ALARMS -> listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))

        HealthItem.FULL_SCREEN_INTENT -> listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri))

        HealthItem.OVERLAY -> listOf(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))

        HealthItem.BATTERY_OPTIMIZATION -> listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )

        HealthItem.DO_NOT_DISTURB -> listOf(
            Intent(ACTION_ZEN_MODE_PRIORITY_SETTINGS),
            Intent(Settings.ACTION_SOUND_SETTINGS),
        )

        HealthItem.ALARM_VOLUME -> listOf(Intent(Settings.ACTION_SOUND_SETTINGS))

        HealthItem.BACKGROUND_RESTRICTION, HealthItem.OEM_BACKGROUND -> emptyList()

        HealthItem.SCHEDULING -> return emptyList()
    }
    return specific + appDetails
}

/** Запускает первый интент, который система приняла; `false` — не приняла ни один. */
internal fun launchFirst(intents: List<Intent>, launch: (Intent) -> Unit): Boolean = intents.any { intent ->
    try {
        launch(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Скрытая в SDK константа `Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS`. */
private const val ACTION_ZEN_MODE_PRIORITY_SETTINGS = "android.settings.ZEN_MODE_PRIORITY_SETTINGS"
