package com.antbtv.balarm.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.InMemoryTestAlarmStore
import com.antbtv.balarm.core.domain.alarm.TestAlarmRunner
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.FakePermissionHealthChecker
import com.antbtv.balarm.core.domain.testing.FakeSetupStateRepository
import com.antbtv.balarm.core.domain.testing.HEALTHY_SNAPSHOT
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.format.ClockFormat
import com.antbtv.balarm.core.model.feature.ConfigFeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** Route → настоящие ViewModel на фейках `:core:domain`: `ON_RESUME` перечитывает статусы, эффекты — тосты. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class SettingsRoutesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val clock = MutableClock(LocalDateTime.parse("2026-09-28T05:00:20").atZone(zone).toInstant(), zone)
    private val checker = FakePermissionHealthChecker()
    private val setup = FakeSetupStateRepository()
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val log = RecordingEventLog()
    private val testRunner = TestAlarmRunner(scheduler, InMemoryTestAlarmStore(), clock, log)
    private val engine = AlarmEngine(repository, scheduler, clock, ConfigFeatureFlagProvider, log, testRunner)

    @Test
    fun `settings route reads health on resume`() {
        checker.current = HEALTHY_SNAPSHOT.copy(notificationsEnabled = false, exactAlarms = false)
        val viewModel = SettingsViewModel(checker, setup, repository, ConfigFeatureFlagProvider)
        val callsBefore = checker.calls

        composeRule.setContent {
            TestTheme { SettingsRoute(onOpenHealth = {}, onOpenAbout = {}, onOpenSounds = {}, viewModel = viewModel) }
        }

        composeRule.onNodeWithTag(SettingsTestTags.HEALTH_ROW).assertTextContains("2 problems")
        assertThat(checker.calls).isGreaterThan(callsBefore)
    }

    @Test
    fun `health route - test alarm shows when it rings`() {
        val viewModel = HealthViewModel(checker, setup, repository, engine, testRunner, clock)
        composeRule.setContent { TestTheme { HealthRoute(onClose = {}, viewModel = viewModel) } }

        composeRule.onNodeWithTag(HealthTestTags.LIST).performScrollToNode(hasTestTag(HealthTestTags.TEST_ALARM))
        composeRule.onNodeWithTag(HealthTestTags.TEST_ALARM).performClick()
        composeRule.waitForIdle()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val at = LocalDateTime.ofInstant(clock.instant().plus(TestAlarmRunner.HEALTH_DELAY), zone)
        val expected = context.getString(R.string.health_test_scheduled, ClockFormat.from(context).time(at))
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(expected)
    }
}
