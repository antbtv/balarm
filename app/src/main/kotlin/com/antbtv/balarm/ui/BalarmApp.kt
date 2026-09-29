package com.antbtv.balarm.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.Clock
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Корневой composable приложения. В M0 — только экран-заглушка; навигация появится в M2.
 *
 * @param clock источник времени (CLAUDE.md: время — через инжектируемый [Clock]).
 */
@Composable
fun BalarmApp(modifier: Modifier = Modifier, clock: Clock = Clock.systemDefaultZone()) {
    val context = LocalContext.current
    // minSdk 26: LocaleList всегда есть; чтение через LocalConfiguration наблюдаемо при смене языка.
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    // Статично: время фиксируется при первой композиции / смене локали или формата, без тиканья.
    val time = remember(clock, locale, is24Hour) {
        val skeleton = if (is24Hour) SKELETON_24H else SKELETON_12H
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        LocalTime.now(clock).format(DateTimeFormatter.ofPattern(pattern, locale))
    }
    PlaceholderScreen(time = time, modifier = modifier)
}

private const val SKELETON_24H = "Hm"
private const val SKELETON_12H = "hm"
