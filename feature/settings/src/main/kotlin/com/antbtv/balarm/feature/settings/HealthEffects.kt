package com.antbtv.balarm.feature.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.antbtv.balarm.core.format.ClockFormat
import com.antbtv.balarm.core.permissions.rememberHealthFixLauncher
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Единственный сборщик эффектов экрана здоровья: `receiveAsFlow` делит элементы между сборщиками, второй
 * «украл» бы эффекты. `OpenFix` открывает системные настройки (`rememberHealthFixLauncher`, после возврата —
 * [HealthEvent.Resumed]); остальное — тосты через `applicationContext` (переживают уход с экрана).
 */
@Composable
internal fun HealthEffectsHandler(
    effects: Flow<HealthEffect>,
    clockFormat: ClockFormat,
    onEvent: (HealthEvent) -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentClockFormat by rememberUpdatedState(clockFormat)
    val fix = rememberHealthFixLauncher { currentOnEvent(HealthEvent.Resumed) }
    val currentFix by rememberUpdatedState(fix)
    LaunchedEffect(effects, appContext) {
        // Новый тост сменяет предыдущий сразу, а не встаёт в очередь.
        var last: Toast? = null
        effects.collect { effect ->
            if (effect is HealthEffect.OpenFix) {
                currentFix(effect.item)
                return@collect
            }
            val text = healthEffectText(appContext, effect, currentClockFormat, ZoneId.systemDefault())
                ?: return@collect
            last?.cancel()
            last = Toast.makeText(appContext, text, Toast.LENGTH_LONG).also(Toast::show)
        }
    }
}

/** Текст тоста для эффекта; `null` — эффект без тоста ([HealthEffect.OpenFix]). Язык — из [context]. */
internal fun healthEffectText(context: Context, effect: HealthEffect, clockFormat: ClockFormat, zone: ZoneId): String? =
    when (effect) {
        is HealthEffect.OpenFix -> null

        is HealthEffect.TestScheduled -> effect.at?.let {
            context.getString(R.string.health_test_scheduled, clockFormat.time(LocalDateTime.ofInstant(it, zone)))
        } ?: context.getString(R.string.health_test_failed)

        HealthEffect.RetryFailed -> context.getString(R.string.health_retry_failed)

        HealthEffect.SaveFailed -> context.getString(R.string.health_save_failed)
    }
