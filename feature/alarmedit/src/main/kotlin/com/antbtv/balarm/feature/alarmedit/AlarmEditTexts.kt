package com.antbtv.balarm.feature.alarmedit

import android.content.res.Resources
import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import com.antbtv.balarm.core.designsystem.component.DayChipUi
import com.antbtv.balarm.core.designsystem.component.DayPreset
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.format.WeekdayFormat
import com.antbtv.balarm.core.format.formatTimeUntil
import com.antbtv.balarm.core.model.SnoozeSettings
import java.time.DayOfWeek
import java.time.Duration
import java.util.Locale

/**
 * Варианты интервала snooze в диалоге: «Выкл» (`null`), затем [SnoozeSettings.INTERVAL_OPTIONS]. Значение из БД
 * вне стандартного списка (будильник из старой версии/бэкапа) добавляется по порядку — иначе диалог открылся бы
 * без выбранного варианта, а текущее значение нельзя было бы оставить.
 */
internal fun snoozeIntervalOptions(current: Duration?): List<Duration?> {
    val standard = SnoozeSettings.INTERVAL_OPTIONS
    val values = if (current == null || current in standard) standard else (standard + current).sorted()
    return listOf(null) + values
}

/**
 * Варианты лимита: [SnoozeSettings.LIMIT_OPTIONS] (`null` — без ограничения, последним); нестандартный — по порядку.
 */
internal fun snoozeLimitOptions(current: Int?): List<Int?> {
    val standard = SnoozeSettings.LIMIT_OPTIONS
    if (current in standard) return standard
    val counts = (standard.filterNotNull() + checkNotNull(current)).sorted()
    return counts + null
}

/** «5 мин» / «Выкл»; [wide] — для TalkBack: «5 минут» / «выключено». Минуты — ICU через `:core:format`. */
internal fun snoozeIntervalText(interval: Duration?, resources: Resources, locale: Locale, wide: Boolean): String =
    if (interval == null) {
        resources.getString(if (wide) R.string.alarm_edit_snooze_off_wide else R.string.alarm_edit_snooze_off)
    } else {
        formatTimeUntil(TimeUntil(days = 0, hours = 0, minutes = interval.toMinutes().toInt()), locale, wide)
    }

/** «3 раза» / «Без ограничения». */
internal fun snoozeLimitText(maxCount: Int?, resources: Resources): String = if (maxCount == null) {
    resources.getString(R.string.alarm_edit_snooze_unlimited)
} else {
    resources.getQuantityString(R.plurals.alarm_edit_snooze_times, maxCount, maxCount)
}

/** Чипы дней в порядке недели локали (первый день — из локали), подписи — короткая и полная (TalkBack). */
internal fun dayChips(selected: Set<DayOfWeek>, weekdayFormat: WeekdayFormat): List<DayChipUi> =
    weekdayFormat.days.map { day ->
        DayChipUi(
            day = day,
            label = weekdayFormat.shortName(day),
            description = weekdayFormat.fullName(day),
            selected = day in selected,
        )
    }

/**
 * Какой пресет нажат: `PresetChips` отдаёт новый набор дней ([DayPreset.toggle]), ViewModel ждёт пресет.
 * Пустой набор — повторный тап по выбранному пресету; `null` — набор не получается ни одним пресетом.
 */
internal fun pressedPreset(current: Set<DayOfWeek>, next: Set<DayOfWeek>): DayPreset? =
    DayPreset.entries.firstOrNull { it.toggle(current) == next }

/** «80%» / «80 %» по локали — значение над слайдером громкости. */
internal fun volumeText(percent: Int, locale: Locale): String =
    NumberFormat.getPercentInstance(locale).format(percent / PERCENT)

/** «80 процентов» — состояние слайдера для TalkBack (иначе он прочитал бы долю диапазона 10..100). */
internal fun volumeDescription(percent: Int, locale: Locale): String =
    MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE).format(Measure(percent, MeasureUnit.PERCENT))

/**
 * Нарастание: «Выкл» / «15 с» / «1 мин»; [wide] — для TalkBack и диалога: «выключено» / «15 секунд» / «1 минута».
 * Единицы — ICU, как у snooze.
 */
internal fun fadeInText(fadeIn: Duration, resources: Resources, locale: Locale, wide: Boolean): String {
    if (fadeIn.isZero) {
        return resources.getString(if (wide) R.string.alarm_edit_fade_in_off_wide else R.string.alarm_edit_fade_in_off)
    }
    val seconds = fadeIn.seconds
    val measure = if (seconds % SECONDS_IN_MINUTE == 0L) {
        Measure(seconds / SECONDS_IN_MINUTE, MeasureUnit.MINUTE)
    } else {
        Measure(seconds, MeasureUnit.SECOND)
    }
    val width = if (wide) MeasureFormat.FormatWidth.WIDE else MeasureFormat.FormatWidth.SHORT
    return MeasureFormat.getInstance(locale, width).format(measure)
}

private const val PERCENT = 100.0
private const val SECONDS_IN_MINUTE = 60L
