package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Шапка списка: «Следующий будильник через 7 ч 12 мин» / «Нет активных будильников» и подпись под ней.
 * Строки готовит вызывающий. Для TalkBack — один узел-заголовок без дочерних: «<заголовок>. <подпись>»,
 * подпись не теряется.
 *
 * @param title заголовок («Следующий будильник через 7 ч 12 мин»).
 * @param subtitle подпись под заголовком («Завтра, 06:30»); `null` — нет.
 * @param titleDescription полная форма заголовка для TalkBack («через 7 часов 12 минут»), `null` — читается [title].
 */
@Composable
fun NextAlarmHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    titleDescription: String? = null,
) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    val description = remember(title, subtitle, titleDescription) {
        nextAlarmHeaderDescription(titleDescription ?: title, subtitle)
    }
    Column(
        modifier = modifier
            .testTag(NextAlarmHeaderTestTags.HEADER)
            .clearAndSetSemantics {
                heading()
                contentDescription = description
            },
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingTiny),
    ) {
        Text(text = title, color = colors.textPrimary, style = type.title)
        if (subtitle != null) {
            Text(text = subtitle, color = colors.textSecondary, style = type.caption)
        }
    }
}

/** Пауза между заголовком и подписью для TalkBack (пунктуация, не перевод). */
private const val DESCRIPTION_SEPARATOR = ". "

internal fun nextAlarmHeaderDescription(title: String, subtitle: String?): String =
    if (subtitle.isNullOrBlank()) title else title + DESCRIPTION_SEPARATOR + subtitle

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun NextAlarmHeaderPreview() {
    BalarmTheme {
        Column(
            modifier = Modifier.padding(BalarmDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
        ) {
            NextAlarmHeader(title = "Next alarm in 7 h 12 min", subtitle = "Tomorrow, 06:30")
            NextAlarmHeader(title = "No active alarms", subtitle = null)
        }
    }
}
