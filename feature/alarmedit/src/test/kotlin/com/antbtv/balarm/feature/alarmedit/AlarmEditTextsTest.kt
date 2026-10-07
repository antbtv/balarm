package com.antbtv.balarm.feature.alarmedit

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.DayPreset
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.format.WeekdayFormat
import com.antbtv.balarm.core.format.formatTimeUntil
import com.antbtv.balarm.core.model.SnoozeSettings
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class AlarmEditTextsTest {

    private val resources: Resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `interval options start with off and keep the standard list`() {
        val options = snoozeIntervalOptions(Duration.ofMinutes(5))

        assertThat(options).isEqualTo(listOf(null) + SnoozeSettings.INTERVAL_OPTIONS)
        assertThat(snoozeIntervalOptions(null)).isEqualTo(options)
    }

    @Test
    fun `non-standard interval from the database is added in order`() {
        val options = snoozeIntervalOptions(Duration.ofMinutes(7))

        assertThat(options.map { it?.toMinutes() }).containsExactly(null, 1L, 3L, 5L, 7L, 10L, 15L, 20L, 30L)
            .inOrder()
    }

    @Test
    fun `limit options end with unlimited, non-standard limit is added in order`() {
        assertThat(snoozeLimitOptions(3)).isEqualTo(SnoozeSettings.LIMIT_OPTIONS)
        assertThat(snoozeLimitOptions(null)).isEqualTo(SnoozeSettings.LIMIT_OPTIONS)
        assertThat(snoozeLimitOptions(4)).containsExactly(1, 2, 3, 4, 5, 10, null).inOrder()
    }

    @Test
    fun `interval texts - short for the row, wide for TalkBack`() {
        val five = Duration.ofMinutes(5)

        assertThat(snoozeIntervalText(five, resources, Locale.US, wide = false))
            .isEqualTo(formatTimeUntil(TimeUntil(0, 0, 5), Locale.US))
        assertThat(snoozeIntervalText(five, resources, Locale.US, wide = true)).isEqualTo("5 minutes")
        assertThat(snoozeIntervalText(null, resources, Locale.US, wide = false)).isEqualTo("Off")
        assertThat(snoozeIntervalText(null, resources, Locale.US, wide = true)).isEqualTo("turned off")
    }

    @Test
    fun `limit texts`() {
        assertThat(snoozeLimitText(1, resources)).isEqualTo("1 time")
        assertThat(snoozeLimitText(3, resources)).isEqualTo("3 times")
        assertThat(snoozeLimitText(null, resources)).isEqualTo("Unlimited")
    }

    @Test
    @Config(qualifiers = "ru-rRU")
    fun `russian limit texts`() {
        assertThat(snoozeLimitText(1, resources)).isEqualTo("1 раз")
        assertThat(snoozeLimitText(3, resources)).isEqualTo("3 раза")
        assertThat(snoozeLimitText(5, resources)).isEqualTo("5 раз")
        assertThat(snoozeLimitText(null, resources)).isEqualTo("Без ограничения")
        assertThat(snoozeIntervalText(null, resources, Locale.forLanguageTag("ru-RU"), wide = false)).isEqualTo("Выкл")
    }

    @Test
    fun `day chips follow the week of the locale`() {
        val us = dayChips(setOf(DayOfWeek.MONDAY), WeekdayFormat.forLocale(Locale.US))
        val ru = dayChips(setOf(DayOfWeek.MONDAY), WeekdayFormat.forLocale(Locale.forLanguageTag("ru-RU")))

        assertThat(us.first().day).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(ru.first().day).isEqualTo(DayOfWeek.MONDAY)
        assertThat(us.single { it.selected }.day).isEqualTo(DayOfWeek.MONDAY)
        assertThat(us.first { it.day == DayOfWeek.MONDAY }.description).isEqualTo("Monday")
    }

    @Test
    fun `pressed preset is recognised from the new set of days`() {
        val weekdays = DayPreset.WEEKDAYS.days

        assertThat(pressedPreset(emptySet(), weekdays)).isEqualTo(DayPreset.WEEKDAYS)
        assertThat(pressedPreset(weekdays, emptySet())).isEqualTo(DayPreset.WEEKDAYS)
        assertThat(pressedPreset(weekdays, DayPreset.WEEKEND.days)).isEqualTo(DayPreset.WEEKEND)
        assertThat(pressedPreset(DayPreset.EVERY_DAY.days, emptySet())).isEqualTo(DayPreset.EVERY_DAY)
        assertThat(pressedPreset(emptySet(), setOf(DayOfWeek.MONDAY))).isNull()
    }
}
