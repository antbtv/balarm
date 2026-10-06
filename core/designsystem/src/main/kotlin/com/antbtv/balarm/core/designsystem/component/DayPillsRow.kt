package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.preview.PreviewWeekdays
import com.antbtv.balarm.core.designsystem.preview.PreviewWeekdaysRu
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/** Разделитель списка выбранных дней в описании по умолчанию (пунктуация, не перевод). */
private const val DAYS_SEPARATOR = ", "

/** Диаметр точки-индикатора относительно размера подписи: крупная подпись — заметная точка. */
private const val INDICATOR_TO_LABEL_RATIO = 0.3f

/** Шаг уменьшения подписи, если пропорциональная оценка ещё не влезла (хинтинг/кернинг нелинейны). */
private const val SHRINK_STEP = 0.95f

/** Предел итераций подгонки — защита от зацикливания на экзотических шрифтах. */
private const val MAX_SHRINK_STEPS = 20

/**
 * Дни повтора — только чтение (в карточке будильника). Порядок и подписи задаёт вызывающий
 * (первый день недели из локали — `:core:format`).
 *
 * Выбранный день — подпись цветом акцента и жирным плюс точка под ней (не только цвет). Мелкий текст на заливке
 * `primary` в тёмной теме не проходит контраст 4.5:1, поэтому «таблетка» без заливки.
 *
 * Ширина строки делится на 7 равных ячеек (раскладка без промежутков между ячейками); подпись и точка центрированы
 * в своей ячейке. Все подписи — **одного** размера: если самая широкая (жирным) не влезает в ширину ячейки минус
 * [BalarmDimens.SpacingTiny] (видимый зазор между соседними подписями) при текущем `fontScale`, размер всех подписей
 * уменьшается до влезающего, но не ниже [BalarmDimens.DayPillLabelMinSize]. Подпись не обрезается («Mon» → «Mor»).
 * Подгонка измеряет текст один раз на весь список: результат — в общем кэше [DayLabelFitCache.Shared].
 *
 * Для TalkBack строка — один узел без дочерних: [rowDescription] или список `description` выбранных дней.
 *
 * @param active `false` — карточка выключена: выбранные дни без красного акцента.
 */
@Composable
fun DayPillsRow(
    days: List<DayPillUi>,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    rowDescription: String? = null,
) {
    val description = rowDescription ?: remember(days) {
        days.filter(DayPillUi::selected).joinToString(DAYS_SEPARATOR, transform = DayPillUi::description)
    }
    BoxWithConstraints(
        modifier = modifier
            .widthIn(max = BalarmDimens.DayPillsRowMaxWidth)
            .fillMaxWidth()
            .testTag(DayPillsTestTags.ROW)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val styles = rememberDayLabelStyles(days = days, rowWidthPx = constraints.maxWidth)
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                DayPill(day = day, active = active, styles = styles, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Стили подписей дня одного размера: [selected] — `captionStrong` (жирный), [unselected] — он же с весом
 * [FontWeight.Normal] (жирный шире, поэтому размер подгоняется по нему).
 */
@Immutable
private data class DayLabelStyles(val selected: TextStyle, val unselected: TextStyle)

/**
 * Стили подписей: `captionStrong`, уменьшенный, если самая широкая подпись жирным не влезает в ячейку
 * `rowWidthPx / 7` с зазором [BalarmDimens.SpacingTiny] между соседями. Измеряется тот же стиль, что рисует `Text`
 * (`LocalTextStyle.current.merge(...)`); результат подгонки — из [DayLabelFitCache.Shared].
 */
@Composable
private fun rememberDayLabelStyles(days: List<DayPillUi>, rowWidthPx: Int): DayLabelStyles {
    val base = LocalTextStyle.current.merge(BalarmTheme.typography.captionStrong)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    // Размер зависит только от подписей: смена selected/description не пересчитывает подгонку.
    val labels = remember(days) { days.map(DayPillUi::label) }
    return remember(labels, rowWidthPx, base, density, measurer) {
        val fontSize = if (labels.isEmpty() || rowWidthPx == Constraints.Infinity) {
            base.fontSize
        } else {
            val available = rowWidthPx / labels.size - with(density) { BalarmDimens.SpacingTiny.roundToPx() }
            val key = DayLabelFitCache.Key(labels, available, density.density, density.fontScale, base)
            DayLabelFitCache.Shared.getOrPut(key) {
                fitDayLabelFontSize(
                    labels = labels,
                    maxFontSize = base.fontSize,
                    minFontSize = with(density) { BalarmDimens.DayPillLabelMinSize.toSp() },
                    availableWidthPx = available,
                    measureWidth = { label, size -> measurer.measure(label, base.copy(fontSize = size)).size.width },
                )
            }
        }
        val selected = if (fontSize == base.fontSize) {
            base
        } else {
            val ratio = fontSize.value / base.fontSize.value
            base.copy(
                fontSize = fontSize,
                lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * ratio else base.lineHeight,
            )
        }
        DayLabelStyles(selected = selected, unselected = selected.copy(fontWeight = FontWeight.Normal))
    }
}

/**
 * Потокобезопасный LRU результатов [fitDayLabelFontSize] на процесс: у всех карточек списка одинаковые подписи
 * дней, ширина и стиль — текст измеряется один раз, остальные карточки берут готовый размер. `TextMeasurer`
 * в ключ не входит: результат зависит только от ключа.
 */
internal class DayLabelFitCache(private val maxEntries: Int) {

    /** Всё, от чего зависит результат подгонки; [style] — итоговый стиль измерения (размер, вес, семейство…). */
    data class Key(
        val labels: List<String>,
        val availableWidthPx: Int,
        val density: Float,
        val fontScale: Float,
        val style: TextStyle,
    )

    private val entries = object : LinkedHashMap<Key, TextUnit>(maxEntries, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, TextUnit>?): Boolean = size > maxEntries
    }

    /** Готовый размер или [compute] (вне блокировки: измерение не держит другие потоки). */
    fun getOrPut(key: Key, compute: () -> TextUnit): TextUnit =
        synchronized(entries) { entries[key] } ?: compute().also { synchronized(entries) { entries[key] = it } }

    companion object {
        private const val LOAD_FACTOR = 0.75f

        /** Общий кэш компонента: несколько локалей/ширин/fontScale одновременно — с запасом. */
        val Shared = DayLabelFitCache(maxEntries = 8)
    }
}

/**
 * Наибольший размер шрифта из `[minFontSize, maxFontSize]`, при котором каждая из [labels] по ширине
 * ([measureWidth], px) не больше [availableWidthPx]. Ниже [minFontSize] не опускается, даже если не влезает.
 *
 * Осознанный предел: при минимуме 10dp и ячейке ≈ 40dp (360dp, карточка) влезают подписи до ~5–6 символов;
 * более длинные (нестандартные сокращения в редких локалях) на минимуме обрезаются по краю ячейки.
 */
internal fun fitDayLabelFontSize(
    labels: List<String>,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    availableWidthPx: Int,
    measureWidth: (label: String, fontSize: TextUnit) -> Int,
): TextUnit {
    fun widest(size: TextUnit): Int = labels.maxOfOrNull { measureWidth(it, size) } ?: 0
    val widestAtMax = widest(maxFontSize)
    val min = minFontSize.value
    return when {
        widestAtMax <= availableWidthPx -> maxFontSize

        availableWidthPx <= 0 -> minFontSize

        else -> {
            // Ширина текста почти пропорциональна размеру: оценка, затем добивка шагами вниз.
            var size = (maxFontSize.value * availableWidthPx / widestAtMax).coerceIn(min, maxFontSize.value)
            var steps = 0
            while (size > min && widest(size.sp) > availableWidthPx && steps < MAX_SHRINK_STEPS) {
                size = (size * SHRINK_STEP).coerceAtLeast(min)
                steps++
            }
            size.sp
        }
    }
}

@Composable
private fun DayPill(day: DayPillUi, active: Boolean, styles: DayLabelStyles, modifier: Modifier = Modifier) {
    val colors = BalarmTheme.colors
    val style = if (day.selected) styles.selected else styles.unselected
    val textColor = when {
        !day.selected -> colors.textSecondary
        active -> colors.primary
        else -> colors.textPrimary
    }
    val dotColor = if (day.selected) textColor else Color.Transparent
    val dotSize = with(LocalDensity.current) {
        (styles.selected.fontSize.toDp() * INDICATOR_TO_LABEL_RATIO).coerceAtLeast(BalarmDimens.DayPillIndicator)
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = day.label,
            color = textColor,
            style = style,
            // Без textAlign: узел по ширине текста, центрирует Column — точка точно под подписью.
            maxLines = 1,
            softWrap = false,
        )
        Box(
            modifier = Modifier
                .padding(top = BalarmDimens.SpacingTiny)
                .size(dotSize)
                .testTag(DayPillsTestTags.DOT)
                .background(dotColor, BalarmShapes.Circle),
        )
    }
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun DayPillsRowPreview() {
    BalarmTheme {
        Column(
            modifier = Modifier.padding(BalarmDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
        ) {
            DayPillsRow(days = PreviewWeekdays)
            DayPillsRow(days = PreviewWeekdays, active = false)
            DayPillsRow(days = PreviewWeekdaysRu)
        }
    }
}
