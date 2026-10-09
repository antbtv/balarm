package com.antbtv.balarm.feature.sounds

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.CustomSound
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import java.time.Duration
import java.time.Instant

// Превью пикера и библиотеки: тёмная тема (светлая — контроль), 360dp, fontScale 2, RU/EN, состояния
// загрузка / пусто / данные / импорт / диалоги. Отступы «как у телефона» — в превью баров нет.

private val PreviewInsets = WindowInsets(top = 24.dp, bottom = 24.dp)

private val PreviewCustoms = listOf(
    CustomSound(CustomSoundId(2), "Утренняя мотивация — запись", Duration.ofSeconds(95), 2_400_000, Instant.EPOCH),
    CustomSound(CustomSoundId(1), "Birdsong", Duration.ofSeconds(32), 640_000, Instant.EPOCH),
)

private val NoActions = SoundPickerActions(onConfirm = {}, onClose = {}, onAddSound = {}, onOpenLibrary = {})
private val NoLibraryActions = SoundLibraryActions(onClose = {}, onAddSound = {})

@Composable
private fun Picker(state: SoundPickerUiState) {
    SoundPickerScreen(state = state, onEvent = {}, actions = NoActions, windowInsets = PreviewInsets)
}

@Composable
private fun Library(state: SoundLibraryUiState) {
    SoundLibraryScreen(state = state, onEvent = {}, actions = NoLibraryActions, windowInsets = PreviewInsets)
}

private val PickerData = SoundPickerUiState(
    selected = SoundRef.Builtin(BuiltinSound.BELLS),
    playing = SoundRef.Builtin(BuiltinSound.BELLS),
    customs = PreviewCustoms,
    customSoundsEnabled = true,
)

private val LibraryData = SoundLibraryUiState(
    enabled = true,
    sounds = PreviewCustoms,
    playing = SoundRef.Custom(CustomSoundId(1)),
)

// --- Пикер ---

@Preview(name = "Picker — data, 360dp", widthDp = 360, heightDp = 1400)
@Composable
private fun PickerPreview() {
    BalarmTheme { Picker(PickerData) }
}

@Preview(name = "Picker — RU, fontScale 2", widthDp = 360, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun PickerLargeFontPreview() {
    BalarmTheme { Picker(PickerData) }
}

@Preview(name = "Picker — custom empty, importing, RU", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun PickerImportingPreview() {
    BalarmTheme { Picker(PickerData.copy(customs = emptyList(), importing = true, playing = null)) }
}

@Preview(name = "Picker — flag off, selected custom", widthDp = 360, heightDp = 1200, locale = "ru")
@Composable
private fun PickerFlagOffPreview() {
    BalarmTheme {
        Picker(
            SoundPickerUiState(
                selected = SoundRef.Custom(CustomSoundId(1)),
                customs = PreviewCustoms.takeLast(1),
            ),
        )
    }
}

@Preview(name = "Picker — selected deleted, RU", widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun PickerMissingPreview() {
    BalarmTheme {
        Picker(PickerData.copy(selected = SoundRef.Custom(CustomSoundId(9)), playing = null))
    }
}

@Preview(name = "Picker — loading", widthDp = 360, heightDp = 640)
@Composable
private fun PickerLoadingPreview() {
    BalarmTheme { Picker(SoundPickerUiState(selected = SoundRef.DEFAULT, customSoundsEnabled = true)) }
}

@Preview(name = "Picker — light", widthDp = 360, heightDp = 1400)
@Composable
private fun PickerLightPreview() {
    BalarmTheme(darkTheme = false) { Picker(PickerData) }
}

// --- Библиотека ---

@Preview(name = "Library — data, 360dp", widthDp = 360, heightDp = 640)
@Composable
private fun LibraryPreview() {
    BalarmTheme { Library(LibraryData) }
}

@Preview(name = "Library — RU, fontScale 2", widthDp = 360, heightDp = 640, fontScale = 2f, locale = "ru")
@Composable
private fun LibraryLargeFontPreview() {
    BalarmTheme { Library(LibraryData) }
}

@Preview(name = "Library — empty, RU", widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun LibraryEmptyPreview() {
    BalarmTheme { Library(SoundLibraryUiState(enabled = true, sounds = emptyList())) }
}

@Preview(name = "Library — loading", widthDp = 360, heightDp = 640)
@Composable
private fun LibraryLoadingPreview() {
    BalarmTheme { Library(SoundLibraryUiState(enabled = true)) }
}

@Preview(name = "Library — importing", widthDp = 360, heightDp = 640)
@Composable
private fun LibraryImportingPreview() {
    BalarmTheme { Library(LibraryData.copy(importing = true)) }
}

@Preview(name = "Library — delete used, RU", widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun LibraryDeletePreview() {
    BalarmTheme {
        Library(LibraryData.copy(dialog = LibraryDialog.ConfirmDelete(CustomSoundId(1), "Birdsong", usageCount = 3)))
    }
}

@Preview(name = "Library — rename, fontScale 2", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun LibraryRenamePreview() {
    BalarmTheme { Library(LibraryData.copy(dialog = LibraryDialog.Rename(CustomSoundId(1), "Birdsong"))) }
}

@Preview(name = "Library — light", widthDp = 360, heightDp = 640)
@Composable
private fun LibraryLightPreview() {
    BalarmTheme(darkTheme = false) { Library(LibraryData) }
}
