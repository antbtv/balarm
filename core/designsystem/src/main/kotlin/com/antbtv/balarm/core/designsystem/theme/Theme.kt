package com.antbtv.balarm.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/** Токены Balarm, которых нет в M3 (success, warning, textSecondary и др.). */
val LocalBalarmColors = staticCompositionLocalOf { DarkBalarmColors }

/** Стили timeHuge/timeLarge/caption/buttonLarge, которых нет в M3 1:1. */
val LocalBalarmTypography = staticCompositionLocalOf { DefaultBalarmTypography }

/**
 * Тема Balarm. Тёмная по умолчанию (стиль Alarmy); светлая включается параметром.
 * Внутри доступны и `MaterialTheme.*`, и `BalarmTheme.colors` / `BalarmTheme.typography`.
 */
@Composable
fun BalarmTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkBalarmColors else LightBalarmColors
    val colorScheme = remember(colors) { colors.toColorScheme() }
    val typography = remember { DefaultBalarmTypography.toMaterialTypography() }
    CompositionLocalProvider(
        LocalBalarmColors provides colors,
        LocalBalarmTypography provides DefaultBalarmTypography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = BalarmMaterialShapes,
            content = content,
        )
    }
}

/** Доступ к токенам Balarm из composable-кода. */
object BalarmTheme {
    val colors: BalarmColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBalarmColors.current

    val typography: BalarmTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalBalarmTypography.current
}
