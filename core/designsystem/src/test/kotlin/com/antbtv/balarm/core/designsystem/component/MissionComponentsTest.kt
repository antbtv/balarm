package com.antbtv.balarm.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MissionComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `progress bar is one accessible node with description and range`() {
        composeRule.setContent {
            BalarmTheme {
                MissionProgressBar(
                    done = 2,
                    total = 5,
                    label = "2/5",
                    description = "Solved 2 of 5",
                    idleFraction = { 0.5f },
                )
            }
        }

        composeRule.onNodeWithTag(MissionProgressBarTestTags.BAR)
            .assertContentDescriptionEquals("Solved 2 of 5")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(current = 2f, range = 0f..5f, steps = 4),
                ),
            )
        composeRule.onNodeWithTag(MissionProgressBarTestTags.LABEL, useUnmergedTree = true).assertTextEquals("2/5")
        composeRule.onNodeWithTag(MissionProgressBarTestTags.IDLE).assertIsDisplayed()
    }

    @Test
    fun `progress bar tolerates an empty total`() {
        composeRule.setContent {
            BalarmTheme {
                MissionProgressBar(done = 3, total = 0, label = "", description = "", idleFraction = { 2f })
            }
        }

        composeRule.onNodeWithTag(MissionProgressBarTestTags.BAR).assertIsDisplayed()
    }

    @Test
    fun `shake gives reject haptic once per new error`() {
        val haptics = mutableListOf<HapticFeedbackType>()
        val feedback = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                haptics += hapticFeedbackType
            }
        }
        var errors by mutableIntStateOf(0)
        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides feedback) {
                BalarmTheme { ShakeOnError(errorCount = errors) { Text("= 42") } }
            }
        }
        composeRule.onNodeWithText("= 42").assertIsDisplayed()
        assertThat(haptics).isEmpty()

        errors = 1
        composeRule.waitForIdle()
        errors = 2
        composeRule.waitForIdle()

        assertThat(haptics).containsExactly(HapticFeedbackType.Reject, HapticFeedbackType.Reject)
    }
}
