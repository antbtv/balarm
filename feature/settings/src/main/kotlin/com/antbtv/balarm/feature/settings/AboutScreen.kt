package com.antbtv.balarm.feature.settings

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.antbtv.balarm.core.designsystem.component.BalarmTopBar
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * «О приложении» (ADR-014 §5, FR-FLAG-5): название, версия, лицензии. ViewModel нет — версия читается
 * из `PackageManager`.
 *
 * @param onOpenDebugFlags не-`null` только в debug-сборке (`:app` передаёт его из debug source set):
 * 7 тапов подряд по строке версии открывают «Feature flags». `null` (release) — строка версии не кликабельна.
 *
 * Полноэкранный: `safeDrawing` со всех сторон обрабатывает экран; системный Back — у навигации.
 */
@Composable
fun AboutRoute(onClose: () -> Unit, onOpenDebugFlags: (() -> Unit)?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val versionName = remember(context) { context.versionName() }
    AboutScreen(
        versionName = versionName,
        onClose = onClose,
        onOpenDebugFlags = onOpenDebugFlags,
        modifier = modifier,
    )
}

private fun Context.versionName(): String? =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()

/**
 * @param versionName `null` — не удалось прочитать («Версия неизвестна»).
 * @param uptimeMillis источник времени для сброса счётчика тапов; тесты подставляют свои часы.
 * @param windowInsets системные отступы (в приложении — `safeDrawing`); тесты и превью задают свои.
 */
@Composable
internal fun AboutScreen(
    versionName: String?,
    onClose: () -> Unit,
    onOpenDebugFlags: (() -> Unit)?,
    modifier: Modifier = Modifier,
    uptimeMillis: () -> Long = SystemClock::uptimeMillis,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(AboutTestTags.ROOT),
        color = colors.background,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            BalarmTopBar(
                title = stringResource(R.string.about_title),
                onBack = onClose,
                backDescription = stringResource(R.string.settings_back),
            )
            val bottomInset = windowInsets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = BalarmDimens.ScreenPadding,
                        end = BalarmDimens.ScreenPadding,
                        top = BalarmDimens.SpacingSmall,
                        bottom = bottomInset + BalarmDimens.ScreenPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
            ) {
                Text(
                    text = stringResource(R.string.about_app_name),
                    style = type.title,
                    color = colors.textPrimary,
                )
                VersionText(versionName = versionName, onOpenDebugFlags = onOpenDebugFlags, uptimeMillis = uptimeMillis)
                Text(
                    text = stringResource(R.string.about_licenses),
                    style = type.title,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .padding(top = BalarmDimens.ScreenPadding)
                        .semantics { heading() },
                )
                Licenses()
            }
        }
    }
}

@Composable
private fun VersionText(versionName: String?, onOpenDebugFlags: (() -> Unit)?, uptimeMillis: () -> Long) {
    val text = stringResource(
        R.string.about_version,
        versionName ?: stringResource(R.string.about_version_unknown),
    )
    val currentOnOpen by rememberUpdatedState(onOpenDebugFlags)
    val counter = remember { SecretTapCounter() }
    val clickable = if (onOpenDebugFlags != null) {
        // Без роли и подписи: скрытая функция debug-сборки, TalkBack читает строку как обычный текст.
        Modifier.clickable { if (counter.onTap(uptimeMillis())) currentOnOpen?.invoke() }
    } else {
        Modifier
    }
    Text(
        text = text,
        style = BalarmTheme.typography.body,
        color = BalarmTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BalarmDimens.MinTouch)
            .then(clickable)
            .padding(vertical = BalarmDimens.CardGap)
            .testTag(AboutTestTags.VERSION),
    )
}

@Composable
private fun Licenses() {
    Surface(
        shape = BalarmShapes.Card,
        color = BalarmTheme.colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AboutTestTags.LICENSES),
    ) {
        // Сюда же — строки мелодий (M4), из docs/LICENSES.md.
        Column(modifier = Modifier.padding(BalarmDimens.CardPadding)) {
            Text(
                text = stringResource(R.string.about_license_icons),
                style = BalarmTheme.typography.body,
                color = BalarmTheme.colors.textPrimary,
            )
        }
    }
}

/**
 * Счётчик «7 тапов подряд» (FR-FLAG-5). Пауза больше [timeoutMs] между тапами начинает отсчёт заново;
 * после срабатывания счётчик обнуляется. Время передаёт вызывающий — без `Thread.sleep` в тестах.
 */
internal class SecretTapCounter(
    private val required: Int = DEBUG_TAPS,
    private val timeoutMs: Long = DEBUG_TAP_TIMEOUT_MS,
) {
    private var count = 0
    private var lastTapAt = 0L

    /** `true` — это [required]-й тап подряд. */
    fun onTap(now: Long): Boolean {
        if (count > 0 && now - lastTapAt > timeoutMs) count = 0
        count++
        lastTapAt = now
        if (count < required) return false
        count = 0
        return true
    }
}

internal const val DEBUG_TAPS = 7
internal const val DEBUG_TAP_TIMEOUT_MS = 2_000L
