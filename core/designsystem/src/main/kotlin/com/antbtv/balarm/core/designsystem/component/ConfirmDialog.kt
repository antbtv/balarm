package com.antbtv.balarm.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Диалог подтверждения («Удалить будильник?»). Фон — `surface`: на нём акцент `primary` проходит 4.5:1
 * в обеих темах (на `surfaceVariant` в тёмной — нет, см. `ContrastTest`).
 *
 * @param destructive подтверждение необратимого действия — кнопка цветом `primary` (он же `error` в схеме M3).
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(ConfirmDialogTestTags.DIALOG),
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(ConfirmDialogTestTags.CONFIRM),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (destructive) colors.primary else colors.textPrimary,
                ),
            ) { Text(text = confirmText, style = type.buttonLarge) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(ConfirmDialogTestTags.DISMISS),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
            ) { Text(text = dismissText, style = type.buttonLarge) }
        },
        title = { Text(text = title, style = type.title) },
        text = { Text(text = text, style = type.body) },
        shape = BalarmShapes.Card,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
    )
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun ConfirmDialogPreview() {
    BalarmTheme {
        ConfirmDialog(
            title = "Delete alarm?",
            text = "Alarm 06:30 will be deleted.",
            confirmText = "Delete",
            dismissText = "Cancel",
            onConfirm = {},
            onDismiss = {},
            destructive = true,
        )
    }
}
