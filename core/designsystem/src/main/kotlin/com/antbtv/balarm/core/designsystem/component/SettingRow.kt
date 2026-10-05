package com.antbtv.balarm.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/** Значение занимает не больше этой доли ширины строки. */
private const val VALUE_MAX_WIDTH_FRACTION = 0.5f

/**
 * Строка настройки в редакторе: [title] слева, [value] справа цветом `textSecondary`, chevron. Кликабельна вся
 * строка, высота ≥ 56dp. Заголовок занимает всё место, кроме значения; значение — не шире половины строки.
 * При fontScale 2 на 360dp заголовок и значение переносятся по словам, chevron не уезжает.
 *
 * TalkBack: строка — одна кнопка, читается «[title], [value]» (или [contentDescription], если задано),
 * действие — [onClickLabel] («Изменить»).
 *
 * @param icon необязательная иконка слева (`BalarmIcons`), декоративная.
 * @param value текущее значение («5 мин»); `null` — не показывается.
 * @param contentDescription замена текста строки для TalkBack (например, развёрнутое «5 минут» вместо «5 мин»).
 */
@Composable
fun SettingRow(
    title: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    onClickLabel: String? = null,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    val contentColor = if (enabled) colors.textPrimary else colors.textSecondary
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BalarmDimens.ListRowMinHeight)
            .testTag(SettingRowTestTags.ROW)
            .clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .then(
                if (contentDescription != null) {
                    Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = BalarmDimens.CardPadding, vertical = BalarmDimens.SpacingSmall),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Значение не шире половины строки, остальное — заголовку (короткое значение не сжимает заголовок).
        val valueMaxWidth = maxWidth * VALUE_MAX_WIDTH_FRACTION
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(BalarmDimens.Icon),
                )
            }
            Text(text = title, style = type.body, color = contentColor, modifier = Modifier.weight(1f))
            if (value != null) {
                Text(
                    text = value,
                    style = type.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = valueMaxWidth),
                )
            }
            Icon(
                painter = painterResource(BalarmIcons.ChevronRight),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(BalarmDimens.Icon),
            )
        }
    }
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun SettingRowPreview() {
    BalarmTheme {
        Surface(
            modifier = Modifier.padding(BalarmDimens.ScreenPadding),
            shape = BalarmShapes.Card,
            color = BalarmTheme.colors.surface,
        ) {
            Column {
                SettingRow(title = "Snooze interval", value = "5 min", onClick = {})
                SettingRow(title = "Количество повторов", value = "Без ограничений", onClick = {})
                SettingRow(title = "Mission", value = null, onClick = {}, icon = BalarmIcons.Keyboard)
                SettingRow(title = "Disabled", value = "Off", onClick = {}, enabled = false)
            }
        }
    }
}
