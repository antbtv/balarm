package com.antbtv.balarm.core.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockFormatTest {

    private val morning = LocalTime.of(6, 30)
    private val evening = LocalTime.of(18, 5)

    @Test
    fun `24 hour format has no am pm marker`() {
        val format = ClockFormat(Locale.US, is24Hour = true)

        assertThat(format.time(morning)).isEqualTo("06:30")
        assertThat(format.timeParts(evening)).isEqualTo(TimeParts("18:05", amPm = null))
    }

    @Test
    fun `russian 24 hour format`() {
        assertThat(ClockFormat(Locale.forLanguageTag("ru-RU"), is24Hour = true).time(evening)).isEqualTo("18:05")
    }

    @Test
    fun `12 hour format splits digits and am pm`() {
        val format = ClockFormat(Locale.US, is24Hour = false)

        val parts = format.timeParts(evening)

        assertThat(parts.text).isEqualTo("6:05")
        assertThat(parts.amPm).isEqualTo("PM")
        assertThat(format.timeParts(morning).amPm).isEqualTo("AM")
        assertThat(format.time(evening)).contains("6:05")
        assertThat(format.time(evening)).contains("PM")
    }

    @Test
    fun `12 hour midnight and noon read as twelve`() {
        val format = ClockFormat(Locale.US, is24Hour = false)

        assertThat(format.timeParts(LocalTime.MIDNIGHT)).isEqualTo(TimeParts("12:00", "AM"))
        assertThat(format.timeParts(LocalTime.NOON)).isEqualTo(TimeParts("12:00", "PM"))
    }

    @Test
    fun `date reads weekday and month by locale`() {
        val saturday = LocalDate.of(2026, 10, 3)

        assertThat(ClockFormat(Locale.US, true).date(saturday)).contains("Saturday")
        assertThat(ClockFormat(Locale.forLanguageTag("ru-RU"), true).date(saturday)).contains("суббота")
    }

    @Test
    fun `local date time is accepted as well`() {
        val moment = LocalDateTime.of(2026, 10, 3, 6, 30)

        assertThat(ClockFormat(Locale.US, true).time(moment)).isEqualTo("06:30")
        assertThat(ClockFormat(Locale.US, true).date(moment)).isNotEmpty()
    }

    @Test
    fun `every locale splits the 12 hour time into digits and a marker that make up the full time`() {
        Locale.getAvailableLocales().forEach { locale ->
            val format = ClockFormat(locale, is24Hour = false)
            val parts = format.timeParts(evening)
            val full = format.time(evening)

            assertWithMessage("$locale text").that(parts.text).isNotEmpty()
            if (parts.amPm != null) {
                assertWithMessage("$locale marker not in digits").that(parts.text).doesNotContain(parts.amPm)
                assertWithMessage("$locale full has marker").that(full).contains(parts.amPm)
            }
            assertWithMessage("$locale full has digits").that(full).contains(parts.text)
        }
    }

    @Test
    fun `am pm is stripped only outside of quoted literals`() {
        assertThat(stripAmPm("h:mm a")).isEqualTo("h:mm")
        assertThat(stripAmPm("a h:mm")).isEqualTo("h:mm")
        assertThat(stripAmPm("h:mm\u202Fa")).isEqualTo("h:mm")
        assertThat(stripAmPm("h 'a' mm a")).isEqualTo("h 'a' mm")
        assertThat(stripAmPm("HH:mm")).isEqualTo("HH:mm")
    }
}
