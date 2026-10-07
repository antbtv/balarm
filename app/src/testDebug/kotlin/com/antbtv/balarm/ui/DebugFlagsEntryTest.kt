package com.antbtv.balarm.ui

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.DebugTools
import com.antbtv.balarm.MainActivity
import com.antbtv.balarm.core.designsystem.component.NavigationBarTestTags
import com.antbtv.balarm.debug.FeatureFlagsActivity
import com.antbtv.balarm.feature.settings.AboutTestTags
import com.antbtv.balarm.feature.settings.SettingsTestTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.util.Optional
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Debug-сборка: «О приложении» → 7 тапов по версии → экран feature flags (FR-FLAG-5). В release биндинга нет. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DebugFlagsEntryTest {

    private val hilt = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(composeRule)

    @Inject lateinit var debugTools: Optional<DebugTools>

    @Before
    fun setUp() = hilt.inject()

    @Test
    fun `debug build binds the feature flags screen`() {
        assertThat(debugTools.get().featureFlagsIntent().component?.className)
            .isEqualTo(FeatureFlagsActivity::class.java.name)
    }

    @Test
    fun `seven taps on the version open the feature flags screen`() {
        composeRule.onNodeWithTag(NavigationBarTestTags.item(Tab.SETTINGS.ordinal)).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.ABOUT_ROW).performClick()
        composeRule.waitForIdle()

        repeat(SECRET_TAPS) { composeRule.onNodeWithTag(AboutTestTags.VERSION).performClick() }
        composeRule.waitForIdle()

        val started = shadowOf(composeRule.activity).nextStartedActivity
        assertThat(started?.component?.className).isEqualTo(FeatureFlagsActivity::class.java.name)
    }

    private companion object {
        const val SECRET_TAPS = 7
    }
}
