package com.antbtv.balarm.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

// Превью-only: подписи не локализуются, это витрина токенов для разработчика.

@Composable
private fun PaletteSwatch(name: String, color: Color, onColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BalarmDimens.MinTouch)
            .background(color, BalarmShapes.Button)
            .padding(horizontal = BalarmDimens.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = name, color = onColor, style = BalarmTheme.typography.body)
    }
}

@Composable
private fun PaletteSheet() {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    Column(
        modifier = Modifier
            .background(colors.background)
            .padding(BalarmDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
    ) {
        Text("06:30", style = type.timeLarge, color = colors.textPrimary)
        Text("Title 22sp", style = type.title, color = colors.textPrimary)
        Text("Secondary caption 13sp", style = type.caption, color = colors.textSecondary)
        PaletteSwatch("surface", colors.surface, colors.textPrimary)
        PaletteSwatch("surfaceVariant", colors.surfaceVariant, colors.textSecondary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BalarmDimens.ButtonHeight)
                .background(colors.primary, BalarmShapes.Button),
            contentAlignment = Alignment.Center,
        ) {
            Text("primary / buttonLarge", style = type.buttonLarge, color = colors.onPrimary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall)) {
            Box(Modifier.size(BalarmDimens.MinTouch).background(colors.success, BalarmShapes.Fab))
            Box(Modifier.size(BalarmDimens.MinTouch).background(colors.warning, BalarmShapes.Fab))
        }
    }
}

@Preview(name = "Palette — dark", widthDp = 360)
@Composable
private fun PaletteDarkPreview() {
    BalarmTheme(darkTheme = true) { PaletteSheet() }
}

@Preview(name = "Palette — light", widthDp = 360)
@Composable
private fun PaletteLightPreview() {
    BalarmTheme(darkTheme = false) { PaletteSheet() }
}

@Preview(name = "Palette — dark, fontScale 2", widthDp = 360, fontScale = 2f)
@Composable
private fun PaletteLargeFontPreview() {
    BalarmTheme { PaletteSheet() }
}
