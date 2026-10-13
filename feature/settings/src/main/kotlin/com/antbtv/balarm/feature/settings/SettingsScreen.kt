package com.antbtv.balarm.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antbtv.balarm.core.designsystem.component.BalarmIcons
import com.antbtv.balarm.core.designsystem.component.BalarmSnackbarHost
import com.antbtv.balarm.core.designsystem.component.ScrimEdge
import com.antbtv.balarm.core.designsystem.component.SettingRow
import com.antbtv.balarm.core.designsystem.component.SystemBarScrim
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/**
 * Вкладка «Настройки» с ViewModel (ADR-014 §5). Сводка здоровья перечитывается на каждом `ON_RESUME`.
 *
 * Insets: экран сам обрабатывает `WindowInsets.safeDrawing` (верх и бока). Это корень вкладки, под ним — нижняя
 * панель навигации: `:app` передаёт её высоту через [modifier] так же, как списку будильников, —
 * `Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)` (ADR-014 §4). После `consumeWindowInsets`
 * нижний `navigationBars` внутри экрана равен 0, и отступ не удваивается. Отдельного параметра отступа нет.
 */
@Composable
fun SettingsRoute(
    onOpenHealth: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSounds: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    SettingsScreen(
        state = state,
        onOpenHealth = onOpenHealth,
        onOpenAbout = onOpenAbout,
        onOpenSounds = onOpenSounds,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

/**
 * Вкладка «Настройки»: заголовок, карточка «Здоровье будильника» со сводкой (✅ «Всё в порядке» / ⚠️ «N проблем»)
 * и «Мелодии» (если [SettingsUiState.soundsVisible]), секция «Общие» (если [SettingsUiState.generalVisible],
 * ADR-021), карточка «О приложении». Пока сводка считается — строка без значения и иконки: «Всё в порядке» не
 * мигает перед «2 проблемы». Ошибка сохранения настройки — снекбар внизу.
 *
 * @param windowInsets системные отступы (в приложении — `safeDrawing`); тесты и превью задают свои.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onOpenHealth: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSounds: () -> Unit,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val colors = BalarmTheme.colors
    val snackbar = remember { SnackbarHostState() }
    SaveFailedEffect(saveFailed = state.saveFailed, hostState = snackbar, onEvent = onEvent)
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(SettingsTestTags.ROOT),
        color = colors.background,
        contentColor = colors.textPrimary,
    ) {
        val vertical = windowInsets.only(WindowInsetsSides.Vertical).asPaddingValues()
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = BalarmDimens.ScreenPadding,
                        end = BalarmDimens.ScreenPadding,
                        top = vertical.calculateTopPadding() + BalarmDimens.ScreenPadding,
                        bottom = vertical.calculateBottomPadding() + BalarmDimens.ScreenPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(BalarmDimens.ScreenPadding),
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = BalarmTheme.typography.title,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() },
                )
                SettingsItems(
                    state = state,
                    onOpenHealth = onOpenHealth,
                    onOpenAbout = onOpenAbout,
                    onOpenSounds = onOpenSounds,
                    onEvent = onEvent,
                )
            }
            // При fontScale 2 на низком экране контент прокручивается под прозрачный статус-бар.
            SystemBarScrim(
                edge = ScrimEdge.Top,
                inset = vertical.calculateTopPadding(),
                modifier = Modifier.align(Alignment.TopCenter),
            )
            BalarmSnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            )
        }
    }
}

/** Карточка «Здоровье»/«Мелодии», секция «Общие» (по флагу), карточка «О приложении». */
@Composable
private fun SettingsItems(
    state: SettingsUiState,
    onOpenHealth: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSounds: () -> Unit,
    onEvent: (SettingsEvent) -> Unit,
) {
    val openLabel = stringResource(R.string.settings_action_open)
    SettingsCard {
        HealthRow(state = state, onClick = onOpenHealth)
        if (state.soundsVisible) {
            SettingRow(
                title = stringResource(R.string.settings_sounds),
                value = null,
                onClick = onOpenSounds,
                onClickLabel = openLabel,
                modifier = Modifier.testTag(SettingsTestTags.SOUNDS_ROW),
            )
        }
    }
    if (state.generalVisible) {
        GeneralSettingsSection(general = state.general, onEvent = onEvent)
    }
    SettingsCard {
        SettingRow(
            title = stringResource(R.string.settings_about),
            value = null,
            onClick = onOpenAbout,
            onClickLabel = openLabel,
            modifier = Modifier.testTag(SettingsTestTags.ABOUT_ROW),
        )
    }
}

/** Показывает снекбар «Не удалось сохранить настройку»; когда он скрылся — [SettingsEvent.SaveFailedShown]. */
@Composable
private fun SaveFailedEffect(saveFailed: Boolean, hostState: SnackbarHostState, onEvent: (SettingsEvent) -> Unit) {
    val message = stringResource(R.string.settings_save_failed)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(saveFailed, hostState, message) {
        if (!saveFailed) return@LaunchedEffect
        hostState.showSnackbar(message, duration = SnackbarDuration.Short)
        currentOnEvent(SettingsEvent.SaveFailedShown)
    }
}

/** Карточка-группа строк настроек. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(shape = BalarmShapes.Card, color = BalarmTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun HealthRow(state: SettingsUiState, onClick: () -> Unit) {
    val colors = BalarmTheme.colors
    val summary = when {
        state.loading -> null
        state.problems == 0 -> stringResource(R.string.settings_health_ok)
        else -> pluralStringResource(R.plurals.settings_health_problems, state.problems, state.problems)
    }
    val hasProblems = !state.loading && state.problems > 0
    // Мелкий текст цветом warning проходит 4.5:1 только на тёмной поверхности; в светлой теме — основной цвет
    // текста, проблему всё равно показывают форма иконки (треугольник) и сами слова.
    val summaryColor = when {
        hasProblems && colors.isDark -> colors.warning
        hasProblems -> colors.textPrimary
        else -> Color.Unspecified
    }
    SettingRow(
        title = stringResource(R.string.settings_health),
        value = summary,
        onClick = onClick,
        onClickLabel = stringResource(R.string.settings_action_open),
        icon = when {
            state.loading -> null
            hasProblems -> BalarmIcons.StatusWarning
            else -> BalarmIcons.StatusOk
        },
        iconTint = if (hasProblems) colors.warning else colors.success,
        valueColor = summaryColor,
        modifier = Modifier.testTag(SettingsTestTags.HEALTH_ROW),
    )
}
