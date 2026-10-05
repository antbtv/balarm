package com.antbtv.balarm.core.designsystem.component

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// Чистая математика колеса времени (`TimeWheelPicker`): индексы «бесконечного» списка, центр, шаги.
// Без Compose — проверяется обычными JVM-тестами.

/** Во сколько раз повторяются значения «бесконечной» колонки: 60 × 1000 строк хватит на любой fling. */
internal const val WHEEL_LOOPS = 1000

/** При `fontScale` от этого порога колесо показывает 3 строки вместо 5 (высота не «взрывается»). */
internal const val LARGE_FONT_SCALE = 1.5f
internal const val VISIBLE_ROWS_DEFAULT = 5
internal const val VISIBLE_ROWS_LARGE_FONT = 3

/** Число строк колонки из [n] значений: «бесконечная» — [n] × [WHEEL_LOOPS]. */
internal fun wheelItemCount(n: Int, infinite: Boolean): Int = if (infinite) n * WHEEL_LOOPS else n

/** Значение строки [index] колонки из [n] значений (0 until n). */
internal fun indexToValue(index: Int, n: Int): Int = Math.floorMod(index, n)

/** Стартовая строка для [value]: у «бесконечной» колонки — из середины, чтобы крутить в обе стороны. */
internal fun valueToStartIndex(value: Int, n: Int, infinite: Boolean): Int {
    val v = value.coerceIn(0, n - 1)
    return if (infinite) (WHEEL_LOOPS / 2) * n + v else v
}

/**
 * Строка со значением [value], ближайшая к [currentIndex]: внешняя смена 23 → 0 крутит колесо на шаг вперёд,
 * а не на 23 назад. У конечной колонки строка одна — сам [value].
 */
internal fun nearestIndexForValue(currentIndex: Int, value: Int, n: Int, infinite: Boolean): Int {
    val v = value.coerceIn(0, n - 1)
    if (!infinite) return v
    val sameLoop = currentIndex - indexToValue(currentIndex, n) + v
    val nearest = listOf(sameLoop - n, sameLoop, sameLoop + n).minBy { abs(it - currentIndex) }
    return nearest.coerceIn(0, wheelItemCount(n, infinite = true) - 1)
}

/**
 * Строка в центре колеса. Колонка сдвинута `contentPadding` так, что первая видимая строка без смещения стоит
 * ровно в центре; при смещении больше половины строки в центре уже следующая.
 */
internal fun centeredIndex(firstVisibleIndex: Int, firstVisibleOffsetPx: Int, itemSizePx: Int): Int =
    if (itemSizePx > 0 && firstVisibleOffsetPx * 2 > itemSizePx) firstVisibleIndex + 1 else firstVisibleIndex

/** Строка на [delta] шагов от [currentIndex]; у конечной колонки — `null` за краем. */
internal fun stepIndex(currentIndex: Int, delta: Int, n: Int, infinite: Boolean): Int? {
    val target = currentIndex + delta
    return if (target in 0 until wheelItemCount(n, infinite)) target else null
}

/**
 * Значение, выбранное TalkBack/`SetProgress` ([target]) при текущем [current]. Дробная цель округляется в сторону
 * движения: шаг TalkBack у колонки из двух значений (AM/PM) меньше 1 и иначе округлился бы обратно в [current].
 * `null` — вне диапазона 0 until [n].
 */
internal fun progressTargetValue(target: Float, current: Int, n: Int): Int? {
    val v = when {
        target > current -> ceil(target).toInt()
        target < current -> floor(target).toInt()
        else -> target.roundToInt()
    }
    return v.takeIf { it in 0 until n }
}

/** Число строк колеса для [fontScale]. */
internal fun visibleRowCount(fontScale: Float): Int =
    if (fontScale >= LARGE_FONT_SCALE) VISIBLE_ROWS_LARGE_FONT else VISIBLE_ROWS_DEFAULT
