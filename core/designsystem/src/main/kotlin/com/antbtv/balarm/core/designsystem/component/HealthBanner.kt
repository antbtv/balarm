package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentLightPreview
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Баннер «Будильник может не сработать → Исправить» над списком будильников (FR-LIST-5). Фон —
 * `warningContainer`, иконка-треугольник `warning` (не только цвет), под текстом — подпись действия с шевроном.
 * Кликабелен целиком (≥ 48dp), строки готовит вызывающий.
 *
 * TalkBack: один узел-кнопка, читается [text], действие — [actionLabel] («Дважды нажмите, чтобы исправить»).
 * Подпись действия отдельным узлом не читается. Баннер — live region: появление на `ON_RESUME` объявляется.
 *
 * @param text предупреждение («Будильник может не сработать»).
 * @param actionLabel подпись действия («Исправить»).
 * @param onClick переход на экран здоровья.
 */
@Composable
fun HealthBanner(text: String, actionLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BalarmDimens.MinTouch)
            .testTag(HealthBannerTestTags.BANNER)
            .clip(BalarmShapes.Card)
            .background(colors.warningContainer)
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = text
                liveRegion = LiveRegionMode.Polite
            }
            .padding(BalarmDimens.CardPadding),
        horizontalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
    ) {
        Icon(
            painter = painterResource(BalarmIcons.StatusWarning),
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(BalarmDimens.Icon),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
        ) {
            Text(text = text, style = type.body, color = colors.textPrimary)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingTiny),
            ) {
                Text(
                    text = actionLabel,
                    style = type.buttonLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    painter = painterResource(BalarmIcons.ChevronRight),
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(BalarmDimens.Icon),
                )
            }
        }
    }
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun HealthBannerPreview() {
    BalarmTheme {
        Column(
            modifier = Modifier.padding(BalarmDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
        ) {
            HealthBanner(text = "Alarm may not ring", actionLabel = "Fix", onClick = {})
            HealthBanner(text = "Будильник может не сработать", actionLabel = "Исправить", onClick = {})
        }
    }
}

@BalarmComponentLightPreview
@Composable
private fun HealthBannerLightPreview() {
    BalarmTheme(darkTheme = false) {
        HealthBanner(
            text = "Alarm may not ring",
            actionLabel = "Fix",
            onClick = {},
            modifier = Modifier.padding(BalarmDimens.ScreenPadding),
        )
    }
}
