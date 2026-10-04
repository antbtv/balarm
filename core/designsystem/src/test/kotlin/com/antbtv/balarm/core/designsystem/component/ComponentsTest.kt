package com.antbtv.balarm.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** DayPillsRow, BalarmSwitch, BalarmFab, NextAlarmHeader, ConfirmDialog. */
@RunWith(AndroidJUnit4::class)
class ComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `day pills row reads selected days as one description and is not clickable`() {
        val days = listOf(
            DayPillUi("Mo", selected = true, description = "Monday"),
            DayPillUi("Tu", selected = false, description = "Tuesday"),
            DayPillUi("We", selected = true, description = "Wednesday"),
        )
        composeRule.setContent { BalarmTheme { DayPillsRow(days = days) } }

        composeRule.onNodeWithTag(DayPillsTestTags.ROW)
            .assertContentDescriptionEquals("Monday, Wednesday")
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        composeRule.onNodeWithText("Mo").assertDoesNotExist()
    }

    @Test
    fun `day pills row uses explicit row description`() {
        composeRule.setContent {
            BalarmTheme {
                DayPillsRow(
                    days = listOf(DayPillUi("Mo", selected = true, description = "Monday")),
                    rowDescription = "Weekdays",
                )
            }
        }

        composeRule.onNodeWithTag(DayPillsTestTags.ROW).assertContentDescriptionEquals("Weekdays")
    }

    @Test
    fun `standalone switch toggles with its description`() {
        val values = mutableListOf<Boolean>()
        composeRule.setContent {
            BalarmTheme {
                BalarmSwitch(
                    checked = false,
                    onCheckedChange = { values += it },
                    contentDescription = "Vibration",
                    modifier = Modifier.testTag(TAG),
                )
            }
        }

        composeRule.onNodeWithTag(TAG)
            .assertContentDescriptionEquals("Vibration")
            .assertHeightIsAtLeast(BalarmDimens.MinTouch)
            .performClick()

        assertThat(values).containsExactly(true)
    }

    @Test
    fun `fab clicks, is described and is 64dp`() {
        var clicks = 0
        composeRule.setContent {
            BalarmTheme {
                BalarmFab(onClick = { clicks++ }, contentDescription = "Add alarm", modifier = Modifier.testTag(TAG))
            }
        }

        composeRule.onNodeWithTag(TAG)
            .assertContentDescriptionEquals("Add alarm")
            .assertWidthIsAtLeast(BalarmDimens.Fab)
            .assertHeightIsAtLeast(BalarmDimens.Fab)
            .performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `header is a single heading node reading the full title and the subtitle`() {
        composeRule.setContent {
            BalarmTheme {
                NextAlarmHeader(
                    title = "Next alarm in 7 h 12 min",
                    subtitle = "Tomorrow, 06:30",
                    titleDescription = "Next alarm in 7 hours 12 minutes",
                )
            }
        }

        composeRule.onNodeWithTag(NextAlarmHeaderTestTags.HEADER)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertContentDescriptionEquals("Next alarm in 7 hours 12 minutes. Tomorrow, 06:30")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        // Подпись видна на экране, но отдельным узлом TalkBack не читается — она уже в описании заголовка.
        composeRule.onNodeWithText("Tomorrow, 06:30").assertDoesNotExist()
        composeRule.onNodeWithText("Tomorrow, 06:30", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `header without subtitle shows only title`() {
        composeRule.setContent { BalarmTheme { NextAlarmHeader(title = "No active alarms", subtitle = null) } }

        composeRule.onNodeWithText("No active alarms", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(NextAlarmHeaderTestTags.HEADER)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertContentDescriptionEquals("No active alarms")
    }

    @Test
    fun `confirm dialog buttons call their callbacks`() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            BalarmTheme {
                ConfirmDialog(
                    title = "Delete alarm?",
                    text = "Alarm 06:30 will be deleted.",
                    confirmText = "Delete",
                    dismissText = "Cancel",
                    onConfirm = { calls += "confirm" },
                    onDismiss = { calls += "dismiss" },
                    destructive = true,
                )
            }
        }

        composeRule.onNodeWithText("Delete alarm?").assertIsDisplayed()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.CONFIRM)
            .assertHeightIsAtLeast(BalarmDimens.MinTouch)
            .performClick()
        composeRule.onNodeWithTag(ConfirmDialogTestTags.DISMISS)
            .assertHeightIsAtLeast(BalarmDimens.MinTouch)
            .performClick()

        assertThat(calls).containsExactly("confirm", "dismiss").inOrder()
    }

    private companion object {
        const val TAG = "subject"
    }
}
