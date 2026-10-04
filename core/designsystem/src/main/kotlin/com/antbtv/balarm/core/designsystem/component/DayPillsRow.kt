package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.preview.PreviewWeekdays
import com.antbtv.balarm.core.designsystem.preview.PreviewWeekdaysRu
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/** Разделитель списка выбранных дней в описании по умолчанию (пунктуация, не перевод). */
private const val DAYS_SEPARATOR = ", "

/**
 * Дни повтора — только чтение (в карточке будильника). Порядок и подписи задаёт вызывающий
 * (первый день недели из локали — `:core:format`).
 *
 * Выбранный день — подпись цветом акцента и жирным плюс точка под ней (не только цвет). Мелкий текст на заливке
 * `primary` в тёмной теме не проходит контраст 4.5:1, поэтому «таблетка» без заливки.
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
    Row(
        modifier = modifier
            .widthIn(max = BalarmDimens.DayPillsRowMaxWidth)
            .testTag(DayPillsTestTags.ROW)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingTiny),
    ) {
        days.forEach { day -> DayPill(day = day, active = active, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun DayPill(day: DayPillUi, active: Boolean, modifier: Modifier = Modifier) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    val style = if (day.selected) type.captionStrong else type.caption
    val textColor = when {
        !day.selected -> colors.textSecondary
        active -> colors.primary
        else -> colors.textPrimary
    }
    val dotColor = if (day.selected) textColor else Color.Transparent
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = day.label,
            color = textColor,
            style = style,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            // fontScale 2 на 360dp ячейка ≈ 29dp: подпись ужимается до явного минимума, а не обрезается.
            autoSize = TextAutoSize.StepBased(
                minFontSize = BalarmDimens.DayPillLabelMinFontSize,
                maxFontSize = style.fontSize,
            ),
        )
        Box(
            modifier = Modifier
                .padding(top = BalarmDimens.SpacingTiny)
                .size(BalarmDimens.DayPillIndicator)
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
