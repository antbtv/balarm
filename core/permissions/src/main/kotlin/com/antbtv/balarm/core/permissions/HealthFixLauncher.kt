package com.antbtv.balarm.core.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.antbtv.balarm.core.alarm.notification.AlarmNotificationChannels
import com.antbtv.balarm.core.domain.health.HealthItem

/**
 * Действие «Исправить» (ADR-012 §4): runtime-запрос уведомлений или нужный экран системных настроек.
 * [onReturned] вызывается при возврате (результат запроса или закрытие экрана настроек) — экран перечитывает статусы.
 * [HealthItem.SCHEDULING] здесь не обрабатывается: «Повторить планирование» делает ViewModel.
 */
@Composable
fun rememberHealthFixLauncher(onReturned: () -> Unit): (HealthItem) -> Unit {
    val context = LocalContext.current
    val currentOnReturned by rememberUpdatedState(onReturned)
    // Запрос уже не показывает диалог (отказы «навсегда») → дальше только экран настроек.
    var notificationsDenied by rememberSaveable { mutableStateOf(false) }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        currentOnReturned()
    }
    fun openSettings(item: HealthItem) {
        val appNotifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val intents = healthFixIntents(item, context.packageName, channelOnly = appNotifications)
        if (!launchFirst(intents, settings::launch)) currentOnReturned()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context.findActivity()
        val permanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        if (!granted) notificationsDenied = true
        // Диалога не было (или он больше не покажется): сразу ведём в настройки, без «мёртвого» нажатия.
        if (permanentlyDenied) openSettings(HealthItem.NOTIFICATIONS) else currentOnReturned()
    }
    return remember(context) {
        { item ->
            val appNotifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (item == HealthItem.NOTIFICATIONS && !appNotifications && !notificationsDenied) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openSettings(item)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
