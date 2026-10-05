package com.antbtv.balarm.core.designsystem.preview

import com.antbtv.balarm.core.designsystem.component.DayChipUi
import com.antbtv.balarm.core.designsystem.component.DayPillUi
import java.time.DayOfWeek

// Данные только для @Preview: подписи не локализуются, это витрина компонентов для разработчика.
// Прод-код и тесты их не используют — у тестов свои данные.

/** Будни, неделя с понедельника, английские подписи. */
internal val PreviewWeekdays = listOf(
    DayPillUi("Mo", selected = true, description = "Monday"),
    DayPillUi("Tu", selected = true, description = "Tuesday"),
    DayPillUi("We", selected = true, description = "Wednesday"),
    DayPillUi("Th", selected = true, description = "Thursday"),
    DayPillUi("Fr", selected = true, description = "Friday"),
    DayPillUi("Sa", selected = false, description = "Saturday"),
    DayPillUi("Su", selected = false, description = "Sunday"),
)

/** Будни с русскими подписями — самые широкие короткие названия дней среди RU/EN. */
internal val PreviewWeekdaysRu = listOf(
    DayPillUi("Пн", selected = true, description = "понедельник"),
    DayPillUi("Вт", selected = true, description = "вторник"),
    DayPillUi("Ср", selected = true, description = "среда"),
    DayPillUi("Чт", selected = true, description = "четверг"),
    DayPillUi("Пт", selected = true, description = "пятница"),
    DayPillUi("Сб", selected = false, description = "суббота"),
    DayPillUi("Вс", selected = false, description = "воскресенье"),
)

/** Чипы редактора: будни, неделя с понедельника, английские подписи. */
internal val PreviewDayChips = listOf(
    DayChipUi(DayOfWeek.MONDAY, "M", "Monday", selected = true),
    DayChipUi(DayOfWeek.TUESDAY, "T", "Tuesday", selected = true),
    DayChipUi(DayOfWeek.WEDNESDAY, "W", "Wednesday", selected = true),
    DayChipUi(DayOfWeek.THURSDAY, "T", "Thursday", selected = true),
    DayChipUi(DayOfWeek.FRIDAY, "F", "Friday", selected = true),
    DayChipUi(DayOfWeek.SATURDAY, "S", "Saturday", selected = false),
    DayChipUi(DayOfWeek.SUNDAY, "S", "Sunday", selected = false),
)

/** Чипы редактора: выходные, русские подписи — самые широкие. */
internal val PreviewDayChipsRu = listOf(
    DayChipUi(DayOfWeek.MONDAY, "Пн", "понедельник", selected = false),
    DayChipUi(DayOfWeek.TUESDAY, "Вт", "вторник", selected = false),
    DayChipUi(DayOfWeek.WEDNESDAY, "Ср", "среда", selected = false),
    DayChipUi(DayOfWeek.THURSDAY, "Чт", "четверг", selected = false),
    DayChipUi(DayOfWeek.FRIDAY, "Пт", "пятница", selected = false),
    DayChipUi(DayOfWeek.SATURDAY, "Сб", "суббота", selected = true),
    DayChipUi(DayOfWeek.SUNDAY, "Вс", "воскресенье", selected = true),
)
