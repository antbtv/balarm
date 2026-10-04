package com.antbtv.balarm.core.format

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import java.util.Locale

/**
 * «7 ч 12 мин» ([wide] = `false`, для экрана) или «7 часов 12 минут» ([wide] = `true`, для TalkBack).
 * Нулевые части опускаются: «12 мин», «7 ч», «2 д 3 ч 5 мин». Падежи и склонения даёт ICU для любой локали.
 */
fun formatTimeUntil(until: TimeUntil, locale: Locale, wide: Boolean = false): String {
    val measures = buildList {
        if (until.days > 0) add(Measure(until.days, MeasureUnit.DAY))
        if (until.hours > 0) add(Measure(until.hours, MeasureUnit.HOUR))
        if (until.minutes > 0) add(Measure(until.minutes, MeasureUnit.MINUTE))
    }
    val width = if (wide) MeasureFormat.FormatWidth.WIDE else MeasureFormat.FormatWidth.SHORT
    return MeasureFormat.getInstance(locale, width).formatMeasures(*measures.toTypedArray())
}

/** Текст тоста FR-EDIT-11: «Будильник зазвонит через 7 ч 12 мин». */
fun alarmRingsInText(context: Context, until: TimeUntil): String {
    val locale = context.resources.configuration.locales[0]
    return context.getString(R.string.format_alarm_rings_in, formatTimeUntil(until, locale))
}
