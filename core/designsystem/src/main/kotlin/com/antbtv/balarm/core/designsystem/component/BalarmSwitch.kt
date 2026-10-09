package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Переключатель Balarm: трек `primary` при вкл, `surfaceVariant` при выкл.
 *
 * Зона тапа ≥ 48dp обеспечивает сам M3 `Switch` (`minimumInteractiveComponentSize`), роль Switch и состояние
 * вкл/выкл TalkBack получает от `toggleable`; [contentDescription] — что именно переключаем
 * («Будильник 07:30»), состояние не дублировать.
 */
@Composable
fun BalarmSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        // Сам узел ≥ 48×48 (трек 52×32 по центру), а не только расширенная зона касания.
        modifier = modifier
            .sizeIn(minWidth = BalarmDimens.MinTouch, minHeight = BalarmDimens.MinTouch)
            .semantics { this.contentDescription = contentDescription },
        enabled = enabled,
        colors = balarmSwitchColors(),
    )
}

@Composable
internal fun balarmSwitchColors(): SwitchColors {
    val colors = BalarmTheme.colors
    return SwitchDefaults.colors(
        checkedThumbColor = colors.onPrimary,
        checkedTrackColor = colors.primary,
        checkedBorderColor = colors.primary,
        checkedIconColor = colors.primary,
        uncheckedThumbColor = colors.textSecondary,
        uncheckedTrackColor = colors.surfaceVariant,
        uncheckedBorderColor = colors.textSecondary,
        uncheckedIconColor = colors.surfaceVariant,
        disabledCheckedThumbColor = colors.textSecondary,
        disabledCheckedTrackColor = colors.surfaceVariant,
        disabledCheckedBorderColor = colors.surfaceVariant,
        disabledUncheckedThumbColor = colors.surfaceVariant,
        disabledUncheckedTrackColor = colors.surface,
        disabledUncheckedBorderColor = colors.surfaceVariant,
    )
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun BalarmSwitchPreview() {
    BalarmTheme {
        Column(modifier = Modifier.padding(BalarmDimens.ScreenPadding)) {
            BalarmSwitch(checked = true, onCheckedChange = {}, contentDescription = "On")
            BalarmSwitch(checked = false, onCheckedChange = {}, contentDescription = "Off")
            BalarmSwitch(checked = true, onCheckedChange = {}, contentDescription = "Disabled", enabled = false)
        }
    }
}
