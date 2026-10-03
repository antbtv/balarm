package com.antbtv.balarm.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/** Пульсация главной кнопки экрана звонка: 1.0 ↔ 1.05, полный цикл 1.2 с (скилл alarmy-ui). */
private const val PULSE_MAX_SCALE = 1.05f
private const val PULSE_HALF_PERIOD_MS = 600

private val ButtonContentPadding = PaddingValues(horizontal = BalarmDimens.ScreenPadding)

/**
 * Главная кнопка: фон `primary`, текст `buttonLarge` (≥ 19sp Bold — порог контраста на красном).
 *
 * @param pulsing мягкая пульсация масштаба — для «Отключить» на экране звонка. Масштаб применяется
 * в `graphicsLayer`, поэтому анимация не вызывает рекомпозицию и не меняет раскладку.
 * @param minHeight [BalarmDimens.ButtonHeight] по умолчанию; [BalarmDimens.ButtonHeightLarge] — звонок/миссии.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pulsing: Boolean = false,
    minHeight: Dp = BalarmDimens.ButtonHeight,
) {
    val colors = BalarmTheme.colors
    val pulseModifier = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val scale = transition.animateFloat(
            initialValue = 1f,
            targetValue = PULSE_MAX_SCALE,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = PULSE_HALF_PERIOD_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseScale",
        )
        Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    } else {
        Modifier
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .then(pulseModifier)
            .heightIn(min = minHeight),
        enabled = enabled,
        shape = BalarmShapes.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.surfaceVariant,
            disabledContentColor = colors.textSecondary,
        ),
        contentPadding = ButtonContentPadding,
    ) {
        Text(text = text, style = BalarmTheme.typography.buttonLarge, textAlign = TextAlign.Center)
    }
}

/** Второстепенная кнопка: фон `surfaceVariant`, текст `textPrimary`. Те же размеры, что у [PrimaryButton]. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = BalarmDimens.ButtonHeight,
) {
    val colors = BalarmTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        enabled = enabled,
        shape = BalarmShapes.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surfaceVariant,
            contentColor = colors.textPrimary,
            disabledContainerColor = colors.surface,
            disabledContentColor = colors.textSecondary,
        ),
        contentPadding = ButtonContentPadding,
    ) {
        Text(text = text, style = BalarmTheme.typography.buttonLarge, textAlign = TextAlign.Center)
    }
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@Composable
private fun ButtonsSheet() {
    Column(
        modifier = Modifier.padding(BalarmDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
    ) {
        SecondaryButton(text = "Snooze (3)", onClick = {}, modifier = Modifier.fillMaxWidth())
        PrimaryButton(
            text = "Dismiss",
            onClick = {},
            pulsing = true,
            minHeight = BalarmDimens.ButtonHeightLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(text = "Disabled", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "Buttons — dark", widthDp = 360, showBackground = true, backgroundColor = 0xFF0E0F14)
@Composable
private fun ButtonsDarkPreview() {
    BalarmTheme(darkTheme = true) { ButtonsSheet() }
}

@Preview(name = "Buttons — light", widthDp = 360, showBackground = true, backgroundColor = 0xFFF5F6FA)
@Composable
private fun ButtonsLightPreview() {
    BalarmTheme(darkTheme = false) { ButtonsSheet() }
}

@Preview(
    name = "Buttons — dark, fontScale 2",
    widthDp = 360,
    fontScale = 2f,
    showBackground = true,
    backgroundColor = 0xFF0E0F14,
)
@Composable
private fun ButtonsLargeFontPreview() {
    BalarmTheme { ButtonsSheet() }
}
