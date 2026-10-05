package com.antbtv.balarm.feature.alarmlist

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.format.ClockFormat
import com.antbtv.balarm.core.format.WeekdayFormat
import com.antbtv.balarm.core.model.AlarmId
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Строки карточки (Robolectric — ради ресурсов; локаль задаёт `qualifiers`). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class AlarmCardTextsTest {

    private val resources: Resources get() = ApplicationProvider.getApplicationContext<Context>().resources
    private val clock24 = ClockFormat(Locale.US, is24Hour = true)
    private val clock12 = ClockFormat(Locale.US, is24Hour = false)
    private val usWeek = WeekdayFormat(Locale.US, DayOfWeek.SUNDAY)
    private val ruWeek = WeekdayFormat(Locale.forLanguageTag("ru-RU"), DayOfWeek.MONDAY)

    private val item = AlarmItemUi(
        id = AlarmId(7),
        time = LocalTime.of(19, 5),
        label = "  ",
        repeatDays = emptySet(),
        active = true,
        subtitle = AlarmSubtitle.Today,
    )

    @Test
    fun `24-hour time has no AM-PM marker`() {
        val texts = alarmCardTexts(item, clock24, usWeek, resources)

        assertThat(texts.time).isEqualTo("19:05")
        assertThat(texts.amPm).isNull()
    }

    @Test
    fun `12-hour time splits digits and marker, TalkBack reads the full time`() {
        val texts = alarmCardTexts(item, clock12, usWeek, resources)

        assertThat(texts.time).isEqualTo("7:05")
        assertThat(texts.amPm).isEqualTo("PM")
        assertThat(texts.toggleDescription).isEqualTo("Alarm ${clock12.time(item.time)}")
    }

    @Test
    fun `blank label is skipped in descriptions`() {
        val texts = alarmCardTexts(item, clock24, usWeek, resources)

        assertThat(texts.contentDescription).isEqualTo("Alarm 19:05, once, Today, on")
        assertThat(texts.toggleDescription).isEqualTo("Alarm 19:05")
    }

    @Test
    fun `subtitles`() {
        assertThat(subtitleText(null, clock24, resources)).isNull()
        assertThat(subtitleText(AlarmSubtitle.Tomorrow, clock24, resources)).isEqualTo("Tomorrow")
        assertThat(subtitleText(AlarmSubtitle.SnoozedUntil(LocalTime.of(7, 10)), clock12, resources))
            .isEqualTo("Snoozed until ${clock12.time(LocalTime.of(7, 10))}")
    }

    @Test
    fun `repeat descriptions`() {
        assertThat(repeatText(emptySet(), usWeek, resources)).isEqualTo("once")
        assertThat(repeatText(DayOfWeek.entries.toSet(), usWeek, resources)).isEqualTo("every day")
        assertThat(
            repeatText(
                setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                usWeek,
                resources,
            ),
        ).isEqualTo("on weekdays")
        assertThat(repeatText(setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY), usWeek, resources)).isEqualTo("on weekends")
        // Порядок — от первого дня недели пользователя, а не порядок в Set.
        assertThat(repeatText(setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY), usWeek, resources))
            .isEqualTo("Monday, Saturday")
        assertThat(repeatText(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), usWeek, resources))
            .isEqualTo("Sunday, Monday")
        assertThat(repeatText(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), ruWeek, resources))
            .isEqualTo("${ruWeek.fullName(DayOfWeek.MONDAY)}, ${ruWeek.fullName(DayOfWeek.SUNDAY)}")
    }

    @Test
    fun `one-shot alarm has no day pills`() {
        assertThat(dayPills(emptySet(), usWeek)).isEmpty()
    }

    @Test
    fun `day pills follow the first day of week and are capitalized`() {
        val pills = dayPills(setOf(DayOfWeek.MONDAY), ruWeek)

        assertThat(pills).hasSize(7)
        assertThat(pills.first().label).isEqualTo("Пн")
        assertThat(pills.first().selected).isTrue()
        assertThat(pills.first().description).isEqualTo(ruWeek.fullName(DayOfWeek.MONDAY))
        assertThat(pills.drop(1).none { it.selected }).isTrue()

        val us = dayPills(setOf(DayOfWeek.MONDAY), usWeek)
        assertThat(us.first().label).isEqualTo(usWeek.shortName(DayOfWeek.SUNDAY))
        assertThat(us[1].selected).isTrue()
    }
}
