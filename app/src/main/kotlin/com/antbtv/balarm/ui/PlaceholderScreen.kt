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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.antbtv.balarm.R
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Экран-заглушка M0: название приложения и статичное время шрифтом `timeLarge` — проверка темы и типографики.
 * Интерактивных элементов нет.
 */
@Composable
fun PlaceholderScreen(time: String, modifier: Modifier = Modifier) {
    val colors = BalarmTheme.colors
    val typography = BalarmTheme.typography
    val timeDescription = stringResource(R.string.placeholder_time_description, time)
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(PlaceholderTestTags.ROOT),
        color = colors.background,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(BalarmDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = typography.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .testTag(PlaceholderTestTags.TITLE)
                    .semantics { heading() },
            )
            Text(
                text = time,
                style = typography.timeLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .testTag(PlaceholderTestTags.TIME)
                    .semantics { contentDescription = timeDescription },
            )
            Text(
                text = stringResource(R.string.placeholder_subtitle),
                style = typography.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(PlaceholderTestTags.SUBTITLE),
            )
        }
    }
}

private const val PREVIEW_TIME = "06:30"

@Preview(name = "Placeholder — dark", widthDp = 360, heightDp = 640)
@Composable
private fun PlaceholderDarkPreview() {
    BalarmTheme(darkTheme = true) { PlaceholderScreen(time = PREVIEW_TIME) }
}

@Preview(name = "Placeholder — light", widthDp = 360, heightDp = 640)
@Composable
private fun PlaceholderLightPreview() {
    BalarmTheme(darkTheme = false) { PlaceholderScreen(time = PREVIEW_TIME) }
}

@Preview(name = "Placeholder — dark, fontScale 2", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun PlaceholderLargeFontPreview() {
    BalarmTheme { PlaceholderScreen(time = PREVIEW_TIME) }
}

@Preview(name = "Placeholder — RU", widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun PlaceholderRuPreview() {
    BalarmTheme { PlaceholderScreen(time = PREVIEW_TIME) }
}
