package com.antbtv.balarm.feature.alarmlist

import android.content.res.Resources
import androidx.compose.runtime.Immutable
import com.antbtv.balarm.core.designsystem.component.DayPillUi
import com.antbtv.balarm.core.designsystem.component.DayPreset
import com.antbtv.balarm.core.format.ClockFormat
import com.antbtv.balarm.core.format.WeekdayFormat
import java.time.DayOfWeek
import java.time.LocalTime

/** Разделитель частей описания для TalkBack (пунктуация, не перевод: одинакова в RU и EN). */
private const val PARTS_SEPARATOR = ", "

/** Готовые строки одной карточки: всё, что `AlarmCard` принимает текстом. */
@Immutable
internal data class AlarmCardTexts(
    val time: String,
    val amPm: String?,
    val subtitle: String?,
    val contentDescription: String,
    val toggleDescription: String,
)

/**
 * Строки карточки из [item]. TalkBack читает карточку одним узлом:
 * «Будильник 07:30, Подъём, по будням, Завтра, включён»; тумблер — «Будильник 07:30, Подъём»
 * (вкл/выкл TalkBack добавляет сам по роли Switch).
 */
internal fun alarmCardTexts(
    item: AlarmItemUi,
    clockFormat: ClockFormat,
    weekdayFormat: WeekdayFormat,
    resources: Resources,
): AlarmCardTexts {
    val parts = clockFormat.timeParts(item.time)
    val subtitle = subtitleText(item.subtitle, clockFormat, resources)
    val label = item.label.takeIf { it.isNotBlank() }
    val title = alarmTitle(item.time, clockFormat, resources)
    val toggleDescription = listOfNotNull(title, label).joinToString(PARTS_SEPARATOR)
    val state = resources.getString(if (item.active) R.string.alarm_list_card_on else R.string.alarm_list_card_off)
    val description = listOfNotNull(
        title,
        label,
        repeatText(item.repeatDays, weekdayFormat, resources),
        subtitle,
        state,
    ).joinToString(PARTS_SEPARATOR)
    return AlarmCardTexts(
        time = parts.text,
        amPm = parts.amPm,
        subtitle = subtitle,
        contentDescription = description,
        toggleDescription = toggleDescription,
    )
}

/** «Будильник 07:30» — время полностью, с AM/PM в 12-часовом формате. */
internal fun alarmTitle(time: LocalTime, clockFormat: ClockFormat, resources: Resources): String =
    resources.getString(R.string.alarm_list_card_alarm, clockFormat.time(time))

internal fun subtitleText(subtitle: AlarmSubtitle?, clockFormat: ClockFormat, resources: Resources): String? =
    when (subtitle) {
        null -> null

        AlarmSubtitle.Today -> resources.getString(R.string.alarm_list_today)

        AlarmSubtitle.Tomorrow -> resources.getString(R.string.alarm_list_tomorrow)

        AlarmSubtitle.NotScheduled -> resources.getString(R.string.alarm_list_not_scheduled)

        is AlarmSubtitle.SnoozedUntil ->
            resources.getString(R.string.alarm_list_snoozed_until, clockFormat.time(subtitle.time))
    }

/** «однократно», «каждый день», «по будням», «по выходным» или полные названия дней в порядке недели пользователя. */
internal fun repeatText(days: Set<DayOfWeek>, weekdayFormat: WeekdayFormat, resources: Resources): String = when {
    days.isEmpty() -> resources.getString(R.string.alarm_list_repeat_once)

    DayPreset.EVERY_DAY.matches(days) -> resources.getString(R.string.alarm_list_repeat_every_day)

    DayPreset.WEEKDAYS.matches(days) -> resources.getString(R.string.alarm_list_repeat_weekdays)

    DayPreset.WEEKEND.matches(days) -> resources.getString(R.string.alarm_list_repeat_weekends)

    else -> weekdayFormat.days.filter { it in days }
        .joinToString(PARTS_SEPARATOR, transform = weekdayFormat::fullName)
}

/** Строка дней карточки; разовый будильник — пустой список (строка не показывается). */
internal fun dayPills(days: Set<DayOfWeek>, weekdayFormat: WeekdayFormat): List<DayPillUi> = if (days.isEmpty()) {
    emptyList()
} else {
    weekdayFormat.days.map { day ->
        DayPillUi(
            label = weekdayFormat.shortName(day).replaceFirstChar { it.titlecase(weekdayFormat.locale) },
            selected = day in days,
            description = weekdayFormat.fullName(day),
        )
    }
}
