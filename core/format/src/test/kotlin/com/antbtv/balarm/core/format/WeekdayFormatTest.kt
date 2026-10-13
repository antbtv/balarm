package com.antbtv.balarm.core.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeekdayFormatTest {

    @Test
    fun `russian week starts on monday`() {
        val format = WeekdayFormat.forLocale(Locale.forLanguageTag("ru-RU"))

        assertThat(format.firstDay).isEqualTo(DayOfWeek.MONDAY)
        assertThat(format.days).containsExactlyElementsIn(DayOfWeek.entries).inOrder()
    }

    @Test
    fun `us week starts on sunday and keeps all seven days`() {
        val format = WeekdayFormat.forLocale(Locale.US)

        assertThat(format.firstDay).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(format.days).hasSize(7)
        assertThat(format.days.first()).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(format.days.last()).isEqualTo(DayOfWeek.SATURDAY)
        assertThat(format.days.toSet()).containsExactlyElementsIn(DayOfWeek.entries)
    }

    @Test
    fun `names follow the locale`() {
        val ru = WeekdayFormat.forLocale(Locale.forLanguageTag("ru-RU"))
        val en = WeekdayFormat.forLocale(Locale.US)

        assertThat(ru.shortName(DayOfWeek.MONDAY)).isEqualTo("пн")
        assertThat(ru.fullName(DayOfWeek.MONDAY)).isEqualTo("понедельник")
        assertThat(en.shortName(DayOfWeek.MONDAY)).isEqualTo("Mon")
        assertThat(en.fullName(DayOfWeek.MONDAY)).isEqualTo("Monday")
    }

    @Test
    fun `every locale resolves a first day without throwing`() {
        Locale.getAvailableLocales().forEach { locale ->
            assertThat(WeekdayFormat.forLocale(locale).days).hasSize(7)
        }
    }

    @Test
    fun `override wins over locale and keeps all seven days`() {
        val ruFromSunday = WeekdayFormat.forLocale(Locale.forLanguageTag("ru-RU"), DayOfWeek.SUNDAY)
        val usFromMonday = WeekdayFormat.forLocale(Locale.US, DayOfWeek.MONDAY)

        assertThat(ruFromSunday.firstDay).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(ruFromSunday.days.first()).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(ruFromSunday.days.last()).isEqualTo(DayOfWeek.SATURDAY)
        assertThat(ruFromSunday.days.toSet()).containsExactlyElementsIn(DayOfWeek.entries)
        assertThat(usFromMonday.days).containsExactlyElementsIn(DayOfWeek.entries).inOrder()
        // Имена — по-прежнему по локали.
        assertThat(ruFromSunday.shortName(DayOfWeek.SUNDAY)).isEqualTo("вс")
    }

    @Test
    fun `any override day starts the week`() {
        DayOfWeek.entries.forEach { day ->
            val format = WeekdayFormat.forLocale(Locale.US, day)
            assertThat(format.days.first()).isEqualTo(day)
            assertThat(format.days.toSet()).hasSize(7)
        }
    }

    @Test
    fun `null override behaves like locale`() {
        listOf(Locale.US, Locale.forLanguageTag("ru-RU"), Locale.forLanguageTag("ar-EG")).forEach { locale ->
            val byLocale = WeekdayFormat.forLocale(locale)
            val withNull = WeekdayFormat.forLocale(locale, null)

            assertThat(withNull.firstDay).isEqualTo(byLocale.firstDay)
            assertThat(withNull.days).isEqualTo(byLocale.days)
        }
    }
}
