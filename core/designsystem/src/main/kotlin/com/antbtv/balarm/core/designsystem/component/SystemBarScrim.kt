package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Подложка под прозрачным системным баром (edge-to-edge): прокручиваемый контент, уходящий под статус-бар или
 * навигационную панель, не накладывается на время, иконки и полоску жестов.
 *
 * Высота — [inset] + [BalarmDimens.SystemBarScrimFade]. Под самим баром ([inset]) подложка сплошная цветом
 * `background`, дальше — плавный переход в прозрачный (контент «растворяется», а не обрезается по линейке).
 * Касания не перехватывает, в дерево доступности ничего не добавляет.
 *
 * @param inset высота системного бара у этого края (`WindowInsets…asPaddingValues()`); 0 — только переход.
 * @param color цвет подложки; по умолчанию — фон экрана.
 */
@Composable
fun SystemBarScrim(
    edge: ScrimEdge,
    inset: Dp,
    modifier: Modifier = Modifier,
    color: Color = BalarmTheme.colors.background,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(inset + BalarmDimens.SystemBarScrimFade)
            .drawWithCache {
                // Доля сплошной части — от реальной высоты; кисть пересоздаётся только при смене размера/цвета.
                val solid = if (size.height > 0f) (inset.toPx() / size.height).coerceIn(0f, 1f) else 0f
                val transparent = color.copy(alpha = 0f)
                val brush = when (edge) {
                    ScrimEdge.Top -> Brush.verticalGradient(0f to color, solid to color, 1f to transparent)
                    ScrimEdge.Bottom -> Brush.verticalGradient(0f to transparent, 1f - solid to color, 1f to color)
                }
                onDrawBehind { drawRect(brush) }
            },
    )
}

// Превью-only: витрина компонента для разработчика.

@BalarmComponentPreviews
@Composable
private fun SystemBarScrimPreview() {
    BalarmTheme {
        // «Контент» цветом primary под подложками: видно, как он растворяется к краям.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PreviewHeight)
                .background(BalarmTheme.colors.primary),
        ) {
            SystemBarScrim(edge = ScrimEdge.Top, inset = PreviewInset, modifier = Modifier.align(Alignment.TopCenter))
            SystemBarScrim(
                edge = ScrimEdge.Bottom,
                inset = PreviewInset,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private val PreviewInset = 24.dp
private val PreviewHeight = 160.dp
