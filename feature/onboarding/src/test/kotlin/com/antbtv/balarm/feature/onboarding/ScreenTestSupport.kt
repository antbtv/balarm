package com.antbtv.balarm.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.google.common.truth.Truth.assertWithMessage

// Копия ScreenTestSupport из :feature:settings: общего модуля тестовых утилит UI пока нет (два потребителя).

/** Тема + при необходимости увеличенный шрифт (как «Размер шрифта» в системных настройках). */
@Composable
internal fun TestTheme(fontScale: Float? = null, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scaled = fontScale?.let { Density(density = density.density, fontScale = it) } ?: density
    CompositionLocalProvider(LocalDensity provides scaled) {
        BalarmTheme(content = content)
    }
}

/**
 * Ни один показанный текст не обрезан: нет переполнения по высоте, многоточий, и каждая строка не шире узла.
 * `hasVisualOverflow` не подходит: у текста с обёрткой по содержимому `didOverflowWidth` срабатывает ложно
 * (так же проверяет `HealthComponentsTest` дизайн-системы). Допуск — округление границ строки до пикселя.
 */
internal fun ComposeTestRule.assertNoTextOverflow() {
    val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes()
    assertWithMessage("text nodes").that(nodes).isNotEmpty()
    nodes.forEach { node ->
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        val text = layout.layoutInput.text
        assertWithMessage("\"$text\" overflows in height").that(layout.didOverflowHeight).isFalse()
        for (line in 0 until layout.lineCount) {
            assertWithMessage("\"$text\" line $line is ellipsized").that(layout.isLineEllipsized(line)).isFalse()
            assertWithMessage("\"$text\" line $line is wider than its node")
                .that(layout.getLineRight(line) - layout.getLineLeft(line))
                .isAtMost(node.size.width + LINE_ROUNDING_PX)
        }
    }
}

private const val LINE_ROUNDING_PX = 1f
