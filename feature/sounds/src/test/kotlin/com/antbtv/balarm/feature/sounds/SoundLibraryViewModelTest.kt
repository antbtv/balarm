package com.antbtv.balarm.feature.sounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.antbtv.balarm.core.domain.sound.ImportResult
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** «Мои мелодии» (FR-SND-3, M4-T10): прослушать, переименовать, удалить с предупреждением, импорт, флаг. */
@OptIn(ExperimentalCoroutinesApi::class)
class SoundLibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sounds = FakeSoundRepository(listOf(customSound(7, "Birds"), customSound(3, "Rain")))
    private val preview = RecordingSoundPreview()
    private val store = ViewModelStore()

    private val birdsId = CustomSoundId(7)
    private val birds = SoundRef.Custom(birdsId)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(customSounds: Boolean = true): SoundLibraryViewModel {
        val created = SoundLibraryViewModel(sounds, preview, flags(customSounds))
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = created as T
        }
        return ViewModelProvider(store, factory)[SoundLibraryViewModel::class.java].also { runCurrent() }
    }

    private fun TestScope.send(viewModel: SoundLibraryViewModel, vararg events: SoundLibraryEvent) {
        events.forEach(viewModel::onEvent)
        runCurrent()
    }

    @Test
    fun `lists the library`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertThat(state.enabled).isTrue()
        assertThat(state.sounds!!.map { it.title }).containsExactly("Birds", "Rain").inOrder()
    }

    @Test
    fun `empty library is distinguishable from loading`() = runTest(dispatcher) {
        sounds.sounds.value = emptyList()

        assertThat(viewModel().uiState.value.sounds).isEmpty()
    }

    @Test
    fun `play toggles the preview`() = runTest(dispatcher) {
        val viewModel = viewModel()

        send(viewModel, SoundLibraryEvent.PlayClicked(birdsId))
        assertThat(viewModel.uiState.value.playing).isEqualTo(birds)

        send(viewModel, SoundLibraryEvent.PlayClicked(birdsId))
        assertThat(viewModel.uiState.value.playing).isNull()
        assertThat(preview.played.map { it.sound }).containsExactly(birds)
    }

    @Test
    fun `preview stops on screen stop and when the view model is cleared`() = runTest(dispatcher) {
        val viewModel = viewModel()
        send(viewModel, SoundLibraryEvent.PlayClicked(birdsId), SoundLibraryEvent.StopPreview)
        assertThat(preview.stops).isEqualTo(1)

        store.clear()
        assertThat(preview.stops).isEqualTo(2)
    }

    @Test
    fun `rename trims the title and updates the list`() = runTest(dispatcher) {
        val viewModel = viewModel()

        send(viewModel, SoundLibraryEvent.RenameClicked(birdsId))
        assertThat(viewModel.uiState.value.dialog).isEqualTo(LibraryDialog.Rename(birdsId, "Birds"))

        send(viewModel, SoundLibraryEvent.RenameInput("  Morning birds "), SoundLibraryEvent.RenameConfirmed)

        assertThat(sounds.renamed).containsExactly(birdsId to "Morning birds")
        assertThat(viewModel.uiState.value.dialog).isNull()
        assertThat(viewModel.uiState.value.sounds!!.first().title).isEqualTo("Morning birds")
        assertThat(viewModel.uiState.value.message).isNull()
    }

    @Test
    fun `blank or too long title cannot be saved`() = runTest(dispatcher) {
        val viewModel = viewModel()
        send(viewModel, SoundLibraryEvent.RenameClicked(birdsId), SoundLibraryEvent.RenameInput("   "))
        assertThat((viewModel.uiState.value.dialog as LibraryDialog.Rename).canSave).isFalse()

        send(viewModel, SoundLibraryEvent.RenameConfirmed)
        assertThat(sounds.renamed).isEmpty()
        assertThat(viewModel.uiState.value.dialog).isNotNull()

        send(viewModel, SoundLibraryEvent.RenameInput("a".repeat(41)))
        assertThat((viewModel.uiState.value.dialog as LibraryDialog.Rename).canSave).isFalse()
        send(viewModel, SoundLibraryEvent.RenameInput("🎵".repeat(40)))
        assertThat((viewModel.uiState.value.dialog as LibraryDialog.Rename).canSave).isTrue()
    }

    @Test
    fun `failed rename is reported`() = runTest(dispatcher) {
        sounds.renameSucceeds = false
        val viewModel = viewModel()

        send(
            viewModel,
            SoundLibraryEvent.RenameClicked(birdsId),
            SoundLibraryEvent.RenameInput("New"),
            SoundLibraryEvent.RenameConfirmed,
        )

        assertThat(viewModel.uiState.value.message).isEqualTo(SoundMessage.RenameFailed)
    }

    @Test
    fun `deleting an unused sound asks without a warning`() = runTest(dispatcher) {
        val viewModel = viewModel()

        send(viewModel, SoundLibraryEvent.DeleteClicked(birdsId))
        assertThat(viewModel.uiState.value.dialog).isEqualTo(LibraryDialog.ConfirmDelete(birdsId, "Birds", 0))

        send(viewModel, SoundLibraryEvent.DeleteConfirmed)
        assertThat(sounds.deleted).containsExactly(birdsId)
        assertThat(viewModel.uiState.value.sounds!!.map { it.title }).containsExactly("Rain")
        assertThat(viewModel.uiState.value.message).isEqualTo(SoundMessage.Deleted("Birds", 0))
    }

    @Test
    fun `deleting a used sound warns and reports switched alarms`() = runTest(dispatcher) {
        sounds.usage = mapOf(birdsId to 2)
        val viewModel = viewModel()
        send(viewModel, SoundLibraryEvent.PlayClicked(birdsId))

        send(viewModel, SoundLibraryEvent.DeleteClicked(birdsId))
        assertThat(viewModel.uiState.value.dialog).isEqualTo(LibraryDialog.ConfirmDelete(birdsId, "Birds", 2))

        send(viewModel, SoundLibraryEvent.DeleteConfirmed)
        assertThat(viewModel.uiState.value.message).isEqualTo(SoundMessage.Deleted("Birds", 2))
        // Удалённая мелодия не доигрывает.
        assertThat(viewModel.uiState.value.playing).isNull()
    }

    @Test
    fun `dismissing the delete dialog deletes nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()

        send(viewModel, SoundLibraryEvent.DeleteClicked(birdsId))
        send(viewModel, SoundLibraryEvent.DialogDismissed)
        send(viewModel, SoundLibraryEvent.DeleteConfirmed)

        assertThat(sounds.deleted).isEmpty()
        assertThat(viewModel.uiState.value.dialog).isNull()
    }

    @Test
    fun `import outcomes`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val sound = customSound(9, "Motivation")
        val cases = listOf(
            ImportResult.TooLarge(1) to SoundMessage.TooLarge(1),
            ImportResult.Unsupported to SoundMessage.Unsupported,
            ImportResult.NoSpace to SoundMessage.NoSpace,
            ImportResult.Failed("x") to SoundMessage.ImportFailed,
            ImportResult.Imported(sound) to SoundMessage.Imported("Motivation"),
        )

        cases.forEach { (result, message) ->
            sounds.importResult = result
            send(viewModel, SoundLibraryEvent.ImportPicked("content://x"))
            assertThat(viewModel.uiState.value.message).isEqualTo(message)
            assertThat(viewModel.uiState.value.importing).isFalse()
            send(viewModel, SoundLibraryEvent.MessageShown)
        }
        assertThat(viewModel.uiState.value.sounds!!.first().title).isEqualTo("Motivation")
        assertThat(preview.played.last().sound).isEqualTo(SoundRef.Custom(sound.id))
    }

    @Test
    fun `flag off - nothing is loaded or changed`() = runTest(dispatcher) {
        val viewModel = viewModel(customSounds = false)

        send(
            viewModel,
            SoundLibraryEvent.PlayClicked(birdsId),
            SoundLibraryEvent.DeleteClicked(birdsId),
            SoundLibraryEvent.ImportPicked("content://x"),
        )

        val state = viewModel.uiState.value
        assertThat(state.enabled).isFalse()
        assertThat(state.sounds).isNull()
        assertThat(state.dialog).isNull()
        assertThat(preview.played).isEmpty()
        assertThat(sounds.imported).isEmpty()
    }
}
