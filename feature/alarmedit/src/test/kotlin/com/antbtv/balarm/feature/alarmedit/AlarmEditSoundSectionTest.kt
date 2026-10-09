package com.antbtv.balarm.feature.alarmedit

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.SingleChoiceDialogTestTags
import com.antbtv.balarm.core.designsystem.component.SliderRowTestTags
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.domain.testing.testAlarmRunner
import com.antbtv.balarm.core.domain.testing.testEngine
import com.antbtv.balarm.core.format.ClockFormat
import com.antbtv.balarm.core.format.WeekdayFormat
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Секция «Звук» (M4-T09): экран без ViewModel с настоящим [AlarmEditReducer] (как `AlarmEditScreenTest`), плюс
 * Route с ViewModel на фейках — параметр выбранной мелодии и колбэк пикера.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class AlarmEditSoundSectionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<AlarmEditEvent>()
    private var state by mutableStateOf(state())

    private fun state(alarm: Alarm = GYM, soundVisible: Boolean = true, titles: Map<CustomSoundId, String>? = TITLES) =
        AlarmEditUiState(
            loading = false,
            isNew = false,
            initial = alarm,
            draft = alarm,
            snoozeVisible = true,
            soundVisible = soundVisible,
            customTitles = titles,
        )

    private fun show(initial: AlarmEditUiState = state(), fontScale: Float? = null) {
        state = initial
        composeRule.setContent {
            val density = LocalDensity.current
            val scaled = fontScale?.let { Density(density = density.density, fontScale = it) } ?: density
            CompositionLocalProvider(LocalDensity provides scaled) {
                BalarmTheme {
                    AlarmEditScreen(
                        state = state,
                        clockFormat = ClockFormat(Locale.US, is24Hour = true),
                        weekdayFormat = WeekdayFormat.forLocale(Locale.US),
                        onEvent = ::onEvent,
                        windowInsets = WindowInsets(0.dp),
                    )
                }
            }
        }
    }

    private fun onEvent(event: AlarmEditEvent) {
        events += event
        state = AlarmEditReducer.reduce(state, event)
    }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun click(tag: String) {
        val scrollable = composeRule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasScrollAction()))
            .fetchSemanticsNodes().isNotEmpty()
        node(tag).apply { if (scrollable) performScrollTo() }.performClick()
        composeRule.waitForIdle()
    }

    private fun stateIs(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun `with the flag off there is no sound section`() {
        show(state(soundVisible = false))

        node(AlarmEditTestTags.SOUND_SECTION).assertDoesNotExist()
        node(AlarmEditTestTags.SOUND).assertDoesNotExist()
        node(AlarmEditTestTags.VOLUME).assertDoesNotExist()
        node(AlarmEditTestTags.FADE_IN).assertDoesNotExist()
        node(AlarmEditTestTags.VIBRATE).assertDoesNotExist()
        node(AlarmEditTestTags.TEST).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the section shows ringtone, volume, fade-in and vibration with talkback descriptions`() {
        show()

        node(AlarmEditTestTags.SOUND).performScrollTo().assertIsDisplayed()
            .assertContentDescriptionEquals("Ringtone, Marimba")
        composeRule.onNodeWithTag(SliderRowTestTags.VALUE, useUnmergedTree = true).assertTextEquals("60%")
        node(SliderRowTestTags.SLIDER).performScrollTo().assertIsDisplayed()
            .assertContentDescriptionEquals("Volume")
            .assert(stateIs("60 percent"))
        node(AlarmEditTestTags.FADE_IN).performScrollTo().assertContentDescriptionEquals("Gradual volume, 30 seconds")
        node(AlarmEditTestTags.VIBRATE).performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun `tapping the ringtone row asks for the picker`() {
        show()

        click(AlarmEditTestTags.SOUND)

        assertThat(events).containsExactly(AlarmEditEvent.PickSound)
    }

    @Test
    fun `moving the slider sends the new volume and the value follows`() {
        show()

        node(SliderRowTestTags.SLIDER).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }
        composeRule.waitForIdle()

        assertThat(events).contains(AlarmEditEvent.VolumeChanged(80))
        assertThat(state.draft.sound.volumePercent).isEqualTo(80)
        composeRule.onNodeWithTag(SliderRowTestTags.VALUE, useUnmergedTree = true).assertTextEquals("80%")
        node(SliderRowTestTags.SLIDER).assert(stateIs("80 percent"))
    }

    @Test
    fun `fade-in dialog shows the options with the current one selected and applies a choice`() {
        show()

        click(AlarmEditTestTags.FADE_IN)
        node(SingleChoiceDialogTestTags.DIALOG).assertIsDisplayed()
        // Выкл, 15 с, 30 с, 1 мин; выбрано текущее 30 с.
        node(SingleChoiceDialogTestTags.option(2)).assertIsSelected()
        click(SingleChoiceDialogTestTags.option(3))

        assertThat(events).contains(AlarmEditEvent.FadeInSelected(Duration.ofSeconds(60)))
        node(SingleChoiceDialogTestTags.DIALOG).assertDoesNotExist()
        node(AlarmEditTestTags.FADE_IN).performScrollTo().assertContentDescriptionEquals("Gradual volume, 1 minute")
    }

    @Test
    fun `the vibration row toggles with a tap anywhere on it`() {
        show()

        click(AlarmEditTestTags.VIBRATE)

        assertThat(events).containsExactly(AlarmEditEvent.VibrateChanged(false))
        node(AlarmEditTestTags.VIBRATE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    }

    @Test
    fun `a deleted custom ringtone reads as deleted with a hint to pick another`() {
        show(state(GYM.copy(sound = SoundSettings(SoundRef.Custom(CustomSoundId(99))))))

        node(AlarmEditTestTags.SOUND).performScrollTo().assertContentDescriptionEquals("Ringtone, Ringtone deleted")
        node(AlarmEditTestTags.SOUND_MISSING_HINT).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a custom ringtone shows its title`() {
        show(state(GYM.copy(sound = SoundSettings(SoundRef.Custom(CustomSoundId(7))))))

        node(AlarmEditTestTags.SOUND).performScrollTo().assertContentDescriptionEquals("Ringtone, Birds")
        node(AlarmEditTestTags.SOUND_MISSING_HINT).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp")
    fun `font scale 2 on a narrow screen - every sound control is reachable and not under save`() {
        show(state(GYM.copy(sound = SoundSettings(SoundRef.Custom(CustomSoundId(99))))), fontScale = 2f)

        val save = node(AlarmEditTestTags.SAVE).assertIsDisplayed().getBoundsInRoot()
        listOf(
            AlarmEditTestTags.SOUND,
            AlarmEditTestTags.SOUND_MISSING_HINT,
            SliderRowTestTags.SLIDER,
            AlarmEditTestTags.FADE_IN,
            AlarmEditTestTags.VIBRATE,
        ).forEach { tag ->
            node(tag).performScrollTo().assertIsDisplayed()
            val bounds = node(tag).getBoundsInRoot()
            assertWithMessage(tag).that(bounds.bottom).isAtMost(save.top)
        }
        listOf(
            AlarmEditTestTags.SOUND,
            AlarmEditTestTags.VOLUME,
            AlarmEditTestTags.FADE_IN,
            AlarmEditTestTags.VIBRATE,
        ).forEach { tag ->
            assertWithMessage(tag).that(node(tag).getBoundsInRoot().height).isAtLeast(BalarmDimens.ListRowMinHeight)
        }
    }

    // --- Route: выбор из пикера приходит параметром, тап открывает пикер ---

    @Test
    fun `route applies the picked ringtone once, reports it consumed and opens the picker with it`() {
        val moscow = ZoneId.of("Europe/Moscow")
        val clock = MutableClock(LocalDateTime.parse("2026-09-28T05:20").atZone(moscow).toInstant(), moscow)
        val flags = FeatureFlagProvider { it == Feature.ALARM_SOUND || it.defaultEnabled }
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val log = RecordingEventLog()
        val preview = RecordingSoundPreview()
        val viewModel = AlarmEditViewModel(
            null,
            repository,
            testEngine(repository, scheduler, clock, flags, log),
            testAlarmRunner(scheduler, clock, log),
            clock,
            flags,
            FakeSoundRepository(),
            preview,
        )
        var picked by mutableStateOf<SoundRef?>(MARIMBA)
        var consumed = 0
        val pickerOpenedWith = mutableListOf<SoundRef>()
        composeRule.setContent {
            BalarmTheme {
                AlarmEditRoute(
                    viewModel = viewModel,
                    onClose = {},
                    onPickSound = { pickerOpenedWith += it },
                    pickedSound = picked,
                    onSoundPickConsumed = {
                        consumed++
                        picked = null
                    },
                )
            }
        }
        composeRule.waitForIdle()

        assertThat(viewModel.uiState.value.draft.sound.sound).isEqualTo(MARIMBA)
        assertThat(consumed).isEqualTo(1)
        node(AlarmEditTestTags.SOUND).performScrollTo().assertContentDescriptionEquals("Ringtone, Marimba")

        click(AlarmEditTestTags.SOUND)
        assertThat(pickerOpenedWith).containsExactly(MARIMBA)
        assertThat(preview.stops).isAtLeast(1)
    }

    private companion object {
        val MARIMBA = SoundRef.Builtin(BuiltinSound.MARIMBA)
        val TITLES = mapOf(CustomSoundId(7) to "Birds")
        val GYM = Alarm(
            id = AlarmId(1),
            time = LocalTime.of(6, 30),
            label = "Gym",
            sound = SoundSettings(MARIMBA, volumePercent = 60, fadeIn = Duration.ofSeconds(30)),
        )
    }
}
