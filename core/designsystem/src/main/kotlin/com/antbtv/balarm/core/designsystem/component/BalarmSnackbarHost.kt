package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Сообщение о результате действия внизу экрана («Мелодия добавлена», «Файл больше 20 МБ»): `surfaceVariant`,
 * текст `body` цветом `textPrimary` (контраст ≥ 4.5:1 в обеих темах), переносится при fontScale 2.
 * TalkBack объявляет его сам (M3 `Snackbar` — live region). Системные отступы не обрабатывает — экран кладёт
 * хост над своей нижней кнопкой/панелью.
 */
@Composable
fun BalarmSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        BalarmSnackbar(text = data.visuals.message)
    }
}

@Composable
private fun BalarmSnackbar(text: String) {
    val colors = BalarmTheme.colors
    Snackbar(
        modifier = Modifier
            .padding(horizontal = BalarmDimens.ScreenPadding, vertical = BalarmDimens.SpacingSmall)
            .testTag(SnackbarTestTags.SNACKBAR),
        shape = BalarmShapes.Button,
        containerColor = colors.surfaceVariant,
        contentColor = colors.textPrimary,
    ) {
        Text(text = text, style = BalarmTheme.typography.body)
    }
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun BalarmSnackbarPreview() {
    BalarmTheme { BalarmSnackbar(text = "Файл больше 20 МБ") }
}
