package com.antbtv.balarm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class PlaceholderScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun titleIsDisplayed() {
        composeRule.setContent {
            BalarmTheme { PlaceholderScreen(time = "06:30") }
        }

        composeRule.onNodeWithTag(PlaceholderTestTags.TITLE)
            .assertIsDisplayed()
            .assertTextEquals("Balarm")
        composeRule.onNodeWithTag(PlaceholderTestTags.TIME).assertIsDisplayed()
        composeRule.onNodeWithText("06:30").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ru")
    fun russianStringsAreUsed() {
        composeRule.setContent {
            BalarmTheme { PlaceholderScreen(time = "06:30") }
        }

        composeRule.onNodeWithText("Будильники скоро появятся").assertIsDisplayed()
    }

    @Test
    fun balarmAppShowsTimeFromInjectedClock() {
        val fixed = Clock.fixed(Instant.parse("2026-09-29T06:30:00Z"), ZoneOffset.UTC)
        composeRule.setContent {
            BalarmTheme { BalarmApp(clock = fixed) }
        }

        composeRule.onNodeWithTag(PlaceholderTestTags.TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(PlaceholderTestTags.TIME).assertIsDisplayed()
        composeRule.onNodeWithText("30", substring = true).assertIsDisplayed()
    }
}
