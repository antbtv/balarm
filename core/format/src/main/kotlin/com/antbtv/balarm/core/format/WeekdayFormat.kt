package com.antbtv.balarm.core.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.text.util.LocalePreferences
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Дни недели для чипов и карточек: порядок — от первого дня недели пользователя (Android 14+ учитывает
 * «Региональные настройки») или от явной настройки приложения (ADR-021 п. 8), имена — по локали.
 * Только отображение: на расписание порядок не влияет.
 */
@Immutable
class WeekdayFormat(val locale: Locale, val firstDay: DayOfWeek) {
    /** Все семь дней, начиная с [firstDay]. */
    val days: List<DayOfWeek> = List(DayOfWeek.entries.size) { firstDay.plus(it.toLong()) }

    /** «пн» — подпись чипа. */
    fun shortName(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale)

    /** «понедельник» — для TalkBack. */
    fun fullName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL_STANDALONE, locale)

    companion object {
        /**
         * @param firstDayOverride первый день из настроек приложения (`AppSettings.firstDayOfWeek`); `null` — по
         * локали.
         */
        fun forLocale(locale: Locale, firstDayOverride: DayOfWeek? = null): WeekdayFormat =
            WeekdayFormat(locale, firstDayOverride ?: firstDayOf(locale))

        private fun firstDayOf(locale: Locale): DayOfWeek = runCatching {
            when (LocalePreferences.getFirstDayOfWeek(locale)) {
                LocalePreferences.FirstDayOfWeek.MONDAY -> DayOfWeek.MONDAY
                LocalePreferences.FirstDayOfWeek.TUESDAY -> DayOfWeek.TUESDAY
                LocalePreferences.FirstDayOfWeek.WEDNESDAY -> DayOfWeek.WEDNESDAY
                LocalePreferences.FirstDayOfWeek.THURSDAY -> DayOfWeek.THURSDAY
                LocalePreferences.FirstDayOfWeek.FRIDAY -> DayOfWeek.FRIDAY
                LocalePreferences.FirstDayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
                LocalePreferences.FirstDayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
                else -> WeekFields.of(locale).firstDayOfWeek
            }
        }.getOrElse { WeekFields.of(locale).firstDayOfWeek }
    }
}

/** Формат по текущей локали; [firstDayOverride] — первый день из настроек приложения, `null` — по локали. */
@Composable
fun rememberWeekdayFormat(firstDayOverride: DayOfWeek? = null): WeekdayFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, firstDayOverride) { WeekdayFormat.forLocale(locale, firstDayOverride) }
}
