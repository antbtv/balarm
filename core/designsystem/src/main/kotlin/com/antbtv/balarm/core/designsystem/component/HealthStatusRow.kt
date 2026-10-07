package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.antbtv.balarm.core.designsystem.R
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentLightPreview
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmColors
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Строка экрана «Здоровье будильника» (FR-REL-7): иконка состояния ✅/⚠️/? (разная форма, не только цвет —
 * WCAG 1.4.1), заголовок, описание и, если задано [actionLabel], кнопка действия («Исправить») под текстом.
 * Строки готовит вызывающий; слово состояния для TalkBack — из ресурсов дизайн-системы (RU/EN).
 *
 * TalkBack: текстовая часть — один узел «<заголовок>. <состояние>. <описание>»; кнопка — отдельный узел
 * «<действие>: <заголовок>» («Исправить: Уведомления»), чтобы в списке из нескольких «Исправить» было понятно,
 * что именно исправляется.
 *
 * @param description пояснение («Без уведомлений экран звонка не появится»); `null` — нет.
 * @param actionLabel подпись кнопки; `null` — кнопки нет (обычно у [HealthStatusUi.Ok]).
 * @param onAction нажатие на кнопку действия.
 */
@Composable
fun HealthStatusRow(
    title: String,
    description: String?,
    status: HealthStatusUi,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    val statusLabel = stringResource(status.label)
    val infoDescription = remember(title, statusLabel, description) {
        joinForTalkBack(title, statusLabel, description)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BalarmDimens.ListRowMinHeight)
            .testTag(HealthStatusRowTestTags.ROW)
            .padding(horizontal = BalarmDimens.CardPadding, vertical = BalarmDimens.SpacingSmall),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HealthStatusRowTestTags.INFO)
                .clearAndSetSemantics { contentDescription = infoDescription },
            horizontalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
        ) {
            Icon(
                painter = painterResource(status.icon),
                contentDescription = null,
                tint = colors.statusTint(status),
                modifier = Modifier.size(BalarmDimens.Icon),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingTiny),
            ) {
                Text(text = title, style = type.body, color = colors.textPrimary)
                if (description != null) {
                    Text(text = description, style = type.caption, color = colors.textSecondary)
                }
            }
        }
        if (actionLabel != null) {
            val actionDescription = stringResource(R.string.designsystem_health_status_action, actionLabel, title)
            SecondaryButton(
                text = actionLabel,
                onClick = onAction,
                minHeight = BalarmDimens.MinTouch,
                modifier = Modifier
                    // Кнопка выровнена по тексту, а не по иконке.
                    .padding(start = BalarmDimens.Icon + BalarmDimens.CardGap)
                    .testTag(HealthStatusRowTestTags.ACTION)
                    .semantics { contentDescription = actionDescription },
            )
        }
    }
}

private fun BalarmColors.statusTint(status: HealthStatusUi): Color = when (status) {
    HealthStatusUi.Ok -> success
    HealthStatusUi.Problem -> warning
    HealthStatusUi.Unconfirmed -> textSecondary
}

/** Пауза между частями описания для TalkBack (пунктуация, не перевод). */
private const val DESCRIPTION_SEPARATOR = ". "

internal fun joinForTalkBack(vararg parts: String?): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(DESCRIPTION_SEPARATOR)

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@Composable
private fun HealthStatusSheet() {
    Surface(
        modifier = Modifier.padding(BalarmDimens.ScreenPadding),
        shape = BalarmShapes.Card,
        color = BalarmTheme.colors.surface,
    ) {
        Column {
            HealthStatusRow(
                title = "Notifications",
                description = "Without notifications the ringing screen cannot appear over the lock screen.",
                status = HealthStatusUi.Problem,
                actionLabel = "Fix",
                onAction = {},
            )
            HealthStatusRow(
                title = "Точные будильники",
                description = "Нужно, чтобы будильник звонил минута в минуту.",
                status = HealthStatusUi.Ok,
                actionLabel = null,
                onAction = {},
            )
            HealthStatusRow(
                title = "Автозапуск и фоновая работа",
                description = null,
                status = HealthStatusUi.Unconfirmed,
                actionLabel = "Открыть настройки",
                onAction = {},
            )
        }
    }
}

@BalarmComponentPreviews
@Composable
private fun HealthStatusRowPreview() {
    BalarmTheme { HealthStatusSheet() }
}

@BalarmComponentLightPreview
@Composable
private fun HealthStatusRowLightPreview() {
    BalarmTheme(darkTheme = false) { HealthStatusSheet() }
}
