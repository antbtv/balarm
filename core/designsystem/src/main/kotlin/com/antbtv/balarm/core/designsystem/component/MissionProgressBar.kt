package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentLightPreview
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Прогресс миссии наверху её экрана (скилл alarmy-ui, FR-MIS-1): [total] сегментов, решённые ([done]) — `primary`,
 * справа подпись [label] («2/3», не только цвет — WCAG 1.4.1); под сегментами — тонкая полоска таймера бездействия.
 *
 * TalkBack: один узел с [description] («Решено 2 из 3») и диапазоном прогресса; подпись и полоска — декоративные.
 *
 * @param idleFraction доля оставшегося до сброса времени, 1 → 0. Лямбда читается только при отрисовке — анимация
 * таймера не вызывает рекомпозицию.
 */
@Composable
fun MissionProgressBar(
    done: Int,
    total: Int,
    label: String,
    description: String,
    idleFraction: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = BalarmTheme.colors
    val safeTotal = total.coerceAtLeast(1)
    val safeDone = done.coerceIn(0, safeTotal)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MissionProgressBarTestTags.BAR)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = safeDone.toFloat(),
                    range = 0f..safeTotal.toFloat(),
                    steps = (safeTotal - 1).coerceAtLeast(0),
                )
            },
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingTiny),
            ) {
                repeat(safeTotal) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(BalarmDimens.MissionProgressSegment)
                            .background(
                                color = if (index < safeDone) colors.primary else colors.surfaceVariant,
                                shape = BalarmShapes.Pill,
                            ),
                    )
                }
            }
            Text(
                text = label,
                style = BalarmTheme.typography.title,
                color = colors.textPrimary,
                modifier = Modifier
                    .testTag(MissionProgressBarTestTags.LABEL)
                    .semantics { hideFromAccessibility() },
            )
        }
        IdleBar(idleFraction = idleFraction)
    }
}

/** Полоска таймера бездействия: убывает от начала строки (в RTL — справа). */
@Composable
private fun IdleBar(idleFraction: () -> Float) {
    val colors = BalarmTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BalarmDimens.MissionIdleBar)
            .clip(BalarmShapes.Pill)
            .background(colors.surfaceVariant)
            .testTag(MissionProgressBarTestTags.IDLE)
            .drawBehind {
                val width = size.width * idleFraction().coerceIn(0f, 1f)
                val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                drawRect(color = colors.textSecondary, topLeft = Offset(left, 0f), size = Size(width, size.height))
            },
    )
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@Composable
private fun MissionProgressBarSheet() {
    Column(
        modifier = Modifier.padding(BalarmDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.ScreenPadding),
    ) {
        MissionProgressBar(done = 0, total = 3, label = "0/3", description = "Solved 0 of 3", idleFraction = { 1f })
        MissionProgressBar(done = 2, total = 5, label = "2/5", description = "Solved 2 of 5", idleFraction = { 0.4f })
        MissionProgressBar(
            done = 10,
            total = 10,
            label = "10/10",
            description = "Решено 10 из 10",
            idleFraction = { 0f },
        )
    }
}

@BalarmComponentPreviews
@Composable
private fun MissionProgressBarPreview() {
    BalarmTheme { MissionProgressBarSheet() }
}

@BalarmComponentLightPreview
@Composable
private fun MissionProgressBarLightPreview() {
    BalarmTheme(darkTheme = false) { MissionProgressBarSheet() }
}
