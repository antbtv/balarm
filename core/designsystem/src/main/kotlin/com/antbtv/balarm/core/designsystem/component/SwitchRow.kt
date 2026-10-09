package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Строка настройки с переключателем («Вибрация»): [title] слева, переключатель с токенами `BalarmSwitch` справа.
 * Переключает тап по **всей** строке (высота ≥ 56dp); сам `Switch` без своего обработчика — один узел TalkBack
 * с ролью Switch, читается «[title], вкл/выкл». При fontScale 2 заголовок переносится, переключатель не уезжает.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = BalarmTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BalarmDimens.ListRowMinHeight)
            .testTag(SwitchRowTestTags.ROW)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = BalarmDimens.CardPadding, vertical = BalarmDimens.SpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
    ) {
        Text(
            text = title,
            style = BalarmTheme.typography.body,
            color = if (enabled) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = balarmSwitchColors())
    }
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun SwitchRowPreview() {
    BalarmTheme {
        var checked by remember { mutableStateOf(true) }
        Surface(
            modifier = Modifier.padding(BalarmDimens.ScreenPadding),
            shape = BalarmShapes.Card,
            color = BalarmTheme.colors.surface,
        ) {
            Column {
                SwitchRow(title = "Vibration", checked = checked, onCheckedChange = { checked = it })
                SwitchRow(title = "Вибрация при звонке будильника", checked = false, onCheckedChange = {})
                SwitchRow(title = "Disabled", checked = true, onCheckedChange = {}, enabled = false)
            }
        }
    }
}
