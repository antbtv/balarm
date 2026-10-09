package com.antbtv.balarm.feature.alarmedit

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.format.ClockFormat
import com.antbtv.balarm.core.format.WeekdayFormat
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SnoozeSettings
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.util.Locale

// Превью-only: метки в данных не локализуются. Тёмная тема — основная (светлая — бэклог, PRD §2),
// одно светлое превью — контроль, что экран не ломается.

private val PreviewExisting = Alarm(
    id = AlarmId(1),
    time = LocalTime.of(6, 30),
    repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
    label = "Workout",
)

private val PreviewNew = Alarm(time = LocalTime.of(7, 0))

private val ExistingState = AlarmEditUiState(
    loading = false,
    isNew = false,
    initial = PreviewExisting,
    draft = PreviewExisting,
    snoozeVisible = true,
)

private val NewState = AlarmEditUiState(
    loading = false,
    isNew = true,
    initial = PreviewNew,
    draft = PreviewNew,
    snoozeVisible = true,
)

private val PreviewWithMarimba = PreviewExisting.copy(
    sound = SoundSettings(SoundRef.Builtin(BuiltinSound.MARIMBA), volumePercent = 60, fadeIn = Duration.ofSeconds(30)),
)

private val SoundState = ExistingState.copy(
    soundVisible = true,
    customTitles = emptyMap(),
    initial = PreviewWithMarimba,
    draft = PreviewWithMarimba,
)

private val PreviewCustomId = CustomSoundId(7)

private val CustomSoundState = SoundState.copy(
    customTitles = mapOf(PreviewCustomId to "Morning birds in the forest near the lake"),
    draft = PreviewExisting.copy(
        sound = SoundSettings(SoundRef.Custom(PreviewCustomId), volumePercent = 100, fadeIn = Duration.ofSeconds(60)),
    ),
)

private val MissingSoundState = CustomSoundState.copy(
    customTitles = emptyMap(),
    draft = PreviewExisting.copy(sound = SoundSettings(SoundRef.Custom(PreviewCustomId)), vibrate = false),
)

private val SnoozeOffState = ExistingState.copy(draft = PreviewExisting.copy(snooze = SnoozeSettings.DISABLED))

/** Отступы «как у телефона» — статус-бар и панель жестов (в превью их нет). */
private val PreviewBarsInsets = WindowInsets(top = 24.dp, bottom = 24.dp)

private val Russian: Locale = Locale.forLanguageTag("ru-RU")

@Composable
private fun PreviewScreen(state: AlarmEditUiState, is24Hour: Boolean = true, locale: Locale = Locale.US) {
    AlarmEditScreen(
        state = state,
        clockFormat = ClockFormat(locale, is24Hour),
        weekdayFormat = WeekdayFormat.forLocale(locale),
        onEvent = {},
        windowInsets = PreviewBarsInsets,
    )
}

@Preview(name = "Existing — dark, 360dp", widthDp = 360, heightDp = 1000)
@Composable
private fun ExistingPreview() {
    BalarmTheme { PreviewScreen(ExistingState) }
}

@Preview(name = "New — dark, 360dp", widthDp = 360, heightDp = 1000)
@Composable
private fun NewPreview() {
    BalarmTheme { PreviewScreen(NewState) }
}

@Preview(name = "Existing — 12h", widthDp = 360, heightDp = 1000)
@Composable
private fun TwelveHourPreview() {
    BalarmTheme { PreviewScreen(ExistingState, is24Hour = false) }
}

@Preview(name = "Existing — RU", widthDp = 360, heightDp = 1000, locale = "ru")
@Composable
private fun RussianPreview() {
    BalarmTheme { PreviewScreen(ExistingState, locale = Russian) }
}

@Preview(name = "Snooze off — dark", widthDp = 360, heightDp = 1000)
@Composable
private fun SnoozeOffPreview() {
    BalarmTheme { PreviewScreen(SnoozeOffState) }
}

@Preview(name = "Snooze hidden by flag — dark", widthDp = 360, heightDp = 900)
@Composable
private fun SnoozeHiddenPreview() {
    BalarmTheme { PreviewScreen(NewState.copy(snoozeVisible = false)) }
}

@Preview(name = "Existing — fontScale 2, 360dp (viewport)", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun LargeFontPreview() {
    BalarmTheme { PreviewScreen(ExistingState) }
}

@Preview(
    name = "Existing — RU, fontScale 2, full height",
    widthDp = 360,
    heightDp = 1800,
    fontScale = 2f,
    locale = "ru",
)
@Composable
private fun LargeFontFullPreview() {
    BalarmTheme { PreviewScreen(ExistingState, locale = Russian) }
}

@Preview(name = "Loading — dark", widthDp = 360, heightDp = 640)
@Composable
private fun LoadingPreview() {
    BalarmTheme { PreviewScreen(ExistingState.copy(loading = true)) }
}

@Preview(name = "Saving — buttons disabled", widthDp = 360, heightDp = 1000)
@Composable
private fun SavingPreview() {
    BalarmTheme { PreviewScreen(ExistingState.copy(saving = true)) }
}

@Preview(name = "Existing — light", widthDp = 360, heightDp = 1000)
@Composable
private fun LightPreview() {
    BalarmTheme(darkTheme = false) { PreviewScreen(ExistingState) }
}

@Preview(name = "Dialog — snooze interval", widthDp = 360, heightDp = 720)
@Composable
private fun IntervalDialogPreview() {
    BalarmTheme { PreviewScreen(ExistingState.copy(dialog = EditDialog.SnoozeInterval)) }
}

@Preview(
    name = "Dialog — snooze limit, RU, fontScale 2",
    widthDp = 360,
    heightDp = 720,
    fontScale = 2f,
    locale = "ru",
)
@Composable
private fun LimitDialogPreview() {
    BalarmTheme { PreviewScreen(ExistingState.copy(dialog = EditDialog.SnoozeLimit), locale = Russian) }
}

@Preview(name = "Dialog — delete", widthDp = 360, heightDp = 720)
@Composable
private fun DeleteDialogPreview() {
    BalarmTheme { PreviewScreen(ExistingState.copy(dialog = EditDialog.ConfirmDelete)) }
}

@Preview(name = "Dialog — discard changes, RU", widthDp = 360, heightDp = 720, locale = "ru")
@Composable
private fun DiscardDialogPreview() {
    val edited = PreviewExisting.copy(time = LocalTime.of(7, 45))
    BalarmTheme {
        PreviewScreen(ExistingState.copy(draft = edited, dialog = EditDialog.ConfirmDiscard), locale = Russian)
    }
}

@Preview(name = "Sound — builtin, dark, 360dp", widthDp = 360, heightDp = 1400)
@Composable
private fun SoundPreview() {
    BalarmTheme { PreviewScreen(SoundState) }
}

@Preview(name = "Sound — custom, RU", widthDp = 360, heightDp = 1400, locale = "ru")
@Composable
private fun SoundCustomRussianPreview() {
    BalarmTheme { PreviewScreen(CustomSoundState, locale = Russian) }
}

@Preview(name = "Sound — deleted ringtone, dark", widthDp = 360, heightDp = 1400)
@Composable
private fun SoundMissingPreview() {
    BalarmTheme { PreviewScreen(MissingSoundState) }
}

@Preview(name = "Sound — library loading", widthDp = 360, heightDp = 1400)
@Composable
private fun SoundPendingPreview() {
    BalarmTheme { PreviewScreen(MissingSoundState.copy(customTitles = null)) }
}

@Preview(
    name = "Sound — RU, fontScale 2, full height",
    widthDp = 360,
    heightDp = 2600,
    fontScale = 2f,
    locale = "ru",
)
@Composable
private fun SoundLargeFontPreview() {
    BalarmTheme { PreviewScreen(MissingSoundState, locale = Russian) }
}

@Preview(name = "Sound — light", widthDp = 360, heightDp = 1400)
@Composable
private fun SoundLightPreview() {
    BalarmTheme(darkTheme = false) { PreviewScreen(CustomSoundState) }
}

@Preview(
    name = "Dialog — fade in, RU, fontScale 2",
    widthDp = 360,
    heightDp = 720,
    fontScale = 2f,
    locale = "ru",
)
@Composable
private fun FadeInDialogPreview() {
    BalarmTheme { PreviewScreen(SoundState.copy(dialog = EditDialog.FadeIn), locale = Russian) }
}
