package com.antbtv.balarm.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Test

/** WCAG 2.x контраст пар токенов (NFR-7, PRD §4.2). */
class ContrastTest {

    @Test
    fun textPrimaryOnBackgroundMeetsAa() = bothThemes { name, c ->
        assertContrast("$name textPrimary/background", c.textPrimary, c.background, AA_NORMAL)
        assertContrast("$name textPrimary/surface", c.textPrimary, c.surface, AA_NORMAL)
    }

    @Test
    fun textSecondaryOnSurfacesMeetsAa() = bothThemes { name, c ->
        assertContrast("$name textSecondary/background", c.textSecondary, c.background, AA_NORMAL)
        assertContrast("$name textSecondary/surface", c.textSecondary, c.surface, AA_NORMAL)
        assertContrast("$name textSecondary/surfaceVariant", c.textSecondary, c.surfaceVariant, AA_NORMAL)
    }

    /** Акцентный текст на `surface`: выбранные дни в карточке, destructive-кнопка в `ConfirmDialog`. */
    @Test
    fun primaryTextOnSurfaceMeetsAa() = bothThemes { name, c ->
        assertContrast("$name primary/surface", c.primary, c.surface, AA_NORMAL)
    }

    /** Переключатель (WCAG 1.4.11, ≥ 3:1): трек вкл и обводка выкл видны на карточке. */
    @Test
    fun switchPartsOnSurfaceMeetNonTextContrast() = bothThemes { name, c ->
        assertContrast("$name primary track/surface", c.primary, c.surface, AA_LARGE)
        assertContrast("$name unchecked border/surface", c.textSecondary, c.surface, AA_LARGE)
    }

    /** Подпись невыбранного пресета в `PresetChips` — на заливке `surfaceVariant`. */
    @Test
    fun textPrimaryOnSurfaceVariantMeetsAa() = bothThemes { name, c ->
        assertContrast("$name textPrimary/surfaceVariant", c.textPrimary, c.surfaceVariant, AA_NORMAL)
    }

    /**
     * WCAG 1.4.11 (≥ 3:1) на фоне экрана: заливка выбранного дня `DayChipsRow`, обводка выбранного пресета,
     * рамка `LabelField` в фокусе. Рамка без фокуса (`textSecondary`) покрыта `textSecondaryOnSurfacesMeetsAa`.
     */
    @Test
    fun primaryOutlinesOnBackgroundMeetNonTextContrast() = bothThemes { name, c ->
        assertContrast("$name primary/background", c.primary, c.background, AA_LARGE)
    }

    /** Текст `HealthBanner` (заголовок и подпись действия) — на `warningContainer`. */
    @Test
    fun textPrimaryOnWarningContainerMeetsAa() = bothThemes { name, c ->
        assertContrast("$name textPrimary/warningContainer", c.textPrimary, c.warningContainer, AA_NORMAL)
    }

    /**
     * Иконки состояния (WCAG 1.4.11, ≥ 3:1) в тёмной теме: `HealthStatusRow` на карточке и фоне, треугольник
     * `HealthBanner` на `warningContainer`, выбранная иконка `BalarmNavigationBar` на индикаторе. Светлая тема —
     * бэклог (PRD §4.2): её `warning`/`success` на светлом фоне ниже 3:1, состояние там дублируется формой иконки.
     */
    @Test
    fun statusIconsMeetNonTextContrastInDarkTheme() {
        val c = DarkBalarmColors
        listOf(c.background, c.surface).forEach { bg ->
            assertContrast("dark success/$bg", c.success, bg, AA_LARGE)
            assertContrast("dark warning/$bg", c.warning, bg, AA_LARGE)
            assertContrast("dark textSecondary/$bg", c.textSecondary, bg, AA_LARGE)
        }
        assertContrast("dark warning/warningContainer", c.warning, c.warningContainer, AA_LARGE)
        assertContrast("dark primary/surfaceVariant", c.primary, c.surfaceVariant, AA_LARGE)
    }

    @Test
    fun onPrimaryOnPrimaryLightMeetsAa() {
        assertContrast("light onPrimary/primary", LightBalarmColors.onPrimary, LightBalarmColors.primary, AA_NORMAL)
    }

    @Test
    fun onPrimaryOnPrimaryDarkMeetsAaLargeText() {
        assertContrast("dark onPrimary/primary", DarkBalarmColors.onPrimary, DarkBalarmColors.primary, AA_LARGE)
    }

    @Test
    fun buttonLargeIsLargeTextPerWcag() {
        val style = DefaultBalarmTypography.buttonLarge
        assertWithMessage("buttonLarge size").that(style.fontSize.value).isAtLeast(LARGE_BOLD_MIN_SP)
        assertWithMessage("buttonLarge weight").that(checkNotNull(style.fontWeight).weight).isAtLeast(BOLD_WEIGHT)
    }

    @Test
    fun contrastFormulaBlackOnWhiteIs21() {
        assertWithMessage("black/white").that(contrast(Color.Black, Color.White)).isWithin(0.01).of(21.0)
    }

    private fun bothThemes(block: (String, BalarmColors) -> Unit) {
        block("dark", DarkBalarmColors)
        block("light", LightBalarmColors)
    }

    private fun assertContrast(pair: String, fg: Color, bg: Color, threshold: Double) {
        assertWithMessage(pair).that(contrast(fg, bg)).isAtLeast(threshold)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun linear(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private companion object {
        const val AA_NORMAL = 4.5
        const val AA_LARGE = 3.0
        const val LARGE_BOLD_MIN_SP = 18.66f
        const val BOLD_WEIGHT = 700
    }
}
