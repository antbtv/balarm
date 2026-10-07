package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Верхняя строка полноэкранного вложенного экрана («Здоровье будильника», «О приложении»): кнопка «Назад»
 * (зона тапа 48dp, стрелка зеркалится в RTL) и заголовок `title`, который при fontScale 2 переносится, а не
 * обрезается. Системные отступы не обрабатывает — экран сам кладёт её под статус-бар.
 *
 * TalkBack: кнопка — [backDescription] («Назад»), заголовок — `heading`.
 */
@Composable
fun BalarmTopBar(title: String, onBack: () -> Unit, backDescription: String, modifier: Modifier = Modifier) {
    val colors = BalarmTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BalarmDimens.ListRowMinHeight)
            .testTag(TopBarTestTags.BAR)
            .padding(horizontal = BalarmDimens.SpacingTiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingTiny),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(BalarmDimens.MinTouch)
                .testTag(TopBarTestTags.BACK),
            colors = IconButtonDefaults.iconButtonColors(contentColor = colors.textPrimary),
        ) {
            Icon(
                painter = painterResource(BalarmIcons.ArrowBack),
                contentDescription = backDescription,
                modifier = Modifier.size(BalarmDimens.Icon),
            )
        }
        Text(
            text = title,
            style = BalarmTheme.typography.title,
            color = colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = BalarmDimens.SpacingSmall)
                .semantics { heading() }
                .testTag(TopBarTestTags.TITLE),
        )
    }
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@BalarmComponentPreviews
@Composable
private fun BalarmTopBarPreview() {
    BalarmTheme { BalarmTopBar(title = "Здоровье будильника", onBack = {}, backDescription = "Назад") }
}
