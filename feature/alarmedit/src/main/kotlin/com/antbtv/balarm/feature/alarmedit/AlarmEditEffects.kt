package com.antbtv.balarm.feature.alarmedit

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.antbtv.balarm.core.domain.alarm.TestAlarmRunner
import com.antbtv.balarm.core.format.alarmRingsInText
import kotlinx.coroutines.flow.Flow

/**
 * Единственный сборщик эффектов редактора: `receiveAsFlow` делит элементы между сборщиками, второй «украл» бы
 * тост или закрытие. Тост — через `applicationContext`: «зазвонит через …» дочитывается после закрытия экрана
 * и не держит Activity. Новый тост сменяет предыдущий сразу, а не встаёт в очередь.
 *
 * [onClose] вызывается не больше одного раза (ViewModel и так шлёт один `Close`/`Saved`, здесь — страховка):
 * второй `removeLastOrNull()` снял бы со стека ещё и список.
 */
@Composable
internal fun AlarmEditEffectsHandler(effects: Flow<AlarmEditEffect>, onClose: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(effects, appContext) {
        var last: Toast? = null
        var closed = false
        effects.collect { effect ->
            alarmEditEffectText(appContext, effect)?.let { text ->
                last?.cancel()
                last = Toast.makeText(appContext, text, Toast.LENGTH_SHORT).also(Toast::show)
            }
            if (effect.closesScreen && !closed) {
                closed = true
                currentOnClose()
            }
        }
    }
}

/** Эффект закрывает редактор: сохранено, удалено/закрыто, не удалось загрузить. */
internal val AlarmEditEffect.closesScreen: Boolean
    get() = this is AlarmEditEffect.Saved || this == AlarmEditEffect.Close || this == AlarmEditEffect.LoadFailed

/** Текст тоста для эффекта (`null` — без тоста); язык — из конфигурации [context]. */
internal fun alarmEditEffectText(context: Context, effect: AlarmEditEffect): String? = when (effect) {
    is AlarmEditEffect.Saved ->
        effect.until?.let { alarmRingsInText(context, it) } ?: context.getString(R.string.alarm_edit_schedule_failed)

    is AlarmEditEffect.TestScheduled -> if (effect.at != null) {
        // Текст — о задержке, которую ставит ViewModel (TestAlarmRunner.EDITOR_DELAY), а не о пересчёте `at`:
        // разница в доли секунды дала бы «через 4 секунды».
        val seconds = TestAlarmRunner.EDITOR_DELAY.seconds.toInt()
        context.resources.getQuantityString(R.plurals.alarm_edit_test_in, seconds, seconds)
    } else {
        context.getString(R.string.alarm_edit_test_failed)
    }

    AlarmEditEffect.SaveFailed -> context.getString(R.string.alarm_edit_save_failed)

    AlarmEditEffect.DeleteFailed -> context.getString(R.string.alarm_edit_delete_failed)

    AlarmEditEffect.LoadFailed -> context.getString(R.string.alarm_edit_load_failed)

    AlarmEditEffect.Close -> null
}
