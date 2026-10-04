package com.antbtv.balarm.core.format

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect

/**
 * [ClockFormat] для экрана. Язык наблюдаем через конфигурацию; смена 12/24 ч в системных настройках
 * конфигурацию не меняет, поэтому значение перечитывается на каждом `ON_START` (возврат из настроек).
 */
@Composable
fun rememberClockFormat(): ClockFormat {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var is24Hour by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
    LifecycleStartEffect(context) {
        is24Hour = DateFormat.is24HourFormat(context)
        onStopOrDispose { }
    }
    return remember(locale, is24Hour) { ClockFormat(locale, is24Hour) }
}
