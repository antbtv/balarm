package com.antbtv.balarm.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.antbtv.balarm.core.format.rememberClockFormat
import java.time.Clock
import java.time.LocalTime

/**
 * Корневой composable приложения. В M0 — только экран-заглушка; навигация появится в M2 (T13).
 *
 * @param clock источник времени (CLAUDE.md: время — через инжектируемый [Clock]).
 */
@Composable
fun BalarmApp(modifier: Modifier = Modifier, clock: Clock = Clock.systemDefaultZone()) {
    val clockFormat = rememberClockFormat()
    // Статично: время фиксируется при первой композиции / смене локали или формата, без тиканья.
    val time = remember(clock, clockFormat) { clockFormat.time(LocalTime.now(clock)) }
    PlaceholderScreen(time = time, modifier = modifier)
}
