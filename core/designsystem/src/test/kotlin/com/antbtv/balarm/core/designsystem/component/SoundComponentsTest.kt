package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** M4-T09: SliderRow (громкость) и SwitchRow (вибрация). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class SoundComponentsTest {

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
    fun `slider is one adjustable node with the title and a spoken value, set progress snaps to a step`() {
        var value by mutableFloatStateOf(80f)
        setContent {
            SliderRow(
                title = "Volume",
                valueText = "${value.toInt()}%",
                valueDescription = "${value.toInt()} percent",
                value = value,
                onValueChange = { value = it },
                valueRange = 10f..100f,
                steps = 8,
            )
        }

        composeRule.onNodeWithTag(SliderRowTestTags.SLIDER)
            .assertContentDescriptionEquals("Volume")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "80 percent"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(43f) }
        composeRule.waitForIdle()

        assertThat(value).isEqualTo(40f)
        composeRule.onNodeWithTag(SliderRowTestTags.SLIDER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "40 percent"))
    }

    @Test
    fun `switch row toggles from anywhere, is one switch node and keeps 56dp`() {
        var checked by mutableStateOf(true)
        setContent { SwitchRow(title = "Vibration", checked = checked, onCheckedChange = { checked = it }) }

        composeRule.onNodeWithTag(SwitchRowTestTags.ROW)
            .assertHeightIsAtLeast(BalarmDimens.ListRowMinHeight)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .performClick()

        assertThat(checked).isFalse()
    }

    @Test
    fun `rows survive font scale 2 on 360dp`() {
        setContent(fontScale = 2f) {
            Column {
                SliderRow(
                    title = "Громкость будильника",
                    valueText = "100 %",
                    valueDescription = "100 процентов",
                    value = 100f,
                    onValueChange = {},
                    valueRange = 10f..100f,
                    steps = 8,
                )
                SwitchRow(title = "Вибрация при звонке будильника", checked = false, onCheckedChange = {})
            }
        }

        composeRule.onNodeWithTag(SliderRowTestTags.SLIDER).assertIsDisplayed()
        composeRule.onNodeWithTag(SwitchRowTestTags.ROW).assertIsDisplayed()
            .assertHeightIsAtLeast(BalarmDimens.ListRowMinHeight)
    }
}
