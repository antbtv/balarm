package com.antbtv.balarm.core.designsystem.component

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DayPillsRowLogicTest {

    /** Ширина «как у шрифта»: символ = 0.6 размера, плюс постоянная добавка 1 px (оценка почти точна). */
    private fun linearWidth(label: String, size: TextUnit): Int =
        (label.length * size.value * WIDTH_PER_CHAR).toInt() + 1

    /**
     * Непропорциональная ширина (крупная постоянная часть, как отступы глифов/хинтинг): пропорциональная оценка
     * размера оказывается великовата — нужны шаги вниз.
     */
    private fun nonLinearWidth(label: String, size: TextUnit): Int =
        (label.length * size.value * WIDTH_PER_CHAR + FIXED_PX).toInt()

    private fun fit(labels: List<String>, available: Int, measure: (String, TextUnit) -> Int = ::linearWidth) =
        fitDayLabelFontSize(
            labels = labels,
            maxFontSize = MAX,
            minFontSize = MIN,
            availableWidthPx = available,
            measureWidth = measure,
        )

    @Test
    fun `keeps the max size when every label fits`() {
        assertThat(fit(listOf("Mo", "Tu"), available = 100)).isEqualTo(MAX)
    }

    @Test
    fun `shrinks to the widest label so that it fits`() {
        val size = fit(listOf("Mo", "Wed"), available = 40)

        assertThat(size.value).isLessThan(MAX.value)
        assertThat(linearWidth("Wed", size)).isAtMost(40)
        // Не ужато лишнего: на шаг подгонки (5 %) крупнее — уже не влезает.
        assertThat(linearWidth("Wed", (size.value / SHRINK).sp)).isGreaterThan(40)
    }

    @Test
    fun `non-linear widths are fitted by stepping down`() {
        val available = 40
        val estimate = MAX.value * available / nonLinearWidth("Wed", MAX)
        // Предусловие: пропорциональная оценка не влезает — работает ветка дошагивания.
        assertThat(nonLinearWidth("Wed", estimate.sp)).isGreaterThan(available)

        val size = fit(listOf("Mo", "Wed"), available, ::nonLinearWidth)

        assertThat(size.value).isLessThan(estimate)
        assertThat(nonLinearWidth("Wed", size)).isAtMost(available)
        assertThat(nonLinearWidth("Wed", (size.value / SHRINK).sp)).isGreaterThan(available)
    }

    @Test
    fun `never goes below the minimum`() {
        assertThat(fit(listOf("Wednesday"), available = 10)).isEqualTo(MIN)
        assertThat(fit(listOf("Wed"), available = 0)).isEqualTo(MIN)
    }

    @Test
    fun `empty labels keep the max size`() {
        assertThat(fit(emptyList(), available = 0)).isEqualTo(MAX)
    }

    // --- Кэш подгонки ---

    private var measurements = 0

    private fun cachedFit(cache: DayLabelFitCache, key: DayLabelFitCache.Key): TextUnit = cache.getOrPut(key) {
        fit(key.labels, key.availableWidthPx) { label, size ->
            measurements++
            linearWidth(label, size)
        }
    }

    private fun key(available: Int = 40, fontScale: Float = 2f, style: TextStyle = STYLE) = DayLabelFitCache.Key(
        labels = LABELS,
        availableWidthPx = available,
        density = 1f,
        fontScale = fontScale,
        style = style,
    )

    @Test
    fun `same key measures once`() {
        val cache = DayLabelFitCache(maxEntries = 8)

        val first = cachedFit(cache, key())
        val afterFirst = measurements
        val second = cachedFit(cache, key())

        assertThat(afterFirst).isGreaterThan(0)
        assertThat(measurements).isEqualTo(afterFirst)
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `width, font scale or style change recomputes`() {
        val cache = DayLabelFitCache(maxEntries = 8)
        cachedFit(cache, key())

        listOf(key(available = 30), key(fontScale = 1.5f), key(style = STYLE.copy(fontWeight = FontWeight.Normal)))
            .forEach { changed ->
                val before = measurements
                cachedFit(cache, changed)
                assertThat(measurements).isGreaterThan(before)
            }
    }

    @Test
    fun `least recently used entry is evicted`() {
        val cache = DayLabelFitCache(maxEntries = 2)
        cachedFit(cache, key(available = 30))
        cachedFit(cache, key(available = 31))
        cachedFit(cache, key(available = 30)) // свежий
        cachedFit(cache, key(available = 32)) // вытесняет 31

        val before = measurements
        cachedFit(cache, key(available = 30))
        assertThat(measurements).isEqualTo(before)
        cachedFit(cache, key(available = 31))
        assertThat(measurements).isGreaterThan(before)
    }

    private companion object {
        const val WIDTH_PER_CHAR = 0.6f
        const val FIXED_PX = 10f
        const val SHRINK = 0.95f
        val MAX = 26.sp
        val MIN = 10.sp
        val LABELS = listOf("Sun", "Mon", "Wed")
        val STYLE = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
