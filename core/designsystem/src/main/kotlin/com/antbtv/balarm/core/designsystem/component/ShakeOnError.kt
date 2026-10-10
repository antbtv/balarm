package com.antbtv.balarm.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/** Тряска: 300 мс, затухающие колебания (доли [BalarmDimens.ShakeOffset]). */
private const val SHAKE_DURATION_MS = 300
private const val SHAKE_STEP_MS = SHAKE_DURATION_MS / 6

@Suppress("MagicNumber") // форма колебаний — часть определения анимации
private val ShakeSpec = keyframes<Float> {
    durationMillis = SHAKE_DURATION_MS
    1f at SHAKE_STEP_MS
    -1f at SHAKE_STEP_MS * 2
    0.6f at SHAKE_STEP_MS * 3
    -0.6f at SHAKE_STEP_MS * 4
    0.3f at SHAKE_STEP_MS * 5
}

/**
 * Неверный ответ в миссии (скилл alarmy-ui): каждое увеличение [errorCount] — горизонтальная тряска содержимого
 * 300 мс и haptic `Reject`. Сдвиг — в `graphicsLayer`: раскладка не меняется, рекомпозиций нет.
 *
 * Уже показанная ошибка запоминается (`rememberSaveable`): поворот экрана не трясёт повторно.
 */
@Composable
fun ShakeOnError(errorCount: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val haptic = LocalHapticFeedback.current
    val offset = remember { Animatable(0f) }
    var shown by rememberSaveable { mutableIntStateOf(errorCount) }
    LaunchedEffect(errorCount) {
        if (errorCount == shown) return@LaunchedEffect
        shown = errorCount
        haptic.performHapticFeedback(HapticFeedbackType.Reject)
        offset.snapTo(0f)
        offset.animateTo(0f, ShakeSpec)
    }
    Box(
        modifier = modifier
            .testTag(ShakeOnErrorTestTags.CONTAINER)
            .graphicsLayer { translationX = offset.value * BalarmDimens.ShakeOffset.toPx() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@BalarmComponentPreviews
@Composable
private fun ShakeOnErrorPreview() {
    BalarmTheme {
        ShakeOnError(errorCount = 0, modifier = Modifier.padding(BalarmDimens.ScreenPadding)) {
            Text(text = "= 42", style = BalarmTheme.typography.timeLarge, color = BalarmTheme.colors.textPrimary)
        }
    }
}
