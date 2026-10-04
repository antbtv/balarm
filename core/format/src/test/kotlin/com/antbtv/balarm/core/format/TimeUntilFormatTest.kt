package com.antbtv.balarm.core.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith

/** Литералы — для ICU Robolectric SDK 37; на других версиях Android разделители могут отличаться. */
@RunWith(AndroidJUnit4::class)
class TimeUntilFormatTest {

    private val ru = Locale.forLanguageTag("ru-RU")

    @Test
    fun `russian short form`() {
        assertThat(formatTimeUntil(TimeUntil(0, 7, 12), ru)).isEqualTo("7 ч 12 мин")
    }

    @Test
    fun `english short form`() {
        assertThat(formatTimeUntil(TimeUntil(0, 7, 12), Locale.US)).isEqualTo("7 hr, 12 min")
    }

    @Test
    fun `zero parts are omitted`() {
        assertThat(formatTimeUntil(TimeUntil(0, 0, 1), ru)).isEqualTo("1 мин")
        assertThat(formatTimeUntil(TimeUntil(0, 7, 0), ru)).isEqualTo("7 ч")
        assertThat(formatTimeUntil(TimeUntil(2, 3, 5), ru)).isEqualTo("2 дн. 3 ч 5 мин")
    }

    @Test
    fun `wide form for screen readers uses full words with correct russian cases`() {
        assertThat(formatTimeUntil(TimeUntil(0, 7, 12), ru, wide = true)).isEqualTo("7 часов 12 минут")
        assertThat(formatTimeUntil(TimeUntil(0, 1, 1), ru, wide = true)).isEqualTo("1 час 1 минута")
        assertThat(formatTimeUntil(TimeUntil(0, 2, 2), ru, wide = true)).isEqualTo("2 часа 2 минуты")
    }

    @Test
    fun `rings in text wraps the duration in the app language`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(alarmRingsInText(context, TimeUntil(0, 7, 12))).isEqualTo("Alarm rings in 7 hr, 12 min")
    }
}
