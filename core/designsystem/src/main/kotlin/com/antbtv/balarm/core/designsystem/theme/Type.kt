package com.antbtv.balarm.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Моноширинные (табличные) цифры: время не «прыгает» при смене минут. */
private const val TABULAR_NUMBERS = "tnum"

/**
 * Типографика Balarm (PRD §4.2, скилл alarmy-ui).
 *
 * @property timeHuge время на экране звонка.
 * @property timeLarge время в карточке будильника.
 * @property title заголовки экранов и секций.
 * @property body основной текст.
 * @property caption подписи, вторичный текст.
 * @property buttonLarge текст на красных (`primary`) кнопках. Не меньше 19sp Bold: пара onPrimary/primary
 * в тёмной теме (3.27:1) проходит WCAG только как крупный текст (≥ 14pt Bold ≈ 18.7sp).
 */
@Immutable
data class BalarmTypography(
    val timeHuge: TextStyle,
    val timeLarge: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val buttonLarge: TextStyle,
)

internal val DefaultBalarmTypography = BalarmTypography(
    timeHuge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 96.sp,
        lineHeight = 104.sp,
        fontFeatureSettings = TABULAR_NUMBERS,
    ),
    timeLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        fontFeatureSettings = TABULAR_NUMBERS,
    ),
    title = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    buttonLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
    ),
)

/** Отображение на M3 [Typography]: стандартные компоненты (Button → labelLarge и т.д.) получают стили Balarm. */
internal fun BalarmTypography.toMaterialTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = timeHuge,
        displayMedium = timeLarge,
        headlineMedium = title,
        titleLarge = title,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = buttonLarge,
        labelMedium = caption,
    )
}
