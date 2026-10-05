package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/** Разделитель счётчика «12/40»: пунктуация, не перевод. */
private const val COUNTER_SEPARATOR = "/"

/**
 * Поле метки будильника (FR-EDIT-4): одна строка, счётчик «12/40» под полем справа.
 *
 * Лимит [maxLength] — в code points (ADR-011 §7, `:core:model` `Alarm.MAX_LABEL_LENGTH` передаёт вызывающий).
 * Переводы строк из вставки заменяются пробелами; при превышении лимита обрезается вставленный фрагмент
 * (без разрыва суррогатной пары), а не хвост метки, и курсор встаёт сразу после принятой части.
 * Отклонённый ввод (набор в заполненном поле) не меняет ни текст, ни курсор.
 * [onValueChange] вызывается только при реальном изменении текста.
 *
 * Выделение и composition IME хранятся внутри (`TextFieldValue`), текст — всегда [value]. Если [value] сменился
 * снаружи (не через [onValueChange]), выделение сохраняется, обрезанное по новой длине, composition сбрасывается —
 * так же ведёт себя `String`-перегрузка `TextField`.
 *
 * @param label подпись поля («Метка»), она же имя для TalkBack.
 * @param placeholder подсказка в пустом поле; `null` — нет.
 * @param counterDescription счётчик для TalkBack («12 из 40 символов»; длину считать через [labelLength]);
 * `null` — читается видимое «12/40».
 */
@Composable
fun LabelField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    counterDescription: String? = null,
) {
    val type = BalarmTheme.typography
    var fieldState by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Текст — всегда внешний value; выделение — своё (TextFieldValue сам обрезает его по длине текста).
    val shown = if (fieldState.text == value) fieldState else fieldState.copy(text = value, composition = null)
    val currentShown by rememberUpdatedState(shown)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val onInput = remember(maxLength) {
        { input: TextFieldValue ->
            val previous = currentShown
            val edit = sanitizeLabelEdit(previous.text, input.text, maxLength)
            // Отклонённый ввод (текст не изменился после обрезки) — курсор остаётся на месте.
            val next = when {
                edit.cursor == null -> input
                edit.text == previous.text -> previous
                else -> TextFieldValue(edit.text, TextRange(edit.cursor))
            }
            fieldState = next
            if (next.text != previous.text) currentOnValueChange(next.text)
        }
    }
    OutlinedTextField(
        value = shown,
        onValueChange = onInput,
        modifier = modifier
            .fillMaxWidth()
            .testTag(LabelFieldTestTags.FIELD),
        textStyle = type.body,
        label = { Text(text = label, style = type.body) },
        placeholder = placeholder?.let { { Text(text = it, style = type.body) } },
        supportingText = {
            LabelCounter(count = labelLength(value), maxLength = maxLength, description = counterDescription)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        shape = BalarmShapes.Button,
        colors = labelFieldColors(),
    )
}

@Composable
private fun LabelCounter(count: Int, maxLength: Int, description: String?) {
    Text(
        text = "$count$COUNTER_SEPARATOR$maxLength",
        style = BalarmTheme.typography.caption,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LabelFieldTestTags.COUNTER)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    )
}

@Composable
private fun labelFieldColors(): TextFieldColors {
    val colors = BalarmTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        cursorColor = colors.primary,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.textSecondary,
        focusedLabelColor = colors.textPrimary,
        unfocusedLabelColor = colors.textSecondary,
        focusedPlaceholderColor = colors.textSecondary,
        unfocusedPlaceholderColor = colors.textSecondary,
        focusedSupportingTextColor = colors.textSecondary,
        unfocusedSupportingTextColor = colors.textSecondary,
    )
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

private const val PREVIEW_MAX_LENGTH = 40

@Composable
private fun PreviewLabelField(initial: String, label: String, placeholder: String) {
    var text by remember { mutableStateOf(initial) }
    LabelField(
        value = text,
        onValueChange = { text = it },
        label = label,
        maxLength = PREVIEW_MAX_LENGTH,
        placeholder = placeholder,
    )
}

@BalarmComponentPreviews
@Composable
private fun LabelFieldPreview() {
    BalarmTheme {
        Column(
            modifier = Modifier.padding(BalarmDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
        ) {
            PreviewLabelField(initial = "", label = "Label", placeholder = "Wake up")
            PreviewLabelField(initial = "Тренировка 💪", label = "Метка", placeholder = "Подъём")
            PreviewLabelField(
                initial = "Очень длинная метка ровно на сорок симв",
                label = "Метка",
                placeholder = "Подъём",
            )
        }
    }
}
