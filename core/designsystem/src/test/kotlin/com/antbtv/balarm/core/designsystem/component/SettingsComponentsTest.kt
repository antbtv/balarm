package com.antbtv.balarm.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** M3-T08: BalarmTopBar, недоступная кнопка HealthStatusRow. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class SettingsComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(fontScale: Float? = null, content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            val scaled = fontScale?.let { Density(density = density.density, fontScale = it) } ?: density
            CompositionLocalProvider(LocalDensity provides scaled) { BalarmTheme(content = content) }
        }
    }

    @Test
    fun `top bar back is a 48dp button with description, title is a heading`() {
        var backs = 0
        setContent { BalarmTopBar(title = "Alarm health", onBack = { backs++ }, backDescription = "Back") }

        composeRule.onNodeWithTag(TopBarTestTags.BACK)
            .assertContentDescriptionEquals("Back")
            .assertWidthIsAtLeast(BalarmDimens.MinTouch)
            .assertHeightIsAtLeast(BalarmDimens.MinTouch)
            .performClick()
        composeRule.onNodeWithTag(TopBarTestTags.TITLE)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun `top bar title wraps at font scale 2`() {
        setContent(fontScale = 2f) {
            BalarmTopBar(title = "Здоровье будильника", onBack = {}, backDescription = "Назад")
        }

        composeRule.onNodeWithTag(TopBarTestTags.TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(TopBarTestTags.BACK).assertIsDisplayed()
    }

    @Test
    fun `disabled health action is visible but not clickable`() {
        var clicks = 0
        setContent {
            HealthStatusRow(
                title = "Scheduling",
                description = null,
                status = HealthStatusUi.Problem,
                actionLabel = "Retry",
                onAction = { clicks++ },
                actionEnabled = false,
            )
        }

        composeRule.onNodeWithTag(HealthStatusRowTestTags.ACTION)
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .performClick()

        assertThat(clicks).isEqualTo(0)
    }
}
