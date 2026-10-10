package com.antbtv.balarm.feature.alarmedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
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
import com.antbtv.balarm.core.designsystem.component.BalarmTopBar
import com.antbtv.balarm.core.designsystem.component.PrimaryButton
import com.antbtv.balarm.core.designsystem.component.RadioRow
import com.antbtv.balarm.core.designsystem.component.SecondaryButton
import com.antbtv.balarm.core.designsystem.component.SettingRow
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.feature.missions.api.MissionDefinition

/**
 * Строка «Миссия» редактора (FR-EDIT-6): сводка выбранной миссии (`MissionDefinition.summary`) или «Нет»; тап
 * открывает подэкран настройки. Показывается, только если есть доступные миссии
 * ([AlarmEditUiState.missionVisible]).
 */
@Composable
internal fun MissionSection(state: AlarmEditUiState, enabled: Boolean, onEvent: (AlarmEditEvent) -> Unit) {
    val title = stringResource(R.string.alarm_edit_mission)
    val value = missionSummary(state.selectedMission, state.draft.mission?.config)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BalarmDimens.ScreenPadding)
            .testTag(AlarmEditTestTags.MISSION_SECTION),
        shape = BalarmShapes.Card,
        color = BalarmTheme.colors.surface,
    ) {
        SettingRow(
            title = title,
            value = value,
            onClick = { onEvent(AlarmEditEvent.OpenMissionSettings) },
            onClickLabel = stringResource(R.string.alarm_edit_action_change),
            contentDescription = stringResource(R.string.alarm_edit_row_description, title, value),
            enabled = enabled,
            modifier = Modifier.testTag(AlarmEditTestTags.MISSION),
        )
    }
}

@Composable
private fun missionSummary(definition: MissionDefinition?, config: String?): String {
    val none = stringResource(R.string.alarm_edit_mission_none)
    val context = LocalContext.current
    return remember(definition, config, context, none) {
        if (definition == null || config == null) none else definition.summary(context, config)
    }
}

/**
 * Подэкран настройки миссии (FR-MIS-5, ADR-018 §6) — часть редактора, а не отдельный экран навигации: черновик
 * живёт в ViewModel редактора, результат возвращать не нужно. Выбор «Нет / <миссии>» (радиогруппа), настройки
 * выбранной (`MissionDefinition.ConfigEditor`, изменения нормализует редуктор), «Превью» (без звука) и «Готово»
 * снизу. «Назад» в шапке, «Готово» и системный Back возвращают к форме редактора.
 *
 * Edge-to-edge: экран сам обрабатывает [windowInsets] (в приложении — `safeDrawing`).
 */
@Composable
internal fun MissionSettingsScreen(
    state: AlarmEditUiState,
    onEvent: (AlarmEditEvent) -> Unit,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
) {
    val colors = BalarmTheme.colors
    val currentOnEvent by rememberUpdatedState(onEvent)
    val close = remember { { currentOnEvent(AlarmEditEvent.CloseMissionSettings) } }
    val selected = state.selectedMission
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(AlarmEditTestTags.MISSION_SETTINGS),
        color = colors.background,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(windowInsets),
        ) {
            BalarmTopBar(
                title = stringResource(R.string.alarm_edit_mission),
                onBack = close,
                backDescription = stringResource(R.string.alarm_edit_mission_back),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(BalarmDimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(BalarmDimens.ScreenPadding),
            ) {
                Text(
                    text = stringResource(R.string.alarm_edit_mission_hint),
                    style = BalarmTheme.typography.body,
                    color = colors.textSecondary,
                )
                MissionChoices(missions = state.missions, selected = selected, onEvent = onEvent)
                if (selected != null) {
                    MissionConfig(definition = selected, config = state.draft.mission?.config, onEvent = onEvent)
                    SecondaryButton(
                        text = stringResource(R.string.alarm_edit_mission_preview),
                        onClick = { onEvent(AlarmEditEvent.PreviewMission) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(AlarmEditTestTags.MISSION_PREVIEW),
                    )
                }
            }
            DoneButton(onClick = close)
        }
    }
}

/** «Готово» закреплена снизу подэкрана, как «Сохранить» у формы. */
@Composable
private fun DoneButton(onClick: () -> Unit) {
    PrimaryButton(
        text = stringResource(R.string.alarm_edit_mission_done),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BalarmDimens.ScreenPadding,
                end = BalarmDimens.ScreenPadding,
                top = BalarmDimens.SpacingSmall,
                bottom = BalarmDimens.ScreenPadding,
            )
            .testTag(AlarmEditTestTags.MISSION_DONE),
    )
}

/** «Нет» и доступные миссии — одна радиогруппа (TalkBack: «выбрано»/«не выбрано», не только цветом). */
@Composable
private fun MissionChoices(
    missions: List<MissionDefinition>,
    selected: MissionDefinition?,
    onEvent: (AlarmEditEvent) -> Unit,
) {
    val rowPadding = PaddingValues(horizontal = BalarmDimens.CardPadding)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BalarmShapes.Card,
        color = BalarmTheme.colors.surface,
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            RadioRow(
                text = stringResource(R.string.alarm_edit_mission_none),
                selected = selected == null,
                onClick = { onEvent(AlarmEditEvent.MissionSelected(null)) },
                contentPadding = rowPadding,
                modifier = Modifier.testTag(AlarmEditTestTags.MISSION_NONE),
            )
            missions.forEach { definition ->
                RadioRow(
                    text = stringResource(definition.titleRes),
                    selected = selected?.type == definition.type,
                    onClick = { onEvent(AlarmEditEvent.MissionSelected(definition.type)) },
                    contentPadding = rowPadding,
                    modifier = Modifier.testTag(AlarmEditTestTags.missionOption(definition.type.value)),
                )
            }
        }
    }
}

@Composable
private fun MissionConfig(definition: MissionDefinition, config: String?, onEvent: (AlarmEditEvent) -> Unit) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    // Ползунки зовут колбэк на каждом шаге перетаскивания — лямбда без пересоздания.
    val onChange = remember { { value: String -> currentOnEvent(AlarmEditEvent.MissionConfigChanged(value)) } }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AlarmEditTestTags.MISSION_CONFIG),
        shape = BalarmShapes.Card,
        color = BalarmTheme.colors.surface,
    ) {
        definition.ConfigEditor(
            config = config ?: definition.defaultConfig(),
            onChange = onChange,
            modifier = Modifier.padding(vertical = BalarmDimens.SpacingSmall),
        )
    }
}
