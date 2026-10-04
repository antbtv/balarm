package com.antbtv.balarm.core.designsystem.preview

import androidx.compose.ui.tooling.preview.Preview

/** ARGB фона превью = `DarkBalarmColors.background`. */
internal const val PREVIEW_BACKGROUND = 0xFF0E0F14

/**
 * Набор превью компонентов: тёмная тема на узком экране (360dp) обычным шрифтом и с `fontScale = 2f`.
 * Светлая тема — бэклог (PRD §4.2), поэтому её здесь нет.
 *
 * `backgroundColor` — константа (аннотации принимают только константы): синхронизировать [PREVIEW_BACKGROUND]
 * с токеном `background` тёмной темы (`DarkBalarmColors.background`); расхождение ловит `PreviewBackgroundTest`.
 */
@Preview(name = "dark, 360dp", widthDp = 360, showBackground = true, backgroundColor = PREVIEW_BACKGROUND)
@Preview(
    name = "dark, 360dp, fontScale 2",
    widthDp = 360,
    fontScale = 2f,
    showBackground = true,
    backgroundColor = PREVIEW_BACKGROUND,
)
annotation class BalarmComponentPreviews
