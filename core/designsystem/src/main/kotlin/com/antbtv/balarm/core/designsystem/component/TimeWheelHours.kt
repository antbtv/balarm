package com.antbtv.balarm.core.designsystem.component

// Часы колеса времени (`TimeWheelPicker`): 12h ↔ 24h, подписи. Без Compose — проверяется JVM-тестами.

internal const val HOURS_24 = 24
internal const val HOURS_12 = 12
internal const val MINUTES_IN_HOUR = 60

/** AM/PM-колонка: 0 — AM, 1 — PM. */
internal const val PERIOD_AM = 0
internal const val PERIOD_PM = 1

private const val TWO_DIGITS = 10

/** Час 0..23 → час на 12-часовом циферблате 1..12 (0 → 12 AM, 12 → 12 PM). */
internal fun to12h(hour24: Int): Int = (hour24 % HOURS_12).let { if (it == 0) HOURS_12 else it }

/** Вторая половина суток (PM) для часа 0..23. */
internal fun isPm(hour24: Int): Boolean = hour24 >= HOURS_12

/** Значение колонки часов (0 until n) для часа 0..23: в 12h — позиция на циферблате 0..11 (0 = «12»). */
internal fun hourColumnValue(hour24: Int, is24Hour: Boolean): Int = if (is24Hour) hour24 else hour24 % HOURS_12

/** Час 0..23 после выбора [columnValue] в колонке часов; в 12h половина суток [hour24] сохраняется. */
internal fun hourFromColumn(columnValue: Int, hour24: Int, is24Hour: Boolean): Int =
    if (is24Hour) columnValue else columnValue + if (isPm(hour24)) HOURS_12 else 0

/** Час 0..23 после выбора AM/PM [period]; час на циферблате (1..12) не меняется. */
internal fun hourFromPeriod(period: Int, hour24: Int): Int =
    hour24 % HOURS_12 + if (period == PERIOD_PM) HOURS_12 else 0

/** «7» → «07». Цифры ASCII, как и в формате времени карточки. */
internal fun twoDigits(value: Int): String = if (value < TWO_DIGITS) "0$value" else value.toString()
