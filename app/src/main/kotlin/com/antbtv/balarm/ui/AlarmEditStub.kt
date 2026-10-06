package com.antbtv.balarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.antbtv.balarm.R
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/** Заглушка редактора до M2-T15 (там — `AlarmEditRoute`). Закрытие — системный Back, он снимает запись стека. */
@Composable
internal fun AlarmEditStub(alarmId: Long?, modifier: Modifier = Modifier) {
    val colors = BalarmTheme.colors
    Surface(
        modifier = modifier.fillMaxSize().testTag(AlarmEditStubTags.ROOT),
        color = colors.background,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(BalarmDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(R.string.edit_stub_title), style = BalarmTheme.typography.title)
            Text(
                text = alarmId?.let { stringResource(R.string.edit_stub_existing, it) }
                    ?: stringResource(R.string.edit_stub_new),
                style = BalarmTheme.typography.body,
                color = colors.textSecondary,
                modifier = Modifier.testTag(AlarmEditStubTags.SUBJECT),
            )
        }
    }
}

internal object AlarmEditStubTags {
    const val ROOT = "alarmEditStub_root"
    const val SUBJECT = "alarmEditStub_subject"
}
