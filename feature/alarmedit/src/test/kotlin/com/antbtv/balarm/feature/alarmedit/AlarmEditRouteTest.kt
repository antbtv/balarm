package com.antbtv.balarm.feature.alarmedit

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.ConfirmDialogTestTags
import com.antbtv.balarm.core.designsystem.component.DayPreset
import com.antbtv.balarm.core.designsystem.component.LabelFieldTestTags
import com.antbtv.balarm.core.designsystem.component.PresetChipsTestTags
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.domain.testing.testAlarmRunner
import com.antbtv.balarm.core.domain.testing.testEngine
import com.antbtv.balarm.core.format.alarmRingsInText
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Сквозной путь Route → реальный [AlarmEditViewModel] → реальный движок и тестовый звонок на фейках `:core:domain`
 * → «БД» и «AlarmManager» в памяти. Часы стоят: 05:20 в Москве.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class AlarmEditRouteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(LocalDateTime.parse("2026-09-28T05:20").atZone(moscow).toInstant(), moscow)
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val log = RecordingEventLog()
    private var snoozeFlag = true
    private val flags = FeatureFlagProvider { if (it == Feature.SNOOZE) snoozeFlag else it.defaultEnabled }
    private val engine = testEngine(repository, scheduler, clock, flags, log)
    private var closed = 0

    private fun showRoute(alarmId: Long? = null) {
        val runner = testAlarmRunner(scheduler, clock, log)
        val viewModel = AlarmEditViewModel(alarmId, repository, engine, runner, clock, flags)
        composeRule.setContent {
            BalarmTheme { AlarmEditRoute(viewModel = viewModel, onClose = { closed++ }) }
        }
        composeRule.waitForIdle()
    }

    private fun stored(alarm: Alarm = Alarm(time = LocalTime.of(7, 30), label = "Gym")): AlarmId =
        runBlocking { engine.save(alarm).id }

    private fun alarms(): List<Alarm> = runBlocking { repository.loadAll() }.map { it.alarm }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    /** Тап; элемент в прокручиваемой части сначала прокручивается в зону видимости. */
    private fun click(tag: String) {
        val scrollable = composeRule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasScrollAction()))
            .fetchSemanticsNodes().isNotEmpty()
        node(tag).apply { if (scrollable) performScrollTo() }.performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `save on an untouched new alarm creates it with the defaults, toasts and closes`() {
        showRoute()

        click(AlarmEditTestTags.SAVE)

        val saved = alarms().single()
        assertThat(saved.time).isEqualTo(LocalTime.of(6, 0))
        assertThat(saved.enabled).isTrue()
        assertThat(scheduler.scheduled).containsKey(saved.id)
        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo(alarmRingsInText(composeRule.activity, TimeUntil(days = 0, hours = 0, minutes = 40)))
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `edits reach the database`() {
        showRoute()

        click(PresetChipsTestTags.WEEKDAYS)
        node(LabelFieldTestTags.FIELD).performScrollTo().performTextInput("  Gym ")
        composeRule.waitForIdle()
        click(AlarmEditTestTags.SAVE)

        val saved = alarms().single()
        assertThat(saved.repeatDays).isEqualTo(DayPreset.WEEKDAYS.days)
        assertThat(saved.label).isEqualTo("Gym")
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `existing alarm loads, delete asks and removes it`() {
        val id = stored()
        showRoute(id.value)
        composeRule.onNode(hasText("Gym"), useUnmergedTree = true).assertExists()

        click(AlarmEditTestTags.DELETE)
        click(ConfirmDialogTestTags.CONFIRM)

        assertThat(alarms()).isEmpty()
        assertThat(scheduler.cancelled).contains(id)
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `test schedules a test ring, toasts and keeps the editor open`() {
        showRoute()

        click(AlarmEditTestTags.TEST)

        assertThat(scheduler.scheduled).containsKey(AlarmId.TEST)
        assertThat(alarms()).isEmpty()
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Test alarm in 5 seconds")
        assertThat(closed).isEqualTo(0)
        node(AlarmEditTestTags.ROOT).assertIsDisplayed()
    }

    @Test
    fun `back with changes asks, discard closes without saving`() {
        val id = stored()
        showRoute(id.value)
        node(LabelFieldTestTags.FIELD).performScrollTo().performTextInput("!")
        composeRule.waitForIdle()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        node(ConfirmDialogTestTags.DIALOG).assertIsDisplayed()
        assertThat(closed).isEqualTo(0)

        click(ConfirmDialogTestTags.CONFIRM)

        assertThat(closed).isEqualTo(1)
        assertThat(alarms().single().label).isEqualTo("Gym")
    }

    @Test
    fun `an alarm deleted while the editor was in the back stack closes it`() {
        showRoute(alarmId = 42)

        assertThat(closed).isEqualTo(1)
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun `snooze flag off hides the section`() {
        snoozeFlag = false
        showRoute()

        node(AlarmEditTestTags.SNOOZE_INTERVAL).assertDoesNotExist()
        node(AlarmEditTestTags.TEST).performScrollTo().assertIsDisplayed()
    }
}
