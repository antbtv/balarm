package com.antbtv.balarm.feature.alarmedit

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.TopBarTestTags
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.FakeSettingsRepository
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.domain.testing.testAlarmRunner
import com.antbtv.balarm.core.domain.testing.testEngine
import com.antbtv.balarm.core.format.ClockFormat
import com.antbtv.balarm.core.format.WeekdayFormat
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.MissionSpec
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.antbtv.balarm.feature.missions.api.MissionDefinition
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Строка «Миссия» и подэкран настройки (M5-T05): экран без ViewModel с настоящим [AlarmEditReducer], плюс Route
 * с ViewModel на фейках — «Превью» уходит колбэком `onPreviewMission`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w360dp-h640dp")
class AlarmEditMissionSectionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<AlarmEditEvent>()
    private var state by mutableStateOf(state())

    private fun state(alarm: Alarm = GYM, missions: List<MissionDefinition> = listOf(FakeMission)) = AlarmEditUiState(
        loading = false,
        isNew = false,
        initial = alarm,
        draft = alarm,
        snoozeVisible = true,
        missions = missions,
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

    private val mathOption = AlarmEditTestTags.missionOption(MissionTypeId.MATH.value)

    private fun openSettings() {
        click(AlarmEditTestTags.MISSION)
        node(AlarmEditTestTags.MISSION_SETTINGS).assertIsDisplayed()
    }

    @Test
    fun `without available missions there is no mission row`() {
        show(state(missions = emptyList()))

        node(AlarmEditTestTags.MISSION_SECTION).assertDoesNotExist()
        node(AlarmEditTestTags.MISSION).assertDoesNotExist()
        node(AlarmEditTestTags.TEST).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the row shows none, then the summary of the chosen mission`() {
        show()
        node(AlarmEditTestTags.MISSION).performScrollTo().assertContentDescriptionEquals("Mission, None")

        openSettings()
        node(AlarmEditTestTags.CONTENT).assertDoesNotExist()
        node(AlarmEditTestTags.SAVE).assertDoesNotExist()
        node(AlarmEditTestTags.MISSION_NONE).assertIsSelected()
        node(mathOption).assertIsNotSelected()
        node(AlarmEditTestTags.MISSION_CONFIG).assertDoesNotExist()
        node(AlarmEditTestTags.MISSION_PREVIEW).assertDoesNotExist()

        click(mathOption)
        node(mathOption).assertIsSelected()
        node(AlarmEditTestTags.MISSION_NONE).assertIsNotSelected()
        assertThat(state.draft.mission).isEqualTo(MissionSpec(MissionTypeId.MATH, FakeMission.DEFAULT))

        click(AlarmEditTestTags.MISSION_DONE)
        node(AlarmEditTestTags.MISSION_SETTINGS).assertDoesNotExist()
        node(AlarmEditTestTags.MISSION).performScrollTo().assertContentDescriptionEquals("Mission, level 2")
        node(AlarmEditTestTags.SAVE).assertIsDisplayed()
    }

    @Test
    fun `the config editor changes the draft and preview is requested`() {
        show(state(GYM.copy(mission = MissionSpec(MissionTypeId.MATH, "2"))))
        openSettings()

        click(FakeMission.UP)
        assertThat(state.draft.mission).isEqualTo(MissionSpec(MissionTypeId.MATH, "3"))

        click(AlarmEditTestTags.MISSION_PREVIEW)
        assertThat(events.last()).isEqualTo(AlarmEditEvent.PreviewMission)
    }

    @Test
    fun `choosing none removes the mission`() {
        show(state(GYM.copy(mission = MissionSpec(MissionTypeId.MATH, "4"))))
        openSettings()

        click(AlarmEditTestTags.MISSION_NONE)

        assertThat(state.draft.mission).isNull()
        node(AlarmEditTestTags.MISSION_CONFIG).assertDoesNotExist()
        node(AlarmEditTestTags.MISSION_PREVIEW).assertDoesNotExist()
    }

    @Test
    fun `system back and the top bar back return to the form`() {
        show()
        openSettings()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        node(AlarmEditTestTags.MISSION_SETTINGS).assertDoesNotExist()
        node(AlarmEditTestTags.CONTENT).assertIsDisplayed()

        openSettings()
        click(TopBarTestTags.BACK)
        node(AlarmEditTestTags.MISSION_SETTINGS).assertDoesNotExist()
        assertThat(events).doesNotContain(AlarmEditEvent.Save)
    }

    @Test
    fun `fontScale 2 on 360dp - rows stay tappable, done and preview are reachable`() {
        show(state(GYM.copy(mission = MissionSpec(MissionTypeId.MATH, "2"))), fontScale = 2f)
        node(AlarmEditTestTags.MISSION).performScrollTo().assertIsDisplayed()
        assertThat(node(AlarmEditTestTags.MISSION).getBoundsInRoot().height).isAtLeast(BalarmDimens.ListRowMinHeight)

        openSettings()
        node(AlarmEditTestTags.MISSION_DONE).assertIsDisplayed()
        node(AlarmEditTestTags.MISSION_PREVIEW).performScrollTo().assertIsDisplayed()
        listOf(AlarmEditTestTags.MISSION_NONE, mathOption).forEach { tag ->
            val height = node(tag).performScrollTo().getBoundsInRoot().height
            assertWithMessage(tag).that(height).isAtLeast(BalarmDimens.ListRowMinHeight)
        }
    }

    // --- Route: «Превью» → колбэк навигации с нормализованной миссией ---

    @Test
    fun `route sends the chosen mission to onPreviewMission`() {
        val moscow = ZoneId.of("Europe/Moscow")
        val clock = MutableClock(LocalDateTime.parse("2026-09-28T05:20").atZone(moscow).toInstant(), moscow)
        val flags = FeatureFlagProvider { it.defaultEnabled }
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val log = RecordingEventLog()
        val viewModel = AlarmEditViewModel(
            null,
            repository,
            testEngine(repository, scheduler, clock, flags, log),
            testAlarmRunner(scheduler, clock, log),
            clock,
            flags,
            FakeSoundRepository(),
            RecordingSoundPreview(),
            missionRegistry(enabled = true),
            FakeSettingsRepository(),
        )
        val previewed = mutableListOf<MissionSpec>()
        composeRule.setContent {
            BalarmTheme {
                AlarmEditRoute(viewModel = viewModel, onClose = {}, onPreviewMission = { previewed += it })
            }
        }
        composeRule.waitForIdle()

        openSettings()
        click(mathOption)
        click(FakeMission.UP)
        click(AlarmEditTestTags.MISSION_PREVIEW)

        assertThat(previewed).containsExactly(MissionSpec(MissionTypeId.MATH, "3"))
        // Подэкран остаётся открытым: после превью пользователь возвращается к настройке.
        node(AlarmEditTestTags.MISSION_SETTINGS).assertIsDisplayed()
    }

    private companion object {
        val GYM = Alarm(id = AlarmId(1), time = LocalTime.of(6, 30), label = "Gym")
    }
}
