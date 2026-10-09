package com.antbtv.balarm.feature.sounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.antbtv.balarm.core.domain.sound.ImportResult
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.core.domain.sound.SoundSource
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
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

/** Пикер мелодии (FR-SND-2, M4-T10): выбор и превью, импорт всех исходов, флаг `feature.customSounds`. */
@OptIn(ExperimentalCoroutinesApi::class)
class SoundPickerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sounds = FakeSoundRepository(listOf(customSound(7, "Birds"), customSound(3, "Rain")))
    private val preview = RecordingSoundPreview()
    private val store = ViewModelStore()

    private val bells = SoundRef.Builtin(BuiltinSound.BELLS)
    private val birds = SoundRef.Custom(CustomSoundId(7))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(selected: SoundRef = SoundRef.DEFAULT, customSounds: Boolean = true) =
        viewModel(selected.encode(), customSounds)

    private fun TestScope.viewModel(
        encoded: String,
        customSounds: Boolean = true,
        repository: SoundRepository = sounds,
    ): SoundPickerViewModel {
        val created = SoundPickerViewModel(encoded, repository, preview, flags(customSounds))
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = created as T
        }
        return ViewModelProvider(store, factory)[SoundPickerViewModel::class.java].also { runCurrent() }
    }

    private fun TestScope.send(viewModel: SoundPickerViewModel, vararg events: SoundPickerEvent) {
        events.forEach(viewModel::onEvent)
        runCurrent()
    }

    @Test
    fun `opens with the alarm's sound selected and the library loaded`() = runTest(dispatcher) {
        val state = viewModel(birds).uiState.value

        assertThat(state.selected).isEqualTo(birds)
        assertThat(state.customs!!.map { it.title }).containsExactly("Birds", "Rain").inOrder()
        assertThat(state.customSoundsEnabled).isTrue()
        assertThat(state.canConfirm).isTrue()
    }

    @Test
    fun `garbage selection falls back to the default sound`() = runTest(dispatcher) {
        assertThat(viewModel("custom:nope").uiState.value.selected).isEqualTo(SoundRef.DEFAULT)
    }

    @Test
    fun `tapping a row selects and plays it, tapping the playing row stops it`() = runTest(dispatcher) {
        val viewModel = viewModel()

        send(viewModel, SoundPickerEvent.RowClicked(bells))
        assertThat(viewModel.uiState.value.selected).isEqualTo(bells)
        assertThat(viewModel.uiState.value.playing).isEqualTo(bells)
        assertThat(preview.played.last()).isEqualTo(SoundSettings(sound = bells))

        send(viewModel, SoundPickerEvent.RowClicked(bells))
        assertThat(viewModel.uiState.value.playing).isNull()
        assertThat(viewModel.uiState.value.selected).isEqualTo(bells)

        // Автостоп превью прошёл — повторный тап по отмеченной снова играет.
        send(viewModel, SoundPickerEvent.RowClicked(bells))
        assertThat(preview.played).hasSize(2)
    }

    @Test
    fun `tapping another row while one plays switches the preview`() = runTest(dispatcher) {
        val viewModel = viewModel()

        send(viewModel, SoundPickerEvent.RowClicked(bells), SoundPickerEvent.RowClicked(birds))

        assertThat(viewModel.uiState.value.selected).isEqualTo(birds)
        assertThat(preview.played.map { it.sound }).containsExactly(bells, birds).inOrder()
    }

    @Test
    fun `preview stops on screen stop and when the view model is cleared`() = runTest(dispatcher) {
        val viewModel = viewModel()
        send(viewModel, SoundPickerEvent.RowClicked(bells), SoundPickerEvent.StopPreview)
        assertThat(preview.stops).isEqualTo(1)

        store.clear()
        assertThat(preview.stops).isEqualTo(2)
    }

    @Test
    fun `imported sound appears, gets selected and plays`() = runTest(dispatcher) {
        val sound = customSound(9, "Motivation")
        sounds.importResult = ImportResult.Imported(sound)
        val gate = CompletableDeferred<Unit>().also { sounds.importGate = it }
        val viewModel = viewModel()

        send(viewModel, SoundPickerEvent.ImportPicked("content://docs/9"))
        assertThat(viewModel.uiState.value.importing).isTrue()

        gate.complete(Unit)
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(sounds.imported).containsExactly("content://docs/9")
        assertThat(state.importing).isFalse()
        assertThat(state.customs!!.first().title).isEqualTo("Motivation")
        assertThat(state.selected).isEqualTo(SoundRef.Custom(sound.id))
        assertThat(state.message).isEqualTo(SoundMessage.Imported("Motivation"))
        assertThat(preview.played.last().sound).isEqualTo(SoundRef.Custom(sound.id))
    }

    @Test
    fun `import failures become messages and keep the selection`() = runTest(dispatcher) {
        val cases = listOf(
            ImportResult.TooLarge(20L * 1024 * 1024) to SoundMessage.TooLarge(20L * 1024 * 1024),
            ImportResult.Unsupported to SoundMessage.Unsupported,
            ImportResult.NoSpace to SoundMessage.NoSpace,
            ImportResult.Failed("io") to SoundMessage.ImportFailed,
        )
        val viewModel = viewModel(bells)

        cases.forEach { (result, message) ->
            sounds.importResult = result
            send(viewModel, SoundPickerEvent.ImportPicked("content://x"))

            assertThat(viewModel.uiState.value.message).isEqualTo(message)
            assertThat(viewModel.uiState.value.selected).isEqualTo(bells)
            assertThat(viewModel.uiState.value.importing).isFalse()
            send(viewModel, SoundPickerEvent.MessageShown)
            assertThat(viewModel.uiState.value.message).isNull()
        }
        assertThat(preview.played).isEmpty()
    }

    @Test
    fun `a crashing repository is reported as a failed import`() = runTest(dispatcher) {
        val failing = object : SoundRepository by sounds {
            override suspend fun import(source: SoundSource): ImportResult = error("boom")
        }
        val created = viewModel(SoundRef.DEFAULT.encode(), repository = failing)

        send(created, SoundPickerEvent.ImportPicked("content://x"))

        assertThat(created.uiState.value.message).isEqualTo(SoundMessage.ImportFailed)
        assertThat(created.uiState.value.importing).isFalse()
    }

    @Test
    fun `no document picker on the device is a failed import`() = runTest(dispatcher) {
        val viewModel = viewModel()

        send(viewModel, SoundPickerEvent.ImportUnavailable)

        assertThat(viewModel.uiState.value.message).isEqualTo(SoundMessage.ImportFailed)
    }

    @Test
    fun `flag off - no library except the alarm's own custom sound, import ignored`() = runTest(dispatcher) {
        val viewModel = viewModel(birds, customSounds = false)

        val state = viewModel.uiState.value
        assertThat(state.customSoundsEnabled).isFalse()
        assertThat(state.customs!!.map { it.id }).containsExactly(CustomSoundId(7))
        assertThat(state.customBlockVisible).isTrue()

        send(viewModel, SoundPickerEvent.ImportPicked("content://x"))
        assertThat(sounds.imported).isEmpty()
    }

    @Test
    fun `flag off with a builtin selected - no custom block at all`() = runTest(dispatcher) {
        val state = viewModel(bells, customSounds = false).uiState.value

        assertThat(state.customs).isNull()
        assertThat(state.customBlockVisible).isFalse()
    }

    @Test
    fun `deleted selected sound cannot be confirmed until another is chosen`() = runTest(dispatcher) {
        val viewModel = viewModel(SoundRef.Custom(CustomSoundId(42)))

        assertThat(viewModel.uiState.value.selectedMissing).isTrue()
        assertThat(viewModel.uiState.value.canConfirm).isFalse()

        send(viewModel, SoundPickerEvent.RowClicked(bells))
        assertThat(viewModel.uiState.value.canConfirm).isTrue()
    }
}
