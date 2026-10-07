package com.antbtv.balarm.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.TopBarTestTags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w360dp-h640dp")
class AboutScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var debugOpened = 0
    private var closed = 0

    /** Часы тапов: тест сдвигает их сам. */
    private var now = 1_000L

    private fun show(debug: Boolean, versionName: String? = "1.2.3", fontScale: Float? = null) {
        composeRule.setContent {
            TestTheme(fontScale) {
                AboutScreen(
                    versionName = versionName,
                    onClose = { closed++ },
                    onOpenDebugFlags = if (debug) ({ debugOpened++ }) else null,
                    uptimeMillis = { now },
                    windowInsets = WindowInsets(0.dp),
                )
            }
        }
    }

    private fun tap(times: Int) = repeat(times) {
        composeRule.onNodeWithTag(AboutTestTags.VERSION).performClick()
        now += 100
    }

    @Test
    fun `shows version and licenses`() {
        show(debug = false)

        composeRule.onNodeWithTag(AboutTestTags.VERSION)
            .assertTextEquals(composeRule.activity.getString(R.string.about_version, "1.2.3"))
        composeRule.onNodeWithTag(AboutTestTags.LICENSES).assertIsDisplayed()
    }

    @Test
    fun `unknown version`() {
        show(debug = false, versionName = null)

        val unknown = composeRule.activity.getString(R.string.about_version_unknown)
        composeRule.onNodeWithTag(AboutTestTags.VERSION)
            .assertTextEquals(composeRule.activity.getString(R.string.about_version, unknown))
    }

    @Test
    fun `seventh tap opens debug flags exactly once`() {
        show(debug = true)

        tap(DEBUG_TAPS - 1)
        assertThat(debugOpened).isEqualTo(0)
        tap(1)
        assertThat(debugOpened).isEqualTo(1)
        // Счётчик начинается заново.
        tap(DEBUG_TAPS - 1)
        assertThat(debugOpened).isEqualTo(1)
        tap(1)
        assertThat(debugOpened).isEqualTo(2)
    }

    @Test
    fun `a long pause restarts the count`() {
        show(debug = true)

        tap(DEBUG_TAPS - 1)
        now += DEBUG_TAP_TIMEOUT_MS + 1
        tap(1)
        assertThat(debugOpened).isEqualTo(0)
        tap(DEBUG_TAPS - 2)
        assertThat(debugOpened).isEqualTo(0)
        tap(1)
        assertThat(debugOpened).isEqualTo(1)
    }

    @Test
    fun `release build - version is not clickable`() {
        show(debug = false)

        composeRule.onNodeWithTag(AboutTestTags.VERSION)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        tap(DEBUG_TAPS * 2)
        assertThat(debugOpened).isEqualTo(0)
    }

    @Test
    fun `back closes`() {
        show(debug = false)

        composeRule.onNodeWithTag(TopBarTestTags.BACK).performClick()

        assertThat(closed).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun `font scale 2 on 360dp does not clip text`() {
        show(debug = true, fontScale = 2f)

        composeRule.onNodeWithTag(AboutTestTags.VERSION).assertIsDisplayed()
        composeRule.assertNoTextOverflow()
    }

    @Test
    fun `counter unit`() {
        val counter = SecretTapCounter(required = 3, timeoutMs = 10)
        assertThat(listOf(0L, 5L, 10L).map(counter::onTap)).containsExactly(false, false, true).inOrder()
        assertThat(listOf(100L, 200L, 205L, 210L).map(counter::onTap))
            .containsExactly(false, false, false, true).inOrder()
    }
}
