package com.antbtv.balarm.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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

    private fun show(state: SettingsUiState, fontScale: Float? = null) {
        composeRule.setContent {
            TestTheme(fontScale) {
                SettingsScreen(
                    state = state,
                    onOpenHealth = { healthOpened++ },
                    onOpenAbout = { aboutOpened++ },
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
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun `font scale 2 on 360dp does not clip text`() {
        show(SettingsUiState(loading = false, problems = 3), fontScale = 2f)

        composeRule.onNodeWithTag(SettingsTestTags.HEALTH_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.ABOUT_ROW).assertIsDisplayed()
        composeRule.assertNoTextOverflow()
    }
}
