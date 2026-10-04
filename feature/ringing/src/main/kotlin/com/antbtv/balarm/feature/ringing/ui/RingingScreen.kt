package com.antbtv.balarm.feature.ringing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antbtv.balarm.core.designsystem.component.PrimaryButton
import com.antbtv.balarm.core.designsystem.component.SecondaryButton
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.format.rememberClockFormat
import com.antbtv.balarm.feature.ringing.R
import java.time.LocalDateTime

/** Обёртка с ViewModel: состояние — с учётом жизненного цикла, команды — в [RingingViewModel.onEvent]. */
@Composable
fun RingingRoute(viewModel: RingingViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RingingScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/**
 * Экран звонка (FR-RING-2, скилл alarmy-ui): дата, огромное текущее время, метка; снизу «Отложить (N)» и
 * пульсирующая «Отключить». Кнопок нет, пока звонок не начался ([RingingPhase.WAITING]) и после его конца.
 */
@Composable
fun RingingScreen(state: RingingUiState, onEvent: (RingingEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = BalarmTheme.colors
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(RingingTestTags.ROOT),
        color = colors.background,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(BalarmDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
        ) {
            ClockArea(now = state.now, label = state.label)
            if (state.phase == RingingPhase.RINGING) {
                RingingActions(snooze = state.snooze, onEvent = onEvent)
            }
        }
    }
}

/** Часы по центру свободного места; при `fontScale = 2` и низком экране — прокрутка вместо обрезки. */
@Composable
private fun ColumnScope.ClockArea(now: LocalDateTime, label: String) {
    val clockFormat = rememberClockFormat()
    val colors = BalarmTheme.colors
    val typography = BalarmTheme.typography
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = clockFormat.date(now),
                style = typography.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(RingingTestTags.DATE),
            )
            // 96sp × fontScale 2 не влезает в 360dp — размер подбирается по ширине, в одну строку.
            Text(
                text = clockFormat.time(now),
                style = typography.timeHuge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(maxFontSize = typography.timeHuge.fontSize),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RingingTestTags.TIME)
                    .semantics { heading() },
            )
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    style = typography.title,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(RingingTestTags.LABEL),
                )
            }
        }
    }
}

@Composable
private fun RingingActions(snooze: SnoozeUi, onEvent: (RingingEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
    ) {
        val snoozeText = when (snooze) {
            SnoozeUi.Hidden -> null
            SnoozeUi.Unlimited -> stringResource(R.string.ringing_snooze)
            is SnoozeUi.Limited -> stringResource(R.string.ringing_snooze_left, snooze.left)
        }
        if (snoozeText != null) {
            SecondaryButton(
                text = snoozeText,
                onClick = { onEvent(RingingEvent.Snooze) },
                minHeight = BalarmDimens.ButtonHeightLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RingingTestTags.SNOOZE),
            )
        }
        PrimaryButton(
            text = stringResource(R.string.ringing_dismiss),
            onClick = { onEvent(RingingEvent.Dismiss) },
            pulsing = true,
            minHeight = BalarmDimens.ButtonHeightLarge,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RingingTestTags.DISMISS),
        )
    }
}

// --- Превью ---

private val PreviewNow: LocalDateTime = LocalDateTime.of(2026, 10, 3, 6, 30)

private val PreviewRinging = RingingUiState(
    now = PreviewNow,
    phase = RingingPhase.RINGING,
    label = "Morning run",
    snooze = SnoozeUi.Limited(left = 3),
)

@Preview(name = "Ringing — dark", widthDp = 360, heightDp = 720)
@Composable
private fun RingingDarkPreview() {
    BalarmTheme { RingingScreen(state = PreviewRinging, onEvent = {}) }
}

@Preview(name = "Ringing — light", widthDp = 360, heightDp = 720)
@Composable
private fun RingingLightPreview() {
    BalarmTheme(darkTheme = false) { RingingScreen(state = PreviewRinging, onEvent = {}) }
}

@Preview(name = "Ringing — dark, fontScale 2, 360dp", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun RingingLargeFontPreview() {
    BalarmTheme { RingingScreen(state = PreviewRinging, onEvent = {}) }
}

// Системные бары видимы (M2-T07): превью с системным UI показывает, что кнопки не уходят под навигацию.
@Preview(
    name = "Ringing — system bars, fontScale 2, 360dp",
    widthDp = 360,
    heightDp = 640,
    fontScale = 2f,
    showSystemUi = true,
)
@Composable
private fun RingingSystemBarsPreview() {
    BalarmTheme { RingingScreen(state = PreviewRinging, onEvent = {}) }
}

@Preview(name = "Ringing — RU, snooze unlimited", widthDp = 360, heightDp = 720, locale = "ru")
@Composable
private fun RingingRuUnlimitedPreview() {
    BalarmTheme {
        RingingScreen(state = PreviewRinging.copy(label = "", snooze = SnoozeUi.Unlimited), onEvent = {})
    }
}

@Preview(name = "Ringing — snooze hidden", widthDp = 360, heightDp = 720)
@Composable
private fun RingingNoSnoozePreview() {
    BalarmTheme { RingingScreen(state = PreviewRinging.copy(snooze = SnoozeUi.Hidden), onEvent = {}) }
}

@Preview(name = "Ringing — waiting for service", widthDp = 360, heightDp = 720)
@Composable
private fun RingingWaitingPreview() {
    BalarmTheme { RingingScreen(state = RingingUiState(now = PreviewNow, phase = RingingPhase.WAITING), onEvent = {}) }
}
