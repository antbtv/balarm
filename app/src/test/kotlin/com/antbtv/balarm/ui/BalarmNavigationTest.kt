package com.antbtv.balarm.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.MainActivity
import com.antbtv.balarm.core.designsystem.component.ConfirmDialogTestTags
import com.antbtv.balarm.core.designsystem.component.LabelFieldTestTags
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.feature.alarmedit.AlarmEditTestTags
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
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowToast

/**
 * Граф навигации (ADR-009): список — стартовый экран, FAB и карточка ведут в редактор, Back возвращает.
 * Редактор — настоящий `AlarmEditRoute`: его ViewModel собирает граф Hilt приложения (smoke assisted-инъекции).
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = "en-rUS")
@RunWith(AndroidJUnit4::class)
class BalarmNavigationTest {

    private val hilt = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    // Hilt должен собрать граф раньше, чем стартует Activity.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(composeRule)

    @Inject lateinit var repository: FakeAlarmRepository

    @Before
    fun setUp() {
        hilt.inject()
        // Настоящий AlarmSchedulerImpl: без разрешения на точные будильники планирование отклоняется.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    /** Системный Back — через диспетчер Activity, как жест/кнопка на устройстве. */
    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun stored(label: String = "Gym"): Long = runBlocking {
        repository.save(Alarm(time = LocalTime.of(7, 30), label = label)).value
    }.also { composeRule.waitForIdle() }

    private fun savedAlarms(): List<Alarm> = runBlocking { repository.loadAll() }.map { it.alarm }

    private fun editor(): SemanticsNodeInteraction = composeRule.onNodeWithTag(AlarmEditTestTags.ROOT)

    private fun list(): SemanticsNodeInteraction = composeRule.onNodeWithTag(AlarmListTestTags.ROOT)

    private fun inEditor(text: String): SemanticsNodeInteraction = composeRule.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag(AlarmEditTestTags.ROOT)),
        useUnmergedTree = true,
    )

    private fun openCard(id: Long) {
        composeRule.onNodeWithTag(AlarmListTestTags.card(AlarmId(id))).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the alarm list is the start screen`() {
        list().assertIsDisplayed()
        editor().assertDoesNotExist()
    }

    @Test
    fun `fab opens the editor for a new alarm and back without changes returns to the list`() {
        composeRule.onNodeWithTag(AlarmListTestTags.FAB).performClick()

        editor().assertIsDisplayed()
        composeRule.onNodeWithTag(AlarmEditTestTags.TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(AlarmEditTestTags.DELETE).assertDoesNotExist()
        list().assertDoesNotExist()

        back()

        list().assertIsDisplayed()
        editor().assertDoesNotExist()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun `a card opens the editor with that alarm`() {
        val id = stored(label = "Gym")

        openCard(id)

        editor().assertIsDisplayed()
        inEditor("Gym").assertExists()
        composeRule.onNodeWithTag(AlarmEditTestTags.DELETE).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a card on the list stays below the editor and returns after back`() {
        val id = stored()
        openCard(id)
        list().assertDoesNotExist()

        back()

        composeRule.onNodeWithTag(AlarmListTestTags.card(AlarmId(id))).assertIsDisplayed()
    }

    /**
     * Главная проверка порядка обработчиков Back: `BackHandler` редактора должен получить системный Back раньше,
     * чем `NavDisplay.onBack`. Иначе несохранённые правки пропали бы без вопроса.
     */
    @Test
    fun `back with unsaved changes asks to discard and keeps the editor open`() {
        composeRule.onNodeWithTag(AlarmListTestTags.FAB).performClick()
        composeRule.onNodeWithTag(LabelFieldTestTags.FIELD).performScrollTo().performTextInput("Gym")
        composeRule.waitForIdle()

        back()

        composeRule.onNodeWithTag(ConfirmDialogTestTags.DIALOG).assertIsDisplayed()
        editor().assertExists()
        list().assertDoesNotExist()

        // «Продолжить» — остаёмся с правками.
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DISMISS).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DIALOG).assertDoesNotExist()
        editor().assertExists()
        inEditor("Gym").assertExists()

        // «Не сохранять» — редактор закрывается, будильник не создан.
        back()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.CONFIRM).performClick()
        composeRule.waitForIdle()

        list().assertIsDisplayed()
        editor().assertDoesNotExist()
        assertThat(savedAlarms()).isEmpty()
    }

    @Test
    fun `save of a new alarm with the defaults creates it, toasts and returns to the list`() {
        composeRule.onNodeWithTag(AlarmListTestTags.FAB).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AlarmEditTestTags.SAVE).performClick()
        composeRule.waitForIdle()

        list().assertIsDisplayed()
        editor().assertDoesNotExist()
        val saved = savedAlarms()
        assertThat(saved).hasSize(1)
        composeRule.onNodeWithTag(AlarmListTestTags.card(saved.single().id)).assertIsDisplayed()
        assertThat(ShadowToast.getTextOfLatestToast()).startsWith("Alarm rings in ")
    }

    @Test
    fun `delete from the editor removes the alarm and returns to the list`() {
        val id = stored()
        openCard(id)

        composeRule.onNodeWithTag(AlarmEditTestTags.DELETE).performScrollTo().performClick()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.CONFIRM).performClick()
        composeRule.waitForIdle()

        list().assertIsDisplayed()
        composeRule.onNodeWithTag(AlarmListTestTags.card(AlarmId(id))).assertDoesNotExist()
        assertThat(savedAlarms()).isEmpty()
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

        editor().assertIsDisplayed()
        composeRule.onNodeWithTag(AlarmEditTestTags.DELETE).assertDoesNotExist()
    }

    @Test
    fun `the editor of an existing alarm survives recreation with its id`() {
        val id = stored(label = "Gym")
        openCard(id)

        composeRule.activityRule.scenario.recreate()

        editor().assertIsDisplayed()
        inEditor("Gym").assertExists()
        composeRule.onNodeWithTag(AlarmEditTestTags.DELETE).performScrollTo().assertIsDisplayed()
    }
}
