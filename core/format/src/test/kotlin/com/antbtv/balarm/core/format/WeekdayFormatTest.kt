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
}
