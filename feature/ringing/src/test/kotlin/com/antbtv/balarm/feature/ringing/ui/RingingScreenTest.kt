package com.antbtv.balarm.feature.ringing.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class RingingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<RingingEvent>()

    private fun show(state: RingingUiState) {
        composeRule.setContent {
            BalarmTheme { RingingScreen(state = state, onEvent = { events += it }) }
        }
    }

    private fun ringingState(snooze: SnoozeUi = SnoozeUi.Limited(2), label: String = "Gym") = RingingUiState(
        now = LocalDateTime.of(2026, 10, 3, 6, 30),
        phase = RingingPhase.RINGING,
        label = label,
        snooze = snooze,
    )

    @Test
    fun `shows time, label and both buttons`() {
        show(ringingState())

        composeRule.onNodeWithTag(RingingTestTags.TIME).assertIsDisplayed()
        composeRule.onNodeWithTag(RingingTestTags.DATE).assertIsDisplayed()
        composeRule.onNodeWithTag(RingingTestTags.LABEL).assertTextEquals("Gym")
        composeRule.onNodeWithTag(RingingTestTags.SNOOZE, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(RingingTestTags.DISMISS).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun `english strings and snooze counter`() {
        show(ringingState(snooze = SnoozeUi.Limited(2)))

        composeRule.onNodeWithTag(RingingTestTags.DISMISS).assertTextEquals("Dismiss")
        composeRule.onNodeWithTag(RingingTestTags.SNOOZE).assertTextEquals("Snooze (2)")
        composeRule.onNodeWithTag(RingingTestTags.DATE).assertTextEquals("Saturday, October 3")
    }

    @Test
    @Config(qualifiers = "ru-rRU")
    fun `russian strings and snooze counter`() {
        show(ringingState(snooze = SnoozeUi.Limited(3)))

        composeRule.onNodeWithTag(RingingTestTags.DISMISS).assertTextEquals("Отключить")
        composeRule.onNodeWithTag(RingingTestTags.SNOOZE).assertTextEquals("Отложить (3)")
        composeRule.onNodeWithTag(RingingTestTags.TIME).assertTextEquals("06:30")
    }

    @Test
    fun `unlimited snooze has no counter`() {
        show(ringingState(snooze = SnoozeUi.Unlimited))

        composeRule.onNodeWithTag(RingingTestTags.SNOOZE).assertTextEquals("Snooze")
    }

    @Test
    fun `snooze is hidden when not allowed`() {
        show(ringingState(snooze = SnoozeUi.Hidden))

        composeRule.onNodeWithTag(RingingTestTags.SNOOZE).assertDoesNotExist()
        composeRule.onNodeWithTag(RingingTestTags.DISMISS).assertIsDisplayed()
    }

    @Test
    fun `blank label is not shown`() {
        show(ringingState(label = ""))

        composeRule.onNodeWithTag(RingingTestTags.LABEL).assertDoesNotExist()
    }

    @Test
    fun `buttons send events`() {
        show(ringingState())

        composeRule.onNodeWithTag(RingingTestTags.SNOOZE).performClick()
        composeRule.onNodeWithTag(RingingTestTags.DISMISS).performClick()

        assertThat(events).containsExactly(RingingEvent.Snooze, RingingEvent.Dismiss).inOrder()
    }

    @Test
    fun `no buttons while waiting for the service`() {
        show(RingingUiState(now = LocalDateTime.of(2026, 10, 3, 6, 30), phase = RingingPhase.WAITING))

        composeRule.onNodeWithTag(RingingTestTags.TIME).assertIsDisplayed()
        composeRule.onNodeWithTag(RingingTestTags.DISMISS).assertDoesNotExist()
        composeRule.onNodeWithTag(RingingTestTags.SNOOZE).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `buttons stay reachable at font scale 2 on a narrow screen`() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = LocalDensity.current.density, fontScale = 2f),
            ) {
                BalarmTheme { RingingScreen(state = ringingState(), onEvent = { events += it }) }
            }
        }

        composeRule.onNodeWithTag(RingingTestTags.DISMISS).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(RingingTestTags.SNOOZE).assertIsDisplayed()
        assertThat(events).containsExactly(RingingEvent.Dismiss)
    }
}
