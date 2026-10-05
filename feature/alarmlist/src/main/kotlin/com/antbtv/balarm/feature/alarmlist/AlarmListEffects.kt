package com.antbtv.balarm.feature.alarmlist

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.antbtv.balarm.core.format.alarmRingsInText
import kotlinx.coroutines.flow.Flow

/**
 * Единственный сборщик эффектов списка: `receiveAsFlow` делит элементы между сборщиками, второй «украл» бы тосты.
 * Тост — через `applicationContext`: переживает уход с экрана и не держит Activity.
 */
@Composable
internal fun AlarmListEffectsHandler(effects: Flow<AlarmListEffect>) {
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(effects, appContext) {
        // Новый тост сменяет предыдущий сразу, а не встаёт в очередь (серия тапов по тумблеру).
        // При уходе с экрана не отменяем: «зазвонит через …» должен дочитаться.
        var last: Toast? = null
        effects.collect { effect ->
            last?.cancel()
            last = Toast.makeText(appContext, alarmListEffectText(appContext, effect), Toast.LENGTH_SHORT)
                .also(Toast::show)
        }
    }
}

/** Текст тоста для эффекта; язык — из конфигурации [context]. */
internal fun alarmListEffectText(context: Context, effect: AlarmListEffect): String = when (effect) {
    is AlarmListEffect.RingsIn -> alarmRingsInText(context, effect.until)
    AlarmListEffect.ScheduleFailed -> context.getString(R.string.alarm_list_schedule_failed)
    AlarmListEffect.DisableFailed -> context.getString(R.string.alarm_list_disable_failed)
    AlarmListEffect.DeleteFailed -> context.getString(R.string.alarm_list_delete_failed)
}
