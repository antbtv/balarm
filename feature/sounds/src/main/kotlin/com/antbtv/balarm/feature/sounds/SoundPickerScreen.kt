package com.antbtv.balarm.feature.sounds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antbtv.balarm.core.designsystem.component.BalarmIcons
import com.antbtv.balarm.core.designsystem.component.BalarmSnackbarHost
import com.antbtv.balarm.core.designsystem.component.BalarmTopBar
import com.antbtv.balarm.core.designsystem.component.PrimaryButton
import com.antbtv.balarm.core.designsystem.component.ScrimEdge
import com.antbtv.balarm.core.designsystem.component.SecondaryButton
import com.antbtv.balarm.core.designsystem.component.SystemBarScrim
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.core.format.titleRes
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.SoundRef

/**
 * Пикер мелодии (FR-SND-2, ADR-016 §8): «Встроенные» и «Мои мелодии». Тап по строке — выбор и прослушивание,
 * по играющей — стоп; «Выбрать» отдаёт выбор [onPicked] и закрывает экран ([onClose]).
 *
 * Полноэкранный: `safeDrawing` обрабатывает сам; системный Back — у навигации (превью замолкает в `onCleared`
 * ViewModel и на `ON_STOP`). [onOpenLibrary] — «Управлять» в блоке «Мои мелодии» (только при
 * `feature.customSounds`).
 *
 * @param selected текущая мелодия будильника — отмечена при открытии.
 */
@Composable
fun SoundPickerRoute(
    selected: SoundRef,
    onPicked: (SoundRef) -> Unit,
    onClose: () -> Unit,
    onOpenLibrary: () -> Unit = {},
) {
    val encoded = selected.encode()
    val viewModel = hiltViewModel<SoundPickerViewModel, SoundPickerViewModel.Factory>(
        creationCallback = { factory -> factory.create(encoded) },
    )
    SoundPickerRoute(viewModel = viewModel, onPicked = onPicked, onClose = onClose, onOpenLibrary = onOpenLibrary)
}

/** То же с готовой ViewModel: тесты собирают её на фейках без Hilt. */
@Composable
internal fun SoundPickerRoute(
    viewModel: SoundPickerViewModel,
    onPicked: (SoundRef) -> Unit,
    onClose: () -> Unit,
    onOpenLibrary: () -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentState by rememberUpdatedState(state)
    val currentOnPicked by rememberUpdatedState(onPicked)
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenLibrary by rememberUpdatedState(onOpenLibrary)
    // Свернули/ушли с экрана — превью замолкает (ADR-017 §6).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onEvent(SoundPickerEvent.StopPreview) }
    val launchImport = rememberImportLauncher(
        onPicked = { viewModel.onEvent(SoundPickerEvent.ImportPicked(it)) },
        onUnavailable = { viewModel.onEvent(SoundPickerEvent.ImportUnavailable) },
    )
    val actions = remember(viewModel, launchImport) {
        SoundPickerActions(
            onConfirm = {
                viewModel.onEvent(SoundPickerEvent.StopPreview)
                currentOnPicked(currentState.selected)
                currentOnClose()
            },
            onClose = {
                viewModel.onEvent(SoundPickerEvent.StopPreview)
                currentOnClose()
            },
            onAddSound = {
                viewModel.onEvent(SoundPickerEvent.StopPreview)
                launchImport()
            },
            onOpenLibrary = {
                viewModel.onEvent(SoundPickerEvent.StopPreview)
                currentOnOpenLibrary()
            },
        )
    }
    SoundPickerScreen(state = state, onEvent = viewModel::onEvent, actions = actions, windowInsets = windowInsets)
}

/** Действия экрана, которые уходят наружу (навигация, SAF); одним стабильным объектом — без лямбд на рекомпозицию. */
internal class SoundPickerActions(
    val onConfirm: () -> Unit,
    val onClose: () -> Unit,
    val onAddSound: () -> Unit,
    val onOpenLibrary: () -> Unit,
)

/**
 * Список мелодий, «Выбрать» закреплена снизу. Своя мелодия, отмеченная при открытии, но удалённая, — подсказка
 * «Выбранная мелодия удалена» и «Выбрать» недоступна, пока не выбрана другая.
 *
 * @param windowInsets системные отступы (в приложении — `safeDrawing`); тесты и превью задают свои.
 */
@Composable
internal fun SoundPickerScreen(
    state: SoundPickerUiState,
    onEvent: (SoundPickerEvent) -> Unit,
    actions: SoundPickerActions,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val colors = BalarmTheme.colors
    val snackbar = remember { SnackbarHostState() }
    SoundMessageEffect(message = state.message, hostState = snackbar) { onEvent(SoundPickerEvent.MessageShown) }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(SoundsTestTags.PICKER),
        color = colors.background,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            BalarmTopBar(
                title = stringResource(R.string.sounds_picker_title),
                onBack = actions.onClose,
                backDescription = stringResource(R.string.sounds_back),
            )
            if (state.importing) ImportProgress()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                PickerList(state = state, onEvent = onEvent, actions = actions)
                // Список уходит под «Выбрать» мягко, как в редакторе.
                SystemBarScrim(edge = ScrimEdge.Bottom, inset = 0.dp, modifier = Modifier.align(Alignment.BottomCenter))
                BalarmSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
            }
            PrimaryButton(
                text = stringResource(R.string.sounds_choose),
                onClick = actions.onConfirm,
                enabled = state.canConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom))
                    .padding(
                        start = BalarmDimens.ScreenPadding,
                        end = BalarmDimens.ScreenPadding,
                        top = BalarmDimens.SpacingSmall,
                        bottom = BalarmDimens.ScreenPadding,
                    )
                    .testTag(SoundsTestTags.CONFIRM),
            )
        }
    }
}

@Composable
private fun PickerList(state: SoundPickerUiState, onEvent: (SoundPickerEvent) -> Unit, actions: SoundPickerActions) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .selectableGroup()
            .testTag(SoundsTestTags.LIST),
        contentPadding = PaddingValues(bottom = BalarmDimens.ScreenPadding),
    ) {
        if (state.selectedMissing && state.customs != null) {
            item(key = "missing") { SelectedMissingHint() }
        }
        item(key = "builtinHeader") {
            SectionHeader(text = stringResource(R.string.sounds_builtin_header), tag = SoundsTestTags.BUILTIN_HEADER)
        }
        items(BUILTIN_SOUNDS, key = { it.key }) { sound ->
            val ref = SoundRef.Builtin(sound)
            SoundRow(
                title = stringResource(sound.titleRes()),
                details = null,
                selected = state.selected == ref,
                playing = state.playing == ref,
                onClick = { onEvent(SoundPickerEvent.RowClicked(ref)) },
                modifier = Modifier.testTag(SoundsTestTags.builtin(sound)),
            )
        }
        if (state.customBlockVisible) customSection(state = state, onEvent = onEvent, actions = actions)
    }
}

private val BUILTIN_SOUNDS: List<BuiltinSound> = BuiltinSound.entries

private fun LazyListScope.customSection(
    state: SoundPickerUiState,
    onEvent: (SoundPickerEvent) -> Unit,
    actions: SoundPickerActions,
) {
    item(key = "customHeader") {
        CustomHeader(showManage = state.customSoundsEnabled, onManage = actions.onOpenLibrary)
    }
    val customs = state.customs.orEmpty()
    items(customs, key = { it.id.value }) { sound ->
        val ref = SoundRef.Custom(sound.id)
        SoundRow(
            title = sound.title,
            details = rememberSoundDetails(sound),
            selected = state.selected == ref,
            playing = state.playing == ref,
            onClick = { onEvent(SoundPickerEvent.RowClicked(ref)) },
            modifier = Modifier.testTag(SoundsTestTags.custom(sound.id)),
        )
    }
    if (!state.customSoundsEnabled) return
    if (state.customs != null && customs.isEmpty()) {
        item(key = "customEmpty") { CustomEmptyHint() }
    }
    item(key = "add") {
        SecondaryButton(
            text = stringResource(if (state.importing) R.string.sounds_importing else R.string.sounds_add),
            onClick = actions.onAddSound,
            enabled = !state.importing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BalarmDimens.ScreenPadding, vertical = BalarmDimens.SpacingSmall)
                .testTag(SoundsTestTags.ADD),
        )
    }
}

@Composable
private fun SectionHeader(text: String, tag: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = BalarmTheme.typography.captionStrong,
        color = BalarmTheme.colors.textSecondary,
        modifier = modifier
            .padding(
                start = BalarmDimens.ScreenPadding,
                end = BalarmDimens.ScreenPadding,
                top = BalarmDimens.ScreenPadding,
                bottom = BalarmDimens.SpacingSmall,
            )
            .semantics { heading() }
            .testTag(tag),
    )
}

@Composable
private fun CustomHeader(showManage: Boolean, onManage: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = BalarmDimens.SpacingSmall),
        verticalAlignment = Alignment.Bottom,
    ) {
        SectionHeader(
            text = stringResource(R.string.sounds_custom_header),
            tag = SoundsTestTags.CUSTOM_HEADER,
            modifier = Modifier.weight(1f),
        )
        if (showManage) {
            TextButton(
                onClick = onManage,
                modifier = Modifier
                    .heightIn(min = BalarmDimens.MinTouch)
                    .testTag(SoundsTestTags.MANAGE),
                colors = ButtonDefaults.textButtonColors(contentColor = BalarmTheme.colors.primary),
            ) { Text(text = stringResource(R.string.sounds_manage), style = BalarmTheme.typography.body) }
        }
    }
}

@Composable
private fun CustomEmptyHint() {
    Text(
        text = stringResource(R.string.sounds_empty_hint, limitMb(SoundRepository.IMPORT_LIMIT_BYTES)),
        style = BalarmTheme.typography.body,
        color = BalarmTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BalarmDimens.ScreenPadding, vertical = BalarmDimens.SpacingSmall)
            .testTag(SoundsTestTags.CUSTOM_EMPTY),
    )
}

@Composable
private fun SelectedMissingHint() {
    Text(
        text = stringResource(R.string.sounds_selected_missing),
        style = BalarmTheme.typography.body,
        color = BalarmTheme.colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BalarmDimens.ScreenPadding, vertical = BalarmDimens.SpacingSmall)
            .testTag(SoundsTestTags.SELECTED_MISSING),
    )
}

/**
 * Строка мелодии: радио-отметка (выбор — не только цветом: точка радиокнопки), название, у своей — длительность
 * и размер; играющая — иконка «Стоп» и состояние «Играет» для TalkBack. Вся строка — одна радиокнопка ≥ 56dp.
 */
@Composable
private fun SoundRow(
    title: String,
    details: SoundDetails?,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BalarmTheme.colors
    val playingText = stringResource(R.string.sounds_playing)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BalarmDimens.ListRowMinHeight)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { if (playing) stateDescription = playingText }
            .padding(horizontal = BalarmDimens.ScreenPadding, vertical = BalarmDimens.SpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.primary, unselectedColor = colors.textSecondary),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = BalarmTheme.typography.body, color = colors.textPrimary)
            details?.let { SoundDetailsText(details = it) }
        }
        if (playing) {
            Icon(
                painter = painterResource(BalarmIcons.Stop),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(BalarmDimens.Icon),
            )
        }
    }
}
