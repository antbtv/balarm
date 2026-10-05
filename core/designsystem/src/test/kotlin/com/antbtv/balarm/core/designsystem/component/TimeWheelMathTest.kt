package com.antbtv.balarm.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeWheelMathTest {

    @Test
    fun `index maps to value modulo n including negative indexes`() {
        assertThat(indexToValue(0, 24)).isEqualTo(0)
        assertThat(indexToValue(23, 24)).isEqualTo(23)
        assertThat(indexToValue(24, 24)).isEqualTo(0)
        assertThat(indexToValue(60 * 500 + 59, 60)).isEqualTo(59)
        assertThat(indexToValue(-1, 60)).isEqualTo(59)
    }

    @Test
    fun `infinite column starts from the middle and finite from the value itself`() {
        val start = valueToStartIndex(7, 24, infinite = true)
        assertThat(indexToValue(start, 24)).isEqualTo(7)
        assertThat(start).isEqualTo(24 * WHEEL_LOOPS / 2 + 7)
        assertThat(start).isLessThan(wheelItemCount(24, infinite = true))
        assertThat(valueToStartIndex(1, 2, infinite = false)).isEqualTo(1)
        assertThat(wheelItemCount(2, infinite = false)).isEqualTo(2)
        // Мусор снаружи не роняет колесо.
        assertThat(valueToStartIndex(99, 60, infinite = true)).isEqualTo(60 * WHEEL_LOOPS / 2 + 59)
    }

    @Test
    fun `next index after 23 is 0 and after 59 is 0`() {
        val hour23 = valueToStartIndex(23, 24, infinite = true)
        assertThat(indexToValue(hour23 + 1, 24)).isEqualTo(0)
        val minute59 = valueToStartIndex(59, 60, infinite = true)
        assertThat(indexToValue(minute59 + 1, 60)).isEqualTo(0)
        assertThat(indexToValue(valueToStartIndex(0, 60, infinite = true) - 1, 60)).isEqualTo(59)
    }

    @Test
    fun `external value scrolls the shortest way across the wrap`() {
        val hour23 = valueToStartIndex(23, 24, infinite = true)
        assertThat(nearestIndexForValue(hour23, 0, 24, infinite = true)).isEqualTo(hour23 + 1)
        val hour0 = valueToStartIndex(0, 24, infinite = true)
        assertThat(nearestIndexForValue(hour0, 23, 24, infinite = true)).isEqualTo(hour0 - 1)
        val minute30 = valueToStartIndex(30, 60, infinite = true)
        assertThat(nearestIndexForValue(minute30, 45, 60, infinite = true)).isEqualTo(minute30 + 15)
        assertThat(nearestIndexForValue(minute30, 30, 60, infinite = true)).isEqualTo(minute30)
        assertThat(nearestIndexForValue(0, 1, 2, infinite = false)).isEqualTo(1)
    }

    @Test
    fun `centered row switches after half a row`() {
        assertThat(centeredIndex(10, 0, 100)).isEqualTo(10)
        assertThat(centeredIndex(10, 50, 100)).isEqualTo(10)
        assertThat(centeredIndex(10, 51, 100)).isEqualTo(11)
        assertThat(centeredIndex(10, 30, 0)).isEqualTo(10)
    }

    @Test
    fun `step stays inside finite column and wraps infinite one`() {
        assertThat(stepIndex(1, 1, 2, infinite = false)).isNull()
        assertThat(stepIndex(0, -1, 2, infinite = false)).isNull()
        assertThat(stepIndex(0, 1, 2, infinite = false)).isEqualTo(1)
        val minute59 = valueToStartIndex(59, 60, infinite = true)
        assertThat(indexToValue(stepIndex(minute59, 1, 60, infinite = true)!!, 60)).isEqualTo(0)
    }

    @Test
    fun `progress target rounds in the direction of movement`() {
        assertThat(progressTargetValue(0.05f, 0, 2)).isEqualTo(1)
        assertThat(progressTargetValue(0.95f, 1, 2)).isEqualTo(0)
        assertThat(progressTargetValue(13f, 7, 24)).isEqualTo(13)
        assertThat(progressTargetValue(7f, 7, 24)).isEqualTo(7)
        assertThat(progressTargetValue(24f, 23, 24)).isNull()
        assertThat(progressTargetValue(-1f, 0, 24)).isNull()
    }

    @Test
    fun `12h conversions`() {
        assertThat(to12h(0)).isEqualTo(12)
        assertThat(to12h(12)).isEqualTo(12)
        assertThat(to12h(13)).isEqualTo(1)
        assertThat(to12h(11)).isEqualTo(11)
        assertThat(to12h(23)).isEqualTo(11)
        assertThat(isPm(11)).isFalse()
        assertThat(isPm(12)).isTrue()
        // Круговая проверка: колонка часов + AM/PM возвращают исходный час.
        (0..23).forEach {
            val column = hourColumnValue(it, is24Hour = false)
            assertThat(hourFromColumn(column, it, is24Hour = false)).isEqualTo(it)
            assertThat(hourFromPeriod(if (isPm(it)) PERIOD_PM else PERIOD_AM, it)).isEqualTo(it)
        }
    }

    @Test
    fun `hour column keeps the half of the day and does not flip AM PM at 11 to 12`() {
        assertThat(hourColumnValue(13, is24Hour = false)).isEqualTo(1)
        assertThat(hourColumnValue(13, is24Hour = true)).isEqualTo(13)
        // 11 AM → «12» = 12 AM (полночь), не 12 PM.
        assertThat(hourFromColumn(0, 11, is24Hour = false)).isEqualTo(0)
        // 11 PM → «12» = 12 PM (полдень).
        assertThat(hourFromColumn(0, 23, is24Hour = false)).isEqualTo(12)
        assertThat(hourFromColumn(1, 12, is24Hour = false)).isEqualTo(13)
        assertThat(hourFromColumn(17, 3, is24Hour = true)).isEqualTo(17)
    }

    @Test
    fun `period column keeps the clock hour`() {
        assertThat(hourFromPeriod(PERIOD_PM, 0)).isEqualTo(12)
        assertThat(hourFromPeriod(PERIOD_AM, 12)).isEqualTo(0)
        assertThat(hourFromPeriod(PERIOD_AM, 13)).isEqualTo(1)
        assertThat(hourFromPeriod(PERIOD_PM, 13)).isEqualTo(13)
    }

    @Test
    fun `large font scale shows three rows`() {
        assertThat(visibleRowCount(1f)).isEqualTo(5)
        assertThat(visibleRowCount(1.3f)).isEqualTo(5)
        assertThat(visibleRowCount(1.5f)).isEqualTo(3)
        assertThat(visibleRowCount(2f)).isEqualTo(3)
    }

    @Test
    fun `two digits`() {
        assertThat(twoDigits(0)).isEqualTo("00")
        assertThat(twoDigits(7)).isEqualTo("07")
        assertThat(twoDigits(59)).isEqualTo("59")
    }
}
