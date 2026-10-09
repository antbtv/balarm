package com.antbtv.balarm.feature.sounds

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antbtv.balarm.core.designsystem.component.BalarmIcons
import com.antbtv.balarm.core.designsystem.component.BalarmSnackbarHost
import com.antbtv.balarm.core.designsystem.component.BalarmTopBar
import com.antbtv.balarm.core.designsystem.component.ConfirmDialog
import com.antbtv.balarm.core.designsystem.component.PrimaryButton
import com.antbtv.balarm.core.designsystem.component.ScrimEdge
import com.antbtv.balarm.core.designsystem.component.SystemBarScrim
import com.antbtv.balarm.core.designsystem.component.TextInputDialog
import com.antbtv.balarm.core.designsystem.component.labelLength
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.core.format.titleRes
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.CustomSound
import com.antbtv.balarm.core.model.SoundRef

/**
 * «Мои мелодии» (FR-SND-3): прослушать, переименовать, удалить (с предупреждением, если мелодию используют
 * будильники), «Добавить мелодию». Весь экран — за `feature.customSounds`: при выключенном флаге (восстановленный
 * стек, старая ссылка) экран сразу закрывается.
 *
 * Полноэкранный: `safeDrawing` обрабатывает сам; системный Back — у навигации.
 */
@Composable
fun SoundLibraryRoute(onClose: () -> Unit) {
    SoundLibraryRoute(viewModel = hiltViewModel(), onClose = onClose)
}

/** То же с готовой ViewModel: тесты собирают её на фейках без Hilt. */
@Composable
internal fun SoundLibraryRoute(
    viewModel: SoundLibraryViewModel,
    onClose: () -> Unit,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(state.enabled) { if (!state.enabled) currentOnClose() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onEvent(SoundLibraryEvent.StopPreview) }
    val launchImport = rememberImportLauncher(
        onPicked = { viewModel.onEvent(SoundLibraryEvent.ImportPicked(it)) },
        onUnavailable = { viewModel.onEvent(SoundLibraryEvent.ImportUnavailable) },
    )
    val actions = remember(viewModel, launchImport) {
        SoundLibraryActions(
            onClose = {
                viewModel.onEvent(SoundLibraryEvent.StopPreview)
                currentOnClose()
            },
            onAddSound = {
                viewModel.onEvent(SoundLibraryEvent.StopPreview)
                launchImport()
            },
        )
    }
    SoundLibraryScreen(state = state, onEvent = viewModel::onEvent, actions = actions, windowInsets = windowInsets)
}

/** Действия экрана наружу (навигация, SAF) одним стабильным объектом. */
internal class SoundLibraryActions(val onClose: () -> Unit, val onAddSound: () -> Unit)

/**
 * Загрузка — только заголовок (пустое состояние не мигает). Пусто — «Пока нет своих мелодий» и подсказка.
 * «Добавить мелодию» закреплена снизу во всех состояниях, кроме загрузки.
 *
 * @param windowInsets системные отступы (в приложении — `safeDrawing`); тесты и превью задают свои.
 */
@Composable
internal fun SoundLibraryScreen(
    state: SoundLibraryUiState,
    onEvent: (SoundLibraryEvent) -> Unit,
    actions: SoundLibraryActions,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val colors = BalarmTheme.colors
    val snackbar = remember { SnackbarHostState() }
    SoundMessageEffect(message = state.message, hostState = snackbar) { onEvent(SoundLibraryEvent.MessageShown) }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(SoundsTestTags.LIBRARY),
        color = colors.background,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            BalarmTopBar(
                title = stringResource(R.string.sounds_library_title),
                onBack = actions.onClose,
                backDescription = stringResource(R.string.sounds_back),
            )
            if (state.importing) ImportProgress()
            val sounds = state.sounds
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    !state.enabled || sounds == null -> Unit
                    sounds.isEmpty() -> LibraryEmpty()
                    else -> LibraryList(sounds = sounds, playing = state.playing, onEvent = onEvent)
                }
                SystemBarScrim(edge = ScrimEdge.Bottom, inset = 0.dp, modifier = Modifier.align(Alignment.BottomCenter))
                BalarmSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
            }
            if (state.enabled && sounds != null) {
                PrimaryButton(
                    text = stringResource(if (state.importing) R.string.sounds_importing else R.string.sounds_add),
                    onClick = actions.onAddSound,
                    enabled = !state.importing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom))
                        .padding(
                            start = BalarmDimens.ScreenPadding,
                            end = BalarmDimens.ScreenPadding,
                            top = BalarmDimens.SpacingSmall,
                            bottom = BalarmDimens.ScreenPadding,
                        )
                        .testTag(SoundsTestTags.ADD),
                )
            }
        }
    }
    LibraryDialogs(state = state, onEvent = onEvent)
}

@Composable
private fun LibraryEmpty() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(BalarmDimens.ScreenPadding)
            .testTag(SoundsTestTags.LIBRARY_EMPTY),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.sounds_library_empty),
            style = BalarmTheme.typography.title,
            color = BalarmTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.sounds_empty_hint, limitMb(SoundRepository.IMPORT_LIMIT_BYTES)),
            style = BalarmTheme.typography.body,
            color = BalarmTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LibraryList(sounds: List<CustomSound>, playing: SoundRef?, onEvent: (SoundLibraryEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(SoundsTestTags.LIST),
        contentPadding = PaddingValues(
            start = BalarmDimens.ScreenPadding,
            end = BalarmDimens.ScreenPadding,
            top = BalarmDimens.SpacingSmall,
            bottom = BalarmDimens.ScreenPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
    ) {
        items(sounds, key = { it.id.value }) { sound ->
            LibraryRow(sound = sound, playing = playing == SoundRef.Custom(sound.id), onEvent = onEvent)
        }
    }
}

/**
 * Карточка мелодии: слева зона «прослушать/остановить» (иконка + название + длительность и размер), справа
 * «Переименовать» и «Удалить» (по 48dp). Название при fontScale 2 переносится, кнопки не сжимаются.
 */
@Composable
private fun LibraryRow(sound: CustomSound, playing: Boolean, onEvent: (SoundLibraryEvent) -> Unit) {
    val colors = BalarmTheme.colors
    val details = rememberSoundDetails(sound)
    val playingText = stringResource(R.string.sounds_playing)
    Surface(
        shape = BalarmShapes.Card,
        color = colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SoundsTestTags.custom(sound.id)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BalarmDimens.ListRowMinHeight)
                    .clickable(
                        onClickLabel = stringResource(
                            if (playing) R.string.sounds_action_stop else R.string.sounds_action_play,
                        ),
                        role = Role.Button,
                    ) { onEvent(SoundLibraryEvent.PlayClicked(sound.id)) }
                    .semantics(mergeDescendants = true) { if (playing) stateDescription = playingText }
                    .padding(
                        start = BalarmDimens.CardPadding,
                        top = BalarmDimens.CardGap,
                        bottom = BalarmDimens.CardGap,
                    )
                    .testTag(SoundsTestTags.play(sound.id)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
            ) {
                Icon(
                    painter = painterResource(if (playing) BalarmIcons.Stop else BalarmIcons.Play),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(BalarmDimens.Icon),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = sound.title, style = BalarmTheme.typography.body, color = colors.textPrimary)
                    SoundDetailsText(details = details)
                }
            }
            RowIconButton(
                icon = BalarmIcons.Edit,
                description = stringResource(R.string.sounds_rename_description, sound.title),
                onClick = { onEvent(SoundLibraryEvent.RenameClicked(sound.id)) },
                tag = SoundsTestTags.rename(sound.id),
            )
            RowIconButton(
                icon = BalarmIcons.Delete,
                description = stringResource(R.string.sounds_delete_description, sound.title),
                onClick = { onEvent(SoundLibraryEvent.DeleteClicked(sound.id)) },
                tag = SoundsTestTags.delete(sound.id),
                modifier = Modifier.padding(end = BalarmDimens.SpacingTiny),
            )
        }
    }
}

@Composable
private fun RowIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(BalarmDimens.MinTouch)
            .testTag(tag),
        colors = IconButtonDefaults.iconButtonColors(contentColor = BalarmTheme.colors.textSecondary),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(BalarmDimens.Icon),
        )
    }
}

@Composable
private fun LibraryDialogs(state: SoundLibraryUiState, onEvent: (SoundLibraryEvent) -> Unit) {
    when (val dialog = state.dialog) {
        is LibraryDialog.Rename -> RenameDialog(dialog = dialog, onEvent = onEvent)
        is LibraryDialog.ConfirmDelete -> DeleteDialog(dialog = dialog, onEvent = onEvent)
        null -> Unit
    }
}

@Composable
private fun RenameDialog(dialog: LibraryDialog.Rename, onEvent: (SoundLibraryEvent) -> Unit) {
    val maxLength = CustomSound.MAX_TITLE_LENGTH
    TextInputDialog(
        title = stringResource(R.string.sounds_rename_title),
        value = dialog.input,
        onValueChange = { onEvent(SoundLibraryEvent.RenameInput(it)) },
        label = stringResource(R.string.sounds_rename_label),
        maxLength = maxLength,
        confirmText = stringResource(R.string.sounds_rename_save),
        dismissText = stringResource(R.string.sounds_cancel),
        onConfirm = { onEvent(SoundLibraryEvent.RenameConfirmed) },
        onDismiss = { onEvent(SoundLibraryEvent.DialogDismissed) },
        confirmEnabled = dialog.canSave,
        counterDescription = pluralStringResource(
            R.plurals.sounds_rename_counter,
            maxLength,
            labelLength(dialog.input),
            maxLength,
        ),
        modifier = Modifier.testTag(SoundsTestTags.RENAME_DIALOG),
    )
}

@Composable
private fun DeleteDialog(dialog: LibraryDialog.ConfirmDelete, onEvent: (SoundLibraryEvent) -> Unit) {
    val text = if (dialog.usageCount > 0) {
        pluralStringResource(
            R.plurals.sounds_delete_used,
            dialog.usageCount,
            dialog.usageCount,
            stringResource(BuiltinSound.DEFAULT.titleRes()),
        )
    } else {
        stringResource(R.string.sounds_delete_unused)
    }
    ConfirmDialog(
        title = stringResource(R.string.sounds_delete_title, dialog.title),
        text = text,
        confirmText = stringResource(R.string.sounds_delete),
        dismissText = stringResource(R.string.sounds_cancel),
        onConfirm = { onEvent(SoundLibraryEvent.DeleteConfirmed) },
        onDismiss = { onEvent(SoundLibraryEvent.DialogDismissed) },
        destructive = true,
        modifier = Modifier.testTag(SoundsTestTags.DELETE_DIALOG),
    )
}
