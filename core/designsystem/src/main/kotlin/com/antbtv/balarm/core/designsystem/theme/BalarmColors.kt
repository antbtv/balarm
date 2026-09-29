package com.antbtv.balarm.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Цветовые токены Balarm (PRD §4.2). Единственный источник значений — [DarkBalarmColors] и [LightBalarmColors];
 * в UI читать через `BalarmTheme.colors` или `MaterialTheme.colorScheme`.
 *
 * Текст на [primary] (красные кнопки) в тёмной теме проходит только порог WCAG для крупного текста (≥ 3:1),
 * поэтому такой текст — не меньше 19sp Bold ([BalarmTypography.buttonLarge]).
 */
@Immutable
data class BalarmColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val success: Color,
    val warning: Color,
    val isDark: Boolean,
)

/** Тёмная тема — по умолчанию. */
internal val DarkBalarmColors = BalarmColors(
    background = Color(0xFF0E0F14),
    surface = Color(0xFF1B1D26),
    surfaceVariant = Color(0xFF262936),
    primary = Color(0xFFFF4D4F),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8B8FA3),
    success = Color(0xFF3DD68C),
    warning = Color(0xFFFFB020),
    isDark = true,
)

/**
 * Светлая тема. Отличия от таблицы PRD §4.2:
 * * `primary` = #D32F2F вместо #F0383B — утверждённая правка (белый текст на #F0383B < 4.5:1);
 * * `textSecondary` = #646879 вместо #6B6F80 — #6B6F80 на `surfaceVariant` #ECEEF4 даёт 4.30:1 < 4.5:1.
 */
internal val LightBalarmColors = BalarmColors(
    background = Color(0xFFF5F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFECEEF4),
    primary = Color(0xFFD32F2F),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF14161F),
    textSecondary = Color(0xFF646879),
    success = Color(0xFF1FAF6A),
    warning = Color(0xFFE08E00),
    isDark = false,
)

/** Проекция токенов на M3 [ColorScheme], чтобы стандартные компоненты не тянули фиолетовые дефолты. */
internal fun BalarmColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primary,
        onPrimaryContainer = onPrimary,
        inversePrimary = primary,
        secondary = textSecondary,
        onSecondary = background,
        secondaryContainer = surfaceVariant,
        onSecondaryContainer = textPrimary,
        tertiary = success,
        onTertiary = background,
        tertiaryContainer = surfaceVariant,
        onTertiaryContainer = textPrimary,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        error = primary,
        onError = onPrimary,
        outline = textSecondary,
        outlineVariant = surfaceVariant,
        scrim = Color.Black,
        surfaceBright = surfaceVariant,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceVariant,
    )
}
