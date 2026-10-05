package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.preview.PreviewDayChips
import com.antbtv.balarm.core.designsystem.preview.PreviewDayChipsRu
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import java.time.DayOfWeek

/**
 * Выбор дней повтора в редакторе (FR-EDIT-2): 7 круглых чипов 40dp. Порядок и подписи — у вызывающего
 * (первый день недели из локали — `:core:format`), компонент только рисует [days] по порядку.
 *
 * Выбранный день — заливка `primary` и текст `onPrimary`. Текст не мельче 19sp Bold при любом `fontScale`
 * (`largeTextFontRangeSp`): пара onPrimary/primary в тёмной теме проходит контраст только как крупный текст.
 * При `fontScale = 2` подпись ужимается autoSize до этой границы, а не обрезается.
 *
 * Зона тапа — вся ячейка: высота ≥ 48dp, ширина — 1/7 ширины строки, ячейки примыкают без «мёртвых» зазоров.
 * Ширина ≥ 48dp достигается, только если строке дали ≥ 336dp: на экране 360dp горизонтальный отступ строки
 * должен быть ≤ 12dp с каждой стороны. С отступом экрана 20dp ячейка ≈ 45.7dp (320 / 7).
 *
 * TalkBack: каждый чип — флажок (Role.Checkbox) с именем `description` и состоянием [selectedDescription] /
 * [notSelectedDescription].
 *
 * @param onToggle тап по дню; новое состояние вычисляет вызывающий (обычно — инверсия `selected`).
 * @param selectedDescription состояние «выбрано» для TalkBack.
 * @param notSelectedDescription состояние «не выбрано» для TalkBack.
 */
@Composable
fun DayChipsRow(
    days: List<DayChipUi>,
    onToggle: (DayOfWeek) -> Unit,
    selectedDescription: String,
    notSelectedDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widthIn(max = BalarmDimens.DayChipsRowMaxWidth)
            .fillMaxWidth()
            .testTag(DayChipsTestTags.ROW),
    ) {
        days.forEach { day ->
            DayChip(
                day = day,
                onToggle = onToggle,
                stateDescription = if (day.selected) selectedDescription else notSelectedDescription,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayChip(day: DayChipUi, onToggle: (DayOfWeek) -> Unit, stateDescription: String, modifier: Modifier) {
    val colors = BalarmTheme.colors
    val style = BalarmTheme.typography.buttonLarge
    val fontScale = LocalDensity.current.fontScale
    val autoSize = remember(style.fontSize, fontScale) {
        val range = largeTextFontRangeSp(style.fontSize.value, fontScale)
        TextAutoSize.StepBased(minFontSize = range.start.sp, maxFontSize = range.endInclusive.sp)
    }
    val fill = if (day.selected) colors.primary else colors.surfaceVariant
    val textColor = if (day.selected) colors.onPrimary else colors.textSecondary
    Box(
        modifier = modifier
            .heightIn(min = BalarmDimens.MinTouch)
            .testTag(DayChipsTestTags.chip(day.day))
            .toggleable(
                value = day.selected,
                interactionSource = null,
                indication = ripple(bounded = false, radius = BalarmDimens.MinTouch / 2),
                role = Role.Checkbox,
                onValueChange = { onToggle(day.day) },
            )
            .semantics {
                contentDescription = day.description
                this.stateDescription = stateDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = BalarmDimens.DayChip)
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(fill, BalarmShapes.Circle)
                // Имя дня читается из description узла-чипа, короткая подпись TalkBack не нужна.
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.label,
                color = textColor,
                style = style,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                autoSize = autoSize,
            )
        }
    }
}

/**
 * Диапазон autoSize (в sp) для текста на заливке `primary`.
 *
 * Пара onPrimary/primary в тёмной теме (3.27:1) проходит WCAG только как крупный текст, поэтому видимый размер
 * не должен опускаться ниже [baseSp] при `fontScale = 1` (`buttonLarge`, 19sp Bold): нижняя граница —
 * `baseSp / fontScale`. При `fontScale ≥ 1` верх — [baseSp] (масштабируется системой), при `fontScale < 1` текст
 * не уменьшается вслед за настройкой.
 *
 * Формула приближённая: на API 34+ масштаб шрифта нелинейный (`FontScaleConverter`) — мелкие размеры растут
 * почти в `fontScale` раз, крупные меньше. Нижняя граница — мелкий размер (≈ 9.5sp при 2f), для него
 * масштабирование близко к линейному, так что на экране получается ≈ 19dp.
 */
internal fun largeTextFontRangeSp(baseSp: Float, fontScale: Float): ClosedFloatingPointRange<Float> {
    require(baseSp > 0f && fontScale > 0f) { "baseSp and fontScale must be positive" }
    val minSp = baseSp / fontScale
    return minSp..maxOf(baseSp, minSp)
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun DayChipsRowPreview() {
    BalarmTheme {
        Column(
            modifier = Modifier.padding(BalarmDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
        ) {
            DayChipsRow(
                days = PreviewDayChips,
                onToggle = {},
                selectedDescription = "Selected",
                notSelectedDescription = "Not selected",
            )
            DayChipsRow(
                days = PreviewDayChipsRu,
                onToggle = {},
                selectedDescription = "Выбрано",
                notSelectedDescription = "Не выбрано",
            )
        }
    }
}
