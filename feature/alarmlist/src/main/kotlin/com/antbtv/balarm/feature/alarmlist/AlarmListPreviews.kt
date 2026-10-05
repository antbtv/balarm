package com.antbtv.balarm.feature.alarmlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.format.ClockFormat
import com.antbtv.balarm.core.format.WeekdayFormat
import com.antbtv.balarm.core.model.AlarmId
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale

// Превью-only: метки в данных не локализуются. Тёмная тема — основная (светлая — бэклог, PRD §2),
// одно светлое превью — контроль, что экран не ломается.

private val PreviewWeekdays = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

private val PreviewAlarms = listOf(
    AlarmItemUi(
        id = AlarmId(1),
        time = LocalTime.of(6, 30),
        label = "Workout",
        repeatDays = PreviewWeekdays,
        active = true,
        subtitle = AlarmSubtitle.Tomorrow,
    ),
    AlarmItemUi(
        id = AlarmId(2),
        time = LocalTime.of(7, 5),
        label = "",
        repeatDays = emptySet(),
        active = true,
        subtitle = AlarmSubtitle.SnoozedUntil(LocalTime.of(7, 10)),
    ),
    AlarmItemUi(
        id = AlarmId(3),
        time = LocalTime.of(9, 0),
        label = "A very long label that does not fit into a single line, not even into two lines of the card",
        repeatDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        active = false,
        subtitle = null,
    ),
    AlarmItemUi(
        id = AlarmId(4),
        time = LocalTime.of(23, 59),
        label = "Pills",
        repeatDays = DayOfWeek.entries.toSet(),
        active = true,
        subtitle = AlarmSubtitle.Today,
    ),
)

private val PreviewData = AlarmListUiState(
    loading = false,
    alarms = PreviewAlarms,
    nextIn = TimeUntil(days = 0, hours = 7, minutes = 12),
)

private val PreviewEmpty = AlarmListUiState(loading = false)

@Composable
private fun PreviewScreen(state: AlarmListUiState, is24Hour: Boolean = true, locale: Locale = Locale.US) {
    AlarmListScreen(
        state = state,
        clockFormat = ClockFormat(locale, is24Hour),
        weekdayFormat = WeekdayFormat.forLocale(locale),
        onEvent = {},
        onAddAlarm = {},
        onOpenAlarm = {},
    )
}

@Preview(name = "List — dark, 360dp", widthDp = 360, heightDp = 720)
@Composable
private fun AlarmListPreview() {
    BalarmTheme { PreviewScreen(PreviewData) }
}

@Preview(name = "List — dark, 12h", widthDp = 360, heightDp = 720)
@Composable
private fun AlarmList12hPreview() {
    BalarmTheme { PreviewScreen(PreviewData, is24Hour = false) }
}

@Preview(name = "List — dark, fontScale 2, 360dp", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun AlarmListLargeFontPreview() {
    BalarmTheme { PreviewScreen(PreviewData) }
}

@Preview(name = "List — RU", widthDp = 360, heightDp = 720, locale = "ru")
@Composable
private fun AlarmListRuPreview() {
    BalarmTheme { PreviewScreen(PreviewData, locale = Locale.forLanguageTag("ru-RU")) }
}

@Preview(name = "List — light", widthDp = 360, heightDp = 720)
@Composable
private fun AlarmListLightPreview() {
    BalarmTheme(darkTheme = false) { PreviewScreen(PreviewData) }
}

@Preview(name = "List — system bars, fontScale 2", widthDp = 360, heightDp = 640, fontScale = 2f, showSystemUi = true)
@Composable
private fun AlarmListSystemBarsPreview() {
    BalarmTheme { PreviewScreen(PreviewData) }
}

@Preview(name = "Empty — dark", widthDp = 360, heightDp = 640)
@Composable
private fun AlarmListEmptyPreview() {
    BalarmTheme { PreviewScreen(PreviewEmpty) }
}

@Preview(name = "Empty — RU, fontScale 2", widthDp = 360, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun AlarmListEmptyLargeFontPreview() {
    BalarmTheme { PreviewScreen(PreviewEmpty, locale = Locale.forLanguageTag("ru-RU")) }
}

@Preview(name = "Loading — dark", widthDp = 360, heightDp = 640)
@Composable
private fun AlarmListLoadingPreview() {
    BalarmTheme { PreviewScreen(AlarmListUiState()) }
}

@Preview(name = "Delete dialog — dark", widthDp = 360, heightDp = 640)
@Composable
private fun DeleteDialogPreview() {
    BalarmTheme {
        PreviewScreen(PreviewData)
        DeleteAlarmDialog(
            item = PreviewAlarms.first(),
            clockFormat = ClockFormat(Locale.US, is24Hour = true),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Delete dialog — RU, fontScale 2", widthDp = 360, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun DeleteDialogLargeFontPreview() {
    BalarmTheme {
        DeleteAlarmDialog(
            item = PreviewAlarms.first(),
            clockFormat = ClockFormat(Locale.forLanguageTag("ru-RU"), is24Hour = true),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
