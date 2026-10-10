package com.antbtv.balarm.feature.missions.math

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.antbtv.balarm.core.designsystem.component.SliderRow
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import kotlin.math.roundToInt
import kotlin.random.Random

/** Пример в подписи уровня — всегда один и тот же для уровня, чтобы подпись не «прыгала». */
private const val EXAMPLE_SEED = 7L

/**
 * Настройки Math (FR-MIS-5): сложность 1–5 и число примеров 1–10 — ступенчатые ползунки `SliderRow`; под ними —
 * пример задачи выбранного уровня. Наружу уходит уже нормализованный JSON.
 */
@Composable
internal fun MathConfigEditor(config: MathConfig, onChange: (MathConfig) -> Unit, modifier: Modifier = Modifier) {
    val example = remember(config.level) { MathGenerator(Random(EXAMPLE_SEED)).next(config.level).text }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MathTestTags.EDITOR),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
    ) {
        SliderRow(
            title = stringResource(R.string.math_level),
            valueText = config.level.toString(),
            valueDescription = stringResource(R.string.math_level_value, config.level),
            value = config.level.toFloat(),
            onValueChange = { value ->
                val level = value.roundToInt().coerceIn(MathConfig.LEVELS)
                if (level != config.level) onChange(config.copy(level = level))
            },
            valueRange = MathConfig.LEVELS.first.toFloat()..MathConfig.LEVELS.last.toFloat(),
            steps = MathConfig.LEVELS.last - MathConfig.LEVELS.first - 1,
            modifier = Modifier.testTag(MathTestTags.EDITOR_LEVEL),
        )
        Text(
            text = stringResource(R.string.math_example, example),
            style = BalarmTheme.typography.body,
            color = BalarmTheme.colors.textSecondary,
            modifier = Modifier
                .padding(horizontal = BalarmDimens.CardPadding)
                .testTag(MathTestTags.EDITOR_EXAMPLE),
        )
        SliderRow(
            title = stringResource(R.string.math_count),
            valueText = config.count.toString(),
            valueDescription = pluralStringResource(R.plurals.math_count_value, config.count, config.count),
            value = config.count.toFloat(),
            onValueChange = { value ->
                val count = value.roundToInt().coerceIn(MathConfig.COUNTS)
                if (count != config.count) onChange(config.copy(count = count))
            },
            valueRange = MathConfig.COUNTS.first.toFloat()..MathConfig.COUNTS.last.toFloat(),
            steps = MathConfig.COUNTS.last - MathConfig.COUNTS.first - 1,
            modifier = Modifier.testTag(MathTestTags.EDITOR_COUNT),
        )
    }
}

// --- Превью ---

@Composable
private fun EditorPreviewContent() {
    var config by remember { mutableStateOf(MathConfig()) }
    Surface(
        modifier = Modifier.padding(BalarmDimens.ScreenPadding),
        shape = BalarmShapes.Card,
        color = BalarmTheme.colors.surface,
    ) {
        MathConfigEditor(config = config, onChange = { config = it })
    }
}

@Preview(name = "Math editor — dark", widthDp = 360, showBackground = true, backgroundColor = 0xFF0E0F14)
@Composable
private fun MathEditorDarkPreview() {
    BalarmTheme { EditorPreviewContent() }
}

@Preview(name = "Math editor — light", widthDp = 360, showBackground = true, backgroundColor = 0xFFF5F6FA)
@Composable
private fun MathEditorLightPreview() {
    BalarmTheme(darkTheme = false) { EditorPreviewContent() }
}

@Preview(
    name = "Math editor — RU, fontScale 2",
    widthDp = 360,
    fontScale = 2f,
    locale = "ru",
    showBackground = true,
    backgroundColor = 0xFF0E0F14,
)
@Composable
private fun MathEditorLargeFontPreview() {
    BalarmTheme { EditorPreviewContent() }
}
