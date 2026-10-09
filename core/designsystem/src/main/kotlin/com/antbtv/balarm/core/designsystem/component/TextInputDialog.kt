package com.antbtv.balarm.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Диалог ввода одной строки («Переименовать мелодию»): заголовок, [LabelField] со счётчиком в code points,
 * «Сохранить» и «Отмена». Поле получает фокус при открытии. Текст — у вызывающего ([value]/[onValueChange]),
 * он же решает, можно ли подтвердить ([confirmEnabled], например непустое после `trim`).
 *
 * Фон — `surface` (как у [ConfirmDialog]): акцент `primary` на нём проходит 4.5:1.
 *
 * @param label подпись поля, она же имя поля для TalkBack.
 * @param counterDescription счётчик для TalkBack («12 из 40 символов»); `null` — читается видимое «12/40».
 */
@Composable
fun TextInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLength: Int,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    counterDescription: String? = null,
) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(TextInputDialogTestTags.DIALOG),
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.testTag(TextInputDialogTestTags.CONFIRM),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.primary,
                    disabledContentColor = colors.textSecondary,
                ),
            ) { Text(text = confirmText, style = type.buttonLarge) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TextInputDialogTestTags.DISMISS),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
            ) { Text(text = dismissText, style = type.buttonLarge) }
        },
        title = { Text(text = title, style = type.title, modifier = Modifier.semantics { heading() }) },
        text = {
            LabelField(
                value = value,
                onValueChange = onValueChange,
                label = label,
                maxLength = maxLength,
                counterDescription = counterDescription,
                modifier = Modifier.focusRequester(focusRequester),
            )
            // В слоте диалога: поле уже в той же композиции, что и эффект, — запрос фокуса не опережает его.
            LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
        },
        shape = BalarmShapes.Card,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textPrimary,
    )
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

private const val PREVIEW_MAX_LENGTH = 40

@BalarmComponentPreviews
@Composable
private fun TextInputDialogPreview() {
    BalarmTheme {
        var text by remember { mutableStateOf("Утренняя мотивация") }
        TextInputDialog(
            title = "Переименовать мелодию",
            value = text,
            onValueChange = { text = it },
            label = "Название",
            maxLength = PREVIEW_MAX_LENGTH,
            confirmText = "Сохранить",
            dismissText = "Отмена",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
