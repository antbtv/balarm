package com.antbtv.balarm.feature.alarmedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.domain.testing.testAlarmRunner
import com.antbtv.balarm.core.domain.testing.testEngine
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Секция «Звук» редактора (FR-EDIT-5, M4-T09): мелодия, громкость с превью, нарастание, вибрация, флаг. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmEditSoundViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(LocalDateTime.parse("2026-09-28T05:20").atZone(moscow).toInstant(), moscow)
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val log = RecordingEventLog()
    private var soundFlag = true
    private val flags = FeatureFlagProvider { if (it == Feature.ALARM_SOUND) soundFlag else it.defaultEnabled }
    private val engine: AlarmEngine = testEngine(repository, scheduler, clock, flags, log)
    private val sounds = FakeSoundRepository(listOf(customSound(7, "Birds")))
    private val preview = RecordingSoundPreview()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    /** Через [ViewModelStore]: `tearDown` и тесты `onCleared` очищают ViewModel как система. */
    private fun TestScope.viewModel(alarmId: Long? = null): AlarmEditViewModel {
        val created = AlarmEditViewModel(
            alarmId,
            repository,
            engine,
            testAlarmRunner(scheduler, clock, log),
            clock,
            flags,
            sounds,
            preview,
        )
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = created as T
        }
        return ViewModelProvider(store, factory)[AlarmEditViewModel::class.java].also { runCurrent() }
    }

    private fun stored(alarm: Alarm): Long = runBlocking { engine.save(alarm).id.value }

    private fun alarmInDb(id: Long): Alarm? = runBlocking { repository.get(AlarmId(id)) }

    private fun TestScope.send(viewModel: AlarmEditViewModel, vararg events: AlarmEditEvent) {
        events.forEach(viewModel::onEvent)
        runCurrent()
    }

    private val birds = SoundRef.Custom(CustomSoundId(7))
    private val marimba = SoundRef.Builtin(BuiltinSound.MARIMBA)

    @Test
    fun `a new alarm shows the default ringtone, volume and fade-in`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertThat(state.soundVisible).isTrue()
        assertThat(state.draft.sound).isEqualTo(SoundSettings.DEFAULT)
        assertThat(state.soundName).isEqualTo(SoundName.Builtin(BuiltinSound.DEFAULT))
        assertThat(state.draft.vibrate).isTrue()
    }

    @Test
    fun `stored sound settings are read on open and saved back`() = runTest(dispatcher) {
        val settings = SoundSettings(birds, volumePercent = 40, fadeIn = Duration.ofSeconds(60))
        val id = stored(Alarm(time = LocalTime.of(7, 30), sound = settings, vibrate = false))
        val viewModel = viewModel(id)

        val state = viewModel.uiState.value
        assertThat(state.draft.sound).isEqualTo(settings)
        assertThat(state.draft.vibrate).isFalse()
        assertThat(state.soundName).isEqualTo(SoundName.Custom("Birds"))

        viewModel.effects.test {
            send(
                viewModel,
                AlarmEditEvent.SoundSelected(marimba),
                AlarmEditEvent.VolumeChanged(70),
                AlarmEditEvent.FadeInSelected(Duration.ofSeconds(15)),
                AlarmEditEvent.VibrateChanged(true),
                AlarmEditEvent.Save,
            )

            assertThat(awaitItem()).isInstanceOf(AlarmEditEffect.Saved::class.java)
        }
        val saved = alarmInDb(id)!!
        assertThat(saved.sound).isEqualTo(SoundSettings(marimba, volumePercent = 70, fadeIn = Duration.ofSeconds(15)))
        assertThat(saved.vibrate).isTrue()
    }

    @Test
    fun `a picked ringtone makes the draft dirty and back asks to discard`() = runTest(dispatcher) {
        val viewModel = viewModel()

        send(viewModel, AlarmEditEvent.SoundSelected(birds))

        val state = viewModel.uiState.value
        assertThat(state.draft.sound.sound).isEqualTo(birds)
        assertThat(state.soundName).isEqualTo(SoundName.Custom("Birds"))
        assertThat(state.isDirty).isTrue()
        send(viewModel, AlarmEditEvent.Back)
        assertThat(viewModel.uiState.value.dialog).isEqualTo(EditDialog.ConfirmDiscard)
    }

    @Test
    fun `a ringtone picked while the alarm is loading is applied after the load`() = runTest(dispatcher) {
        val id = stored(Alarm(time = LocalTime.of(7, 30)))
        val viewModel = AlarmEditViewModel(
            id,
            repository,
            engine,
            testAlarmRunner(scheduler, clock, log),
            clock,
            flags,
            sounds,
            preview,
        )
        assertThat(viewModel.uiState.value.loading).isTrue()

        viewModel.onEvent(AlarmEditEvent.SoundSelected(marimba))
        runCurrent()

        assertThat(viewModel.uiState.value.draft.sound.sound).isEqualTo(marimba)
        assertThat(viewModel.uiState.value.isDirty).isTrue()
    }

    @Test
    fun `a deleted custom ringtone is shown as missing, a renamed one updates live`() = runTest(dispatcher) {
        val id = stored(Alarm(time = LocalTime.of(7, 30), sound = SoundSettings(birds)))
        val viewModel = viewModel(id)
        assertThat(viewModel.uiState.value.soundName).isEqualTo(SoundName.Custom("Birds"))

        sounds.sounds.value = listOf(customSound(7, "Forest birds"))
        runCurrent()
        assertThat(viewModel.uiState.value.soundName).isEqualTo(SoundName.Custom("Forest birds"))

        sounds.sounds.value = emptyList()
        runCurrent()
        assertThat(viewModel.uiState.value.soundName).isEqualTo(SoundName.Missing)
        // Черновик не меняется сам: звонок сработает по резерву, пользователь выберет другую.
        assertThat(viewModel.uiState.value.draft.sound.sound).isEqualTo(birds)
        assertThat(viewModel.uiState.value.isDirty).isFalse()
    }

    @Test
    fun `a custom ringtone has no name until the library is loaded`() {
        val state = AlarmEditUiState(
            loading = false,
            isNew = true,
            initial = Alarm(time = LocalTime.of(7, 0)),
            draft = Alarm(time = LocalTime.of(7, 0), sound = SoundSettings(birds)),
            snoozeVisible = true,
            soundVisible = true,
            customTitles = null,
        )

        assertThat(state.soundName).isEqualTo(SoundName.Pending)
    }

    @Test
    fun `volume steps are clamped to 10-100 and snapped to 10`() = runTest(dispatcher) {
        val viewModel = viewModel()

        send(viewModel, AlarmEditEvent.VolumeChanged(0))
        assertThat(viewModel.uiState.value.draft.sound.volumePercent).isEqualTo(10)
        send(viewModel, AlarmEditEvent.VolumeChanged(57))
        assertThat(viewModel.uiState.value.draft.sound.volumePercent).isEqualTo(60)
        send(viewModel, AlarmEditEvent.VolumeChanged(140))
        assertThat(viewModel.uiState.value.draft.sound.volumePercent).isEqualTo(100)
    }

    @Test
    fun `changing the volume plays the preview at the new volume, the same step does not restart it`() =
        runTest(dispatcher) {
            val id = stored(Alarm(time = LocalTime.of(7, 30), sound = SoundSettings(marimba, volumePercent = 50)))
            val viewModel = viewModel(id)

            send(
                viewModel,
                AlarmEditEvent.VolumeChanged(70),
                AlarmEditEvent.VolumeChanged(70),
                AlarmEditEvent.VolumeChanged(30),
            )

            assertThat(preview.played).containsExactly(
                SoundSettings(marimba, volumePercent = 70),
                SoundSettings(marimba, volumePercent = 30),
            ).inOrder()
        }

    @Test
    fun `the preview stops on screen stop, before the picker and when the view model is cleared`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.effects.test {
                send(viewModel, AlarmEditEvent.VolumeChanged(50), AlarmEditEvent.StopSoundPreview)
                assertThat(preview.playing.value).isNull()
                assertThat(preview.stops).isEqualTo(1)

                send(viewModel, AlarmEditEvent.PickSound)
                assertThat(awaitItem()).isEqualTo(AlarmEditEffect.OpenSoundPicker(SoundRef.DEFAULT))
                assertThat(preview.stops).isEqualTo(2)
            }

            store.clear()
            assertThat(preview.stops).isEqualTo(3)
        }

    @Test
    fun `the picker opens with the current ringtone`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.SoundSelected(marimba), AlarmEditEvent.PickSound)

            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.OpenSoundPicker(marimba))
        }
    }

    @Test
    fun `fade-in dialog opens and a choice closes it, an unsupported value is ignored`() = runTest(dispatcher) {
        val viewModel = viewModel()

        send(viewModel, AlarmEditEvent.ShowFadeInDialog)
        assertThat(viewModel.uiState.value.dialog).isEqualTo(EditDialog.FadeIn)
        send(viewModel, AlarmEditEvent.FadeInSelected(Duration.ofSeconds(30)))
        assertThat(viewModel.uiState.value.dialog).isNull()
        assertThat(viewModel.uiState.value.draft.sound.fadeIn).isEqualTo(Duration.ofSeconds(30))

        send(viewModel, AlarmEditEvent.FadeInSelected(Duration.ofSeconds(45)))
        assertThat(viewModel.uiState.value.draft.sound.fadeIn).isEqualTo(Duration.ofSeconds(30))
    }

    @Test
    fun `with the flag off sound events change nothing and stored values are saved as they are`() =
        runTest(dispatcher) {
            soundFlag = false
            val settings = SoundSettings(birds, volumePercent = 40, fadeIn = Duration.ofSeconds(60))
            val id = stored(Alarm(time = LocalTime.of(7, 30), sound = settings, vibrate = false))
            val viewModel = viewModel(id)
            assertThat(viewModel.uiState.value.soundVisible).isFalse()

            viewModel.effects.test {
                send(
                    viewModel,
                    AlarmEditEvent.SoundSelected(marimba),
                    AlarmEditEvent.VolumeChanged(100),
                    AlarmEditEvent.FadeInSelected(Duration.ZERO),
                    AlarmEditEvent.VibrateChanged(true),
                    AlarmEditEvent.ShowFadeInDialog,
                    AlarmEditEvent.PickSound,
                )
                expectNoEvents()
                assertThat(viewModel.uiState.value.isDirty).isFalse()
                assertThat(viewModel.uiState.value.dialog).isNull()
                assertThat(preview.played).isEmpty()

                send(viewModel, AlarmEditEvent.Save)
                awaitItem()
            }
            val saved = alarmInDb(id)!!
            assertThat(saved.sound).isEqualTo(settings)
            assertThat(saved.vibrate).isFalse()
        }
}
