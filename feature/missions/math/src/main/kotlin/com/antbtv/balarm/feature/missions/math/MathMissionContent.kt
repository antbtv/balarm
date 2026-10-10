package com.antbtv.balarm.feature.missions.math

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.antbtv.balarm.core.designsystem.component.BalarmIcons
import com.antbtv.balarm.core.designsystem.component.ShakeOnError
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.feature.missions.api.MissionFrame
import com.antbtv.balarm.feature.missions.api.MissionSession
import kotlin.random.Random

/**
 * Миссия Math (FR-MIS, PRD §3.5): крупный пример, поле ответа и своя цифровая клавиатура.
 *
 * * Примеры детерминированы по `session.seed` — поворот экрана и пересоздание не меняют задачи.
 * * Прогресс ([MathAnswer]) — в `rememberSaveable` с ключом seed+конфиг: переживает поворот, новая сессия
 *   (новый seed) начинает с нуля.
 * * Каждое нажатие → `session.onInteraction()`; неверный ответ — тряска + haptic, ввод очищается, пример тот же;
 *   рост `session.resetSignal` (бездействие) стирает ввод текущего примера; последний верный → `onCompleted()`.
 */
@Composable
internal fun MathMissionContent(config: MathConfig, session: MissionSession, modifier: Modifier = Modifier) {
    val problems = remember(session.seed, config) {
        MathGenerator(Random(session.seed)).sequence(config.level, config.count)
    }
    var answer by rememberSaveable(session.seed, config, stateSaver = MathAnswer.Saver) {
        mutableStateOf(MathAnswer(resetsSeen = session.resetSignal))
    }
    val currentSession by rememberUpdatedState(session)
    LaunchedEffect(session.resetSignal) { answer = answer.reset(session.resetSignal) }
    LaunchedEffect(answer.solved, problems.size) { currentSession.onProgress(answer.solved, problems.size) }

    val haptic = LocalHapticFeedback.current
    val onKey: (MathKey) -> Unit = remember(problems, haptic) {
        { key ->
            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
            currentSession.onInteraction()
            val (next, result) = answer.press(key, problems)
            answer = next
            // Вызов — из обработчика нажатия, а не из эффекта: поворот после победы не повторяет завершение.
            if (result == MathPress.COMPLETED) currentSession.onCompleted()
        }
    }
    MathMissionLayout(
        problem = problems.getOrNull(answer.solved)?.text,
        input = answer.input,
        errors = answer.errors,
        onKey = onKey,
        modifier = modifier,
    )
}

@Composable
private fun MathMissionLayout(
    problem: String?,
    input: String,
    errors: Int,
    onKey: (MathKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BalarmTheme.colors
    val typography = BalarmTheme.typography
    Column(
        modifier = modifier
            .widthIn(max = BalarmDimens.MissionContentMaxWidth)
            .fillMaxSize()
            .testTag(MathTestTags.ROOT),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // Размер подбирается и по ширине, и по высоте: длинный пример при fontScale 2 не обрезается.
            Text(
                text = problem ?: stringResource(R.string.math_completed),
                style = typography.timeHuge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(maxFontSize = typography.timeHuge.fontSize),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MathTestTags.PROBLEM)
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            )
        }
        ShakeOnError(errorCount = errors, modifier = Modifier.fillMaxWidth()) {
            AnswerField(input = input)
        }
        Keypad(onKey = onKey)
    }
}

@Composable
private fun AnswerField(input: String) {
    val colors = BalarmTheme.colors
    val typography = BalarmTheme.typography
    val description = if (input.isEmpty()) {
        stringResource(R.string.math_answer_empty)
    } else {
        stringResource(R.string.math_answer_description, input)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(BalarmDimens.ButtonHeightLarge)
            .testTag(MathTestTags.ANSWER)
            .clearAndSetSemantics { contentDescription = description },
        shape = BalarmShapes.Card,
        color = colors.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (input.isEmpty()) stringResource(R.string.math_answer_placeholder) else input,
                style = typography.timeLarge,
                color = if (input.isEmpty()) colors.textSecondary else colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(maxFontSize = typography.timeLarge.fontSize),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BalarmDimens.CardPadding),
            )
        }
    }
}

private val KeypadRows: List<List<MathKey>> = listOf(
    listOf(MathKey.Digit(1), MathKey.Digit(2), MathKey.Digit(3)),
    listOf(MathKey.Digit(4), MathKey.Digit(5), MathKey.Digit(6)),
    listOf(MathKey.Digit(7), MathKey.Digit(8), MathKey.Digit(9)),
    listOf(MathKey.Erase, MathKey.Digit(0), MathKey.Enter),
)

/** Цифровая клавиатура 3×4, как у телефона: раскладка не зеркалится в RTL. Клавиши — 64dp в высоту. */
@Composable
private fun Keypad(onKey: (MathKey) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MathTestTags.KEYPAD),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
        ) {
            KeypadRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
                ) {
                    row.forEach { key ->
                        KeypadKey(key = key, onKey = onKey, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(key: MathKey, onKey: (MathKey) -> Unit, modifier: Modifier = Modifier) {
    val colors = BalarmTheme.colors
    val typography = BalarmTheme.typography
    val isEnter = key == MathKey.Enter
    val description = when (key) {
        is MathKey.Digit -> key.value.toString()
        MathKey.Erase -> stringResource(R.string.math_key_erase)
        MathKey.Enter -> stringResource(R.string.math_key_enter_description)
    }
    Surface(
        onClick = { onKey(key) },
        modifier = modifier
            .height(BalarmDimens.ButtonHeightLarge)
            .testTag(MathTestTags.key(key))
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        shape = BalarmShapes.Button,
        color = if (isEnter) colors.primary else colors.surfaceVariant,
        contentColor = if (isEnter) colors.onPrimary else colors.textPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (key) {
                is MathKey.Digit -> Text(
                    text = key.value.toString(),
                    style = typography.timeLarge,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(maxFontSize = typography.timeLarge.fontSize),
                )

                MathKey.Erase -> Icon(
                    painter = painterResource(BalarmIcons.Backspace),
                    contentDescription = null,
                    modifier = Modifier.size(BalarmDimens.Icon),
                )

                // Текст на красном — только buttonLarge (≥ 19sp Bold, контраст на primary).
                MathKey.Enter -> Text(
                    text = stringResource(R.string.math_key_enter),
                    style = typography.buttonLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Теги для Compose UI-тестов. */
object MathTestTags {
    const val ROOT = "math_root"
    const val PROBLEM = "math_problem"
    const val ANSWER = "math_answer"
    const val KEYPAD = "math_keypad"
    const val EDITOR = "math_editor"
    const val EDITOR_LEVEL = "math_editor_level"
    const val EDITOR_COUNT = "math_editor_count"
    const val EDITOR_EXAMPLE = "math_editor_example"

    fun key(key: MathKey): String = when (key) {
        is MathKey.Digit -> "math_key_${key.value}"
        MathKey.Erase -> "math_key_erase"
        MathKey.Enter -> "math_key_enter"
    }
}

// --- Превью ---

private val PreviewSession = MissionSession(
    seed = 42L,
    isPreview = true,
    resetSignal = 0,
    onInteraction = {},
    onProgress = { _, _ -> },
    onCompleted = {},
)

@Composable
private fun MathPreviewScreen(config: MathConfig = MathConfig(level = 3, count = 3)) {
    MissionFrame(done = 1, total = config.count, idleKey = 0) {
        MathMissionContent(config = config, session = PreviewSession)
    }
}

@Preview(name = "Math — dark", widthDp = 360, heightDp = 720)
@Composable
private fun MathDarkPreview() {
    BalarmTheme { MathPreviewScreen() }
}

@Preview(name = "Math — light", widthDp = 360, heightDp = 720)
@Composable
private fun MathLightPreview() {
    BalarmTheme(darkTheme = false) { MathPreviewScreen() }
}

@Preview(name = "Math — level 5, fontScale 2, 360dp", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun MathLargeFontPreview() {
    BalarmTheme { MathPreviewScreen(MathConfig(level = 5, count = 10)) }
}

@Preview(name = "Math — RU, typed answer", widthDp = 360, heightDp = 720, locale = "ru")
@Composable
private fun MathTypedPreview() {
    BalarmTheme {
        MissionFrame(done = 0, total = 3, idleKey = 0) {
            MathMissionLayout(problem = "47 + 38", input = "85", errors = 0, onKey = {})
        }
    }
}

@Preview(name = "Math — completed", widthDp = 360, heightDp = 720)
@Composable
private fun MathCompletedPreview() {
    BalarmTheme {
        MissionFrame(done = 3, total = 3, idleKey = 0) {
            MathMissionLayout(problem = null, input = "", errors = 0, onKey = {})
        }
    }
}
