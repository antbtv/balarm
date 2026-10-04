package com.antbtv.balarm.core.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith

/** Экран и уведомление звонка не должны падать из-за языка устройства: ICU-шаблон любой локали → рабочий формат. */
@RunWith(AndroidJUnit4::class)
class LocalizedFormatterTest {

    private val moment = LocalDateTime.of(2026, 10, 3, 6, 30)

    @Test
    fun `every available locale formats time and date`() {
        val locales = Locale.getAvailableLocales().toList() + listOf("ar", "th", "zh-Hant-TW", "hi", "fa", "ja")
            .map(Locale::forLanguageTag)

        locales.forEach { locale ->
            listOf("Hm", "hm").forEach { skeleton ->
                val time = localizedFormatter(locale, skeleton, DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                assertWithMessage("$locale $skeleton").that(moment.format(time)).isNotEmpty()
            }
            val date = localizedFormatter(locale, "EEEEdMMMM", DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
            assertWithMessage("$locale date").that(moment.format(date)).isNotEmpty()
        }
    }
}
