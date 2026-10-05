package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Диалог выбора одного варианта из списка (интервал/лимит snooze в M2, fade-in в M4, уровень Math в M5).
 *
 * Тап по варианту сразу вызывает [onSelect] с его индексом — подтверждения нет (как системный single-choice);
 * закрыть диалог после выбора — дело вызывающего. Длинный список (или fontScale 2) прокручивается внутри диалога.
 *
 * TalkBack: варианты — группа радиокнопок (`selectableGroup`, Role.RadioButton), выбран [selectedIndex].
 *
 * @param options подписи вариантов (готовые строки: «5 мин», «Без ограничений»).
 * @param selectedIndex индекс выбранного варианта; вне `options.indices` — ничего не выбрано.
 * @param dismissText подпись кнопки отмены («Отмена»).
 */
@Composable
fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (index: Int) -> Unit,
    onDismiss: () -> Unit,
    dismissText: String,
    modifier: Modifier = Modifier,
) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(SingleChoiceDialogTestTags.DIALOG),
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(SingleChoiceDialogTestTags.DISMISS),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
            ) { Text(text = dismissText, style = type.buttonLarge) }
        },
        title = { Text(text = title, style = type.title, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                modifier = Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEachIndexed { index, option ->
                    ChoiceRow(
                        index = index,
                        text = option,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                    )
                }
            }
        },
        shape = BalarmShapes.Card,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textPrimary,
    )
}

@Composable
private fun ChoiceRow(index: Int, text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BalarmTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BalarmDimens.ListRowMinHeight)
            .testTag(SingleChoiceDialogTestTags.option(index))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
    ) {
        // onClick = null: строка целиком — одна радиокнопка для TalkBack и тапа.
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.primary,
                unselectedColor = colors.textSecondary,
            ),
        )
        Text(text = text, style = BalarmTheme.typography.body, modifier = Modifier.weight(1f))
    }
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun SingleChoiceDialogPreview() {
    BalarmTheme {
        SingleChoiceDialog(
            title = "Snooze interval",
            options = listOf("1 min", "3 min", "5 min", "10 min", "15 min", "20 min", "30 min"),
            selectedIndex = 2,
            onSelect = {},
            onDismiss = {},
            dismissText = "Cancel",
        )
    }
}

@BalarmComponentPreviews
@Composable
private fun SingleChoiceDialogRuPreview() {
    BalarmTheme {
        SingleChoiceDialog(
            title = "Количество повторов",
            options = listOf("1", "2", "3", "5", "10", "Без ограничений"),
            selectedIndex = 5,
            onSelect = {},
            onDismiss = {},
            dismissText = "Отмена",
        )
    }
}
