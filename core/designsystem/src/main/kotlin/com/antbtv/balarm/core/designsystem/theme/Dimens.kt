package com.antbtv.balarm.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Размеры и отступы Balarm (PRD §4.2, скилл alarmy-ui). */
object BalarmDimens {
    val CardRadius = 20.dp
    val ButtonRadius = 16.dp
    val ButtonHeight = 56.dp
    val Fab = 64.dp
    val ScreenPadding = 20.dp
    val CardGap = 12.dp
    val MinTouch = 48.dp
    val SpacingSmall = 8.dp
}

/** Формы Balarm: кнопки — 16dp, карточки — 20dp, FAB — круг. */
object BalarmShapes {
    val Button = RoundedCornerShape(BalarmDimens.ButtonRadius)
    val Card = RoundedCornerShape(BalarmDimens.CardRadius)
    val Fab = CircleShape
}

/** M3 [Shapes]: `medium` — кнопки/поля, `large` — карточки. */
internal val BalarmMaterialShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = BalarmShapes.Button,
    large = BalarmShapes.Card,
    extraLarge = RoundedCornerShape(28.dp),
)
