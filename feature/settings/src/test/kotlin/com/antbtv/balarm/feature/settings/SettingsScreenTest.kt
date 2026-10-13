package com.antbtv.balarm.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.SingleChoiceDialogTestTags
import com.antbtv.balarm.core.model.AutoStop
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w360dp-h640dp")
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var healthOpened = 0
    private var aboutOpened = 0
    private var soundsOpened = 0
    private val events = mutableListOf<SettingsEvent>()

    private val general = GeneralSettingsUi(
        autoStop = AutoStop.MIN_30,
        missionIdleSeconds = 15,
        missionDuckPercent = 30,
        firstDayOfWeek = null,
    )
    private val withGeneral = SettingsUiState(loading = false, generalVisible = true, general = general)

    private fun show(state: SettingsUiState, fontScale: Float? = null) {
        composeRule.setContent {
            TestTheme(fontScale) {
                SettingsScreen(
                    state = state,
                    onOpenHealth = { healthOpened++ },
                    onOpenAbout = { aboutOpened++ },
                    onOpenSounds = { soundsOpened++ },
                    onEvent = { events += it },
                    windowInsets = WindowInsets(0.dp),
                )
            }
        }
    }

    private fun text(id: Int): String = composeRule.activity.getString(id)

    @Test
    fun `healthy summary`() {
        show(SettingsUiState(loading = false, problems = 0))

        composeRule.onNodeWithTag(SettingsTestTags.HEALTH_ROW)
            .assertTextContains(text(R.string.settings_health))
            .assertTextContains(text(R.string.settings_health_ok))
    }

    @Test
    fun `problems summary uses plurals`() {
        show(SettingsUiState(loading = false, problems = 2))

        composeRule.onNodeWithTag(SettingsTestTags.HEALTH_ROW).assertTextContains("2 problems")
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun `russian plurals`() {
        show(SettingsUiState(loading = false, problems = 5))

        composeRule.onNodeWithTag(SettingsTestTags.HEALTH_ROW).assertTextContains("5 проблем")
    }

    @Test
    fun `loading shows no summary`() {
        show(SettingsUiState())

        val texts = composeRule.onNodeWithTag(SettingsTestTags.HEALTH_ROW).fetchSemanticsNode().config
            .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
            .map { it.text }
        assertThat(texts).containsExactly(text(R.string.settings_health))
    }

    @Test
    fun `rows open health and about`() {
        show(SettingsUiState(loading = false, problems = 1))

        composeRule.onNodeWithTag(SettingsTestTags.HEALTH_ROW).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.ABOUT_ROW).performClick()

        assertThat(healthOpened).isEqualTo(1)
        assertThat(aboutOpened).isEqualTo(1)
    }

    @Test
    fun `ringtones row is shown when the flag is on and opens the library`() {
        show(SettingsUiState(loading = false, soundsVisible = true))

        composeRule.onNodeWithTag(SettingsTestTags.SOUNDS_ROW)
            .assertTextContains(text(R.string.settings_sounds))
            .performClick()

        assertThat(soundsOpened).isEqualTo(1)
        assertThat(healthOpened).isEqualTo(0)
        assertThat(aboutOpened).isEqualTo(0)
    }

    @Test
    fun `ringtones row is hidden when the flag is off`() {
        show(SettingsUiState(loading = false, soundsVisible = false))

        composeRule.onNodeWithTag(SettingsTestTags.SOUNDS_ROW).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.ABOUT_ROW).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun `russian ringtones row`() {
        show(SettingsUiState(loading = false, soundsVisible = true))

        composeRule.onNodeWithTag(SettingsTestTags.SOUNDS_ROW).assertTextContains("Мелодии")
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun `font scale 2 on 360dp does not clip text`() {
        show(SettingsUiState(loading = false, problems = 3, soundsVisible = true), fontScale = 2f)

        composeRule.onNodeWithTag(SettingsTestTags.HEALTH_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SOUNDS_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.ABOUT_ROW).assertIsDisplayed()
        composeRule.assertNoTextOverflow()
    }

    @Test
    fun `general section is absent when the flag is off`() {
        show(SettingsUiState(loading = false, generalVisible = false, general = general))

        composeRule.onNodeWithTag(SettingsTestTags.GENERAL_SECTION).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.AUTO_STOP_ROW).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.settings_general)).assertDoesNotExist()
    }

    @Test
    fun `general rows show values and talkback descriptions`() {
        show(withGeneral.copy(general = general.copy(firstDayOfWeek = DayOfWeek.SUNDAY)))

        composeRule.onNodeWithTag(SettingsTestTags.AUTO_STOP_ROW).assertContentDescriptionEquals(
            "Auto-stop alarm, 30 minutes",
        )
        composeRule.onNodeWithTag(SettingsTestTags.MISSION_IDLE_ROW).assertContentDescriptionEquals(
            "Mission inactivity, 15 seconds",
        )
        composeRule.onNodeWithTag(SettingsTestTags.MISSION_VOLUME_ROW).assertContentDescriptionEquals(
            "Volume while solving, 30 percent",
        )
        composeRule.onNodeWithTag(SettingsTestTags.FIRST_DAY_ROW).assertContentDescriptionEquals(
            "First day of week, Sunday",
        )
        composeRule.onNodeWithTag(SettingsTestTags.APPLY_NEXT)
            .assertTextEquals(text(R.string.settings_general_apply_next))
        composeRule.onNode(hasText(text(R.string.settings_general)) and isHeading()).assertExists()
    }

    @Test
    fun `rows have a change action for talkback`() {
        show(withGeneral)

        composeRule.onNodeWithTag(SettingsTestTags.MISSION_IDLE_ROW)
            .assert(SemanticsMatcher("onClick label") { it.config[SemanticsActions.OnClick].label == "Change" })
    }

    @Test
    fun `loading general shows rows without values`() {
        show(withGeneral.copy(general = null))

        composeRule.onNodeWithTag(SettingsTestTags.AUTO_STOP_ROW).assertTextEquals(text(R.string.settings_auto_stop))
    }

    @Test
    fun `auto stop dialog explains never and sends the choice`() {
        show(withGeneral)

        composeRule.onNodeWithTag(SettingsTestTags.AUTO_STOP_ROW).performClick()
        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.DESCRIPTION)
            .assertTextEquals(text(R.string.settings_auto_stop_hint))
        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.option(3)).assertIsSelected()
        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.option(4))
            .assertTextEquals(text(R.string.settings_auto_stop_never_option))
            .performClick()

        assertThat(events).containsExactly(SettingsEvent.AutoStopChanged(AutoStop.NEVER))
        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun `idle, volume and first day dialogs send their choices`() {
        show(withGeneral)

        composeRule.onNodeWithTag(SettingsTestTags.MISSION_IDLE_ROW).performClick()
        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.option(1)).assertIsSelected() // 15 s
        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.option(4)).performClick() // 30 s

        composeRule.onNodeWithTag(SettingsTestTags.MISSION_VOLUME_ROW).performClick()
        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.DESCRIPTION)
            .assertTextEquals(text(R.string.settings_mission_volume_hint))
        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.option(0)).performClick() // 10 %

        composeRule.onNodeWithTag(SettingsTestTags.FIRST_DAY_ROW).performClick()
        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.option(0))
            .assertIsSelected()
            .assertTextContains("By region (", substring = true)
        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.option(1)).performClick() // Monday

        assertThat(events).containsExactly(
            SettingsEvent.MissionIdleChanged(30),
            SettingsEvent.MissionDuckChanged(10),
            SettingsEvent.FirstDayOfWeekChanged(DayOfWeek.MONDAY),
        ).inOrder()
    }

    @Test
    fun `cancel closes the dialog without a choice`() {
        show(withGeneral)

        composeRule.onNodeWithTag(SettingsTestTags.FIRST_DAY_ROW).performClick()
        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.DISMISS).performClick()

        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.DIALOG).assertDoesNotExist()
        assertThat(events).isEmpty()
    }

    @Test
    fun `save failure shows a snackbar and reports it shown`() {
        composeRule.mainClock.autoAdvance = false
        show(withGeneral.copy(saveFailed = true))
        composeRule.mainClock.advanceTimeBy(SNACKBAR_APPEAR_MS)

        composeRule.onNodeWithText(text(R.string.settings_save_failed)).assertIsDisplayed()
        assertThat(events).isEmpty()

        composeRule.mainClock.advanceTimeBy(SNACKBAR_SHORT_MS)
        assertThat(events).containsExactly(SettingsEvent.SaveFailedShown)
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun `russian general values`() {
        show(withGeneral.copy(general = general.copy(autoStop = AutoStop.NEVER, firstDayOfWeek = DayOfWeek.MONDAY)))

        composeRule.onNodeWithTag(SettingsTestTags.AUTO_STOP_ROW)
            .assertContentDescriptionEquals("Автостоп звонка, Никогда")
        composeRule.onNodeWithTag(SettingsTestTags.MISSION_IDLE_ROW)
            .assertContentDescriptionEquals("Бездействие в миссии, 15 секунд")
        composeRule.onNodeWithTag(SettingsTestTags.FIRST_DAY_ROW)
            .assertContentDescriptionEquals("Первый день недели, Понедельник")
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun `font scale 2 on 360dp does not clip the general section`() {
        show(withGeneral.copy(soundsVisible = true), fontScale = 2f)

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag(SettingsTestTags.APPLY_NEXT))
        composeRule.onNodeWithTag(SettingsTestTags.APPLY_NEXT).assertIsDisplayed()
        composeRule.assertNoTextOverflow()

        composeRule.onNodeWithTag(SettingsTestTags.MISSION_IDLE_ROW).performClick()
        composeRule.onNodeWithTag(SingleChoiceDialogTestTags.DESCRIPTION).assertExists()
        composeRule.assertNoTextOverflow()
    }

    private companion object {
        /** `SnackbarDuration.Short` — 4 с, с запасом. */
        const val SNACKBAR_SHORT_MS = 5_000L
        const val SNACKBAR_APPEAR_MS = 500L
    }
}
