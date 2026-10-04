package com.antbtv.balarm.core.format

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Время для карточки: [text] крупно, [amPm] — маркер 12-часового формата (`null` в 24-часовом). Маркер рисуется
 * после цифр во всех локалях (продуктовое решение: в ko/ja/zh системный порядок «PM 6:05» не повторяется).
 */
@Immutable
data class TimeParts(val text: String, val amPm: String?)

/**
 * Форматы времени и даты по локали и системной настройке 12/24 ч (скелеты ICU «Hm»/«hm», «EEEEdMMMM»).
 * Единственная реализация для уведомлений, экрана звонка, списка и редактора (ADR-011 §6).
 */
@Immutable
data class ClockFormat(val locale: Locale, val is24Hour: Boolean) {
    private val timeFormatter: DateTimeFormatter by lazy {
        val skeleton = if (is24Hour) SKELETON_24H else SKELETON_12H
        localizedFormatter(locale, skeleton, DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }
    private val dateFormatter: DateTimeFormatter by lazy {
        localizedFormatter(locale, SKELETON_DATE, DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }
    private val digitsFormatter: DateTimeFormatter? by lazy { digitsOnlyFormatter(locale) }
    private val amPmFormatter: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern("a", locale) }

    fun time(value: LocalTime): String = timeFormatter.format(value)

    fun time(value: LocalDateTime): String = time(value.toLocalTime())

    /** «понедельник, 5 октября». */
    fun date(value: LocalDate): String = dateFormatter.format(value)

    fun date(value: LocalDateTime): String = date(value.toLocalDate())

    /** Время и AM/PM раздельно: в 12-часовом формате маркер рисуется мельче рядом с цифрами. */
    fun timeParts(value: LocalTime): TimeParts {
        val digits = digitsFormatter
        return if (is24Hour || digits == null) {
            TimeParts(time(value), amPm = null)
        } else {
            TimeParts(digits.format(value), amPm = amPmFormatter.format(value))
        }
    }

    companion object {
        private const val SKELETON_24H = "Hm"
        private const val SKELETON_12H = "hm"
        private const val SKELETON_DATE = "EEEEdMMMM"

        /** Язык — из конфигурации контекста (с учётом языка приложения), 12/24 ч — из системных настроек. */
        fun from(context: Context): ClockFormat =
            ClockFormat(context.resources.configuration.locales[0], DateFormat.is24HourFormat(context))
    }
}

/**
 * Шаблон ICU по скелету → `java.time`. ICU-шаблон некоторых локалей может содержать символы, которых нет
 * в `java.time`, — тогда стандартный формат локали: экран и уведомление звонка не должны падать из-за языка.
 */
internal fun localizedFormatter(locale: Locale, skeleton: String, fallback: DateTimeFormatter): DateTimeFormatter =
    runCatching { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale) }
        .getOrElse { fallback.withLocale(locale) }

/** 12-часовой шаблон локали без маркера AM/PM («h:mm a» → «h:mm»); `null`, если выделить цифры не удалось. */
private fun digitsOnlyFormatter(locale: Locale): DateTimeFormatter? = runCatching {
    val pattern = DateFormat.getBestDateTimePattern(locale, "hm")
    val digits = stripAmPm(pattern)
    if (digits.isEmpty() || digits == pattern) null else DateTimeFormatter.ofPattern(digits, locale)
}.getOrNull()

/** Убирает букву `a` вне кавычек ICU-шаблона (буквы внутри `'...'` — литералы) вместе с окружающими пробелами. */
internal fun stripAmPm(pattern: String): String {
    val marked = StringBuilder()
    var quoted = false
    pattern.forEach { ch ->
        if (ch == '\'') quoted = !quoted
        marked.append(if (ch == 'a' && !quoted) MARKER else ch)
    }
    return marked.toString().replace(MARKER_WITH_SPACES, "").trim()
}

private const val MARKER = '\u0000'
private val MARKER_WITH_SPACES = Regex("[\\s\u00A0\u202F]*\u0000+[\\s\u00A0\u202F]*")
