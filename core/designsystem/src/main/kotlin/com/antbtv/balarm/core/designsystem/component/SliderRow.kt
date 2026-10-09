package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Строка настройки со ступенчатым ползунком: сверху [title] и [valueText] (`textSecondary`, справа), под ними
 * M3 `Slider` во всю ширину. При fontScale 2 заголовок и значение переносятся, ползунок не сжимается.
 *
 * TalkBack: один регулируемый узел — ползунок с описанием [title] и состоянием [valueDescription] («80 процентов»
 * вместо доли диапазона, которую слайдер прочитал бы сам); жесты громкости TalkBack двигают на шаг. Подписи над
 * ползунком из дерева доступности убраны — иначе значение читалось бы дважды. Зона касания ползунка ≥ 48dp
 * (M3 `Slider` расширяет её сам), строка целиком ≥ 56dp.
 *
 * @param steps число промежуточных делений, как у M3 `Slider` (10..100 с шагом 10 → 8).
 * @param onValueChange вызывается на каждом шаге перетаскивания (значение уже привязано к делению).
 * @param onValueChangeFinished конец жеста.
 */
@Composable
fun SliderRow(
    title: String,
    valueText: String,
    valueDescription: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    val contentColor = if (enabled) colors.textPrimary else colors.textSecondary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SliderRowTestTags.ROW)
            .padding(horizontal = BalarmDimens.CardPadding, vertical = BalarmDimens.SpacingSmall),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
        ) {
            Text(text = title, style = type.body, color = contentColor, modifier = Modifier.weight(1f))
            Text(
                text = valueText,
                style = type.body,
                color = colors.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.testTag(SliderRowTestTags.VALUE),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            colors = balarmSliderColors(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = title
                    stateDescription = valueDescription
                }
                .testTag(SliderRowTestTags.SLIDER),
        )
    }
}

@Composable
private fun balarmSliderColors(): SliderColors {
    val colors = BalarmTheme.colors
    return SliderDefaults.colors(
        thumbColor = colors.primary,
        activeTrackColor = colors.primary,
        activeTickColor = colors.onPrimary,
        inactiveTrackColor = colors.surfaceVariant,
        inactiveTickColor = colors.textSecondary,
        disabledThumbColor = colors.textSecondary,
        disabledActiveTrackColor = colors.textSecondary,
        disabledInactiveTrackColor = colors.surfaceVariant,
    )
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun SliderRowPreview() {
    BalarmTheme {
        var value by remember { mutableFloatStateOf(80f) }
        Surface(
            modifier = Modifier.padding(BalarmDimens.ScreenPadding),
            shape = BalarmShapes.Card,
            color = BalarmTheme.colors.surface,
        ) {
            Column {
                SliderRow(
                    title = "Volume",
                    valueText = "${value.toInt()}%",
                    valueDescription = "${value.toInt()} percent",
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 10f..100f,
                    steps = 8,
                )
                SliderRow(
                    title = "Громкость будильника",
                    valueText = "30 %",
                    valueDescription = "30 процентов",
                    value = 30f,
                    onValueChange = {},
                    valueRange = 10f..100f,
                    steps = 8,
                    enabled = false,
                )
            }
        }
    }
}
