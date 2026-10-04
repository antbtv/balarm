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
 * «Региональные настройки»), имена — по локали.
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
        fun forLocale(locale: Locale): WeekdayFormat = WeekdayFormat(locale, firstDayOf(locale))

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

@Composable
fun rememberWeekdayFormat(): WeekdayFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { WeekdayFormat.forLocale(locale) }
}
