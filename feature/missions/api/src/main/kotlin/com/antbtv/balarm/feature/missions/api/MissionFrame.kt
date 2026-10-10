package com.antbtv.balarm.feature.missions.api

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.antbtv.balarm.core.designsystem.component.MissionProgressBar
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.alarm.MissionPolicy
import java.time.Duration

/**
 * Каркас экрана миссии — общий для звонка и превью (ADR-018 §7, скилл alarmy-ui): сверху `MissionProgressBar`
 * («2/3» и полоска бездействия), ниже — слот [content] с самой миссией. Edge-to-edge: системные панели видимы и
 * прозрачны, отступы — `safeDrawingPadding` (как на экране звонка, FR-RING-1); экран не гаснет.
 *
 * Полоска бездействия — подсказка пользователю: отсчёт [idleTimeout] заново при каждой смене [idleKey]
 * (касание). Источник истины по таймеру — сервис звонка; полоска с ним не синхронизируется.
 *
 * @param done решено задач; [total] — всего (`MissionSession.onProgress`).
 * @param topEndAction необязательное действие справа от прогресса (кнопка «Закрыть» превью, FR-MIS-5); на
 * звонке его нет.
 */
@Composable
fun MissionFrame(
    done: Int,
    total: Int,
    idleKey: Int,
    modifier: Modifier = Modifier,
    idleTimeout: Duration = MissionPolicy.IDLE_TIMEOUT,
    topEndAction: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = BalarmTheme.colors
    val idle = remember { Animatable(1f) }
    LaunchedEffect(idleKey, idleTimeout) {
        idle.snapTo(1f)
        idle.animateTo(0f, tween(durationMillis = idleTimeout.toMillis().toInt(), easing = LinearEasing))
    }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .keepScreenOn()
            .testTag(MissionFrameTestTags.ROOT),
        color = colors.background,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(BalarmDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.ScreenPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
            ) {
                MissionProgressBar(
                    done = done,
                    total = total,
                    label = stringResource(R.string.mission_progress, done, total),
                    description = stringResource(R.string.mission_progress_description, done, total),
                    idleFraction = { idle.value },
                    modifier = Modifier.weight(1f),
                )
                topEndAction?.invoke()
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(MissionFrameTestTags.CONTENT),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}

/** Теги для Compose UI-тестов. */
object MissionFrameTestTags {
    const val ROOT = "missionFrame"
    const val CONTENT = "missionFrameContent"
}

// --- Превью ---

@Composable
private fun FramePreviewContent() {
    MissionFrame(done = 1, total = 3, idleKey = 0) {
        Text(text = "7 × 8", style = BalarmTheme.typography.timeHuge, color = BalarmTheme.colors.textPrimary)
    }
}

@Preview(name = "Mission frame — dark", widthDp = 360, heightDp = 720)
@Composable
private fun MissionFrameDarkPreview() {
    BalarmTheme { FramePreviewContent() }
}

@Preview(name = "Mission frame — light", widthDp = 360, heightDp = 720)
@Composable
private fun MissionFrameLightPreview() {
    BalarmTheme(darkTheme = false) { FramePreviewContent() }
}

@Preview(
    name = "Mission frame — fontScale 2, system bars",
    widthDp = 360,
    heightDp = 640,
    fontScale = 2f,
    showSystemUi = true,
)
@Composable
private fun MissionFrameLargeFontPreview() {
    BalarmTheme { FramePreviewContent() }
}
