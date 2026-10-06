package com.antbtv.balarm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.MainActivity
import com.antbtv.balarm.R
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.feature.alarmlist.AlarmListTestTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Граф навигации (ADR-009): список — стартовый экран, FAB и карточка ведут в редактор, Back возвращает. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class BalarmNavigationTest {

    private val hilt = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    // Hilt должен собрать граф раньше, чем стартует Activity.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(composeRule)

    @Inject lateinit var repository: FakeAlarmRepository

    @Before
    fun setUp() = hilt.inject()

    private fun string(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun `the alarm list is the start screen`() {
        composeRule.onNodeWithTag(AlarmListTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(AlarmEditStubTags.ROOT).assertDoesNotExist()
    }

    @Test
    fun `fab opens the editor for a new alarm and back returns to the list`() {
        composeRule.onNodeWithTag(AlarmListTestTags.FAB).performClick()

        composeRule.onNodeWithTag(AlarmEditStubTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(AlarmEditStubTags.SUBJECT).assertTextEquals(string(R.string.edit_stub_new))
        composeRule.onNodeWithTag(AlarmListTestTags.ROOT).assertDoesNotExist()

        back()

        composeRule.onNodeWithTag(AlarmListTestTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(AlarmEditStubTags.ROOT).assertDoesNotExist()
    }

    @Test
    fun `a card opens the editor with that alarm`() {
        val id = runBlocking { repository.save(Alarm(time = LocalTime.of(7, 30))) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AlarmListTestTags.card(id)).performClick()

        composeRule.onNodeWithTag(AlarmEditStubTags.SUBJECT)
            .assertTextEquals(string(R.string.edit_stub_existing, id.value))
    }

    @Test
    fun `a card on the list stays below the editor and returns after back`() {
        val id = runBlocking { repository.save(Alarm(time = LocalTime.of(7, 30))) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AlarmListTestTags.card(id)).performClick()
        composeRule.onNodeWithTag(AlarmListTestTags.ROOT).assertDoesNotExist()

        back()

        composeRule.onNodeWithTag(AlarmListTestTags.card(id)).assertIsDisplayed()
    }

    @Test
    fun `back on the start screen closes the app`() {
        back()

        assertThat(composeRule.activity.isFinishing).isTrue()
    }

    @Test
    fun `the new alarm editor survives recreation of the activity`() {
        composeRule.onNodeWithTag(AlarmListTestTags.FAB).performClick()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(AlarmEditStubTags.ROOT).assertIsDisplayed()
    }

    @Test
    fun `the editor of an existing alarm survives recreation with its id`() {
        val id = runBlocking { repository.save(Alarm(time = LocalTime.of(7, 30))) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AlarmListTestTags.card(id)).performClick()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(AlarmEditStubTags.SUBJECT)
            .assertTextEquals(string(R.string.edit_stub_existing, id.value))
    }
}
