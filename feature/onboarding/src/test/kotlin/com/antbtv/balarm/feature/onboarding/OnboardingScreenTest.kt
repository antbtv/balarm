package com.antbtv.balarm.feature.onboarding

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.OnboardingStepTestTags
import com.antbtv.balarm.core.domain.health.HealthItem
import com.antbtv.balarm.core.permissions.titleRes
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w360dp-h640dp")
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<OnboardingEvent>()
    private var state by mutableStateOf(OnboardingUiState())

    private fun show(initial: OnboardingUiState, fontScale: Float? = null) {
        state = initial
        composeRule.setContent {
            TestTheme(fontScale) { OnboardingScreen(state = state, onEvent = { events += it }) }
        }
    }

    private fun text(id: Int): String = composeRule.activity.getString(id)

    private fun node(tag: String) = composeRule.onNodeWithTag(tag)

    @Test
    fun `every step shows its own title and illustration`() {
        show(OnboardingUiState())
        OnboardingStep.entries.forEach { step ->
            val s = stepState(step)
            state = s
            composeRule.waitForIdle()

            node(OnboardingStepTestTags.TITLE).assertTextEquals(text(s.fixItem!!.titleRes))
            node(OnboardingTestTags.illustration(step)).assertExists()
            node(OnboardingStepTestTags.PROGRESS).assertExists()
        }
    }

    @Test
    fun `loading and finished states are a blank background`() {
        show(OnboardingUiState())
        node(OnboardingTestTags.BLANK).assertIsDisplayed()
        node(OnboardingStepTestTags.LAYOUT).assertDoesNotExist()

        state = OnboardingUiState(loading = false, step = null)
        composeRule.waitForIdle()
        node(OnboardingTestTags.BLANK).assertIsDisplayed()
    }

    @Test
    fun `battery in restricted mode asks to lift the restriction`() {
        show(stepState(OnboardingStep.BATTERY, restricted = true))

        node(OnboardingStepTestTags.TITLE).assertTextEquals(text(HealthItem.BACKGROUND_RESTRICTION.titleRes))
        node(OnboardingStepTestTags.PRIMARY).assertTextEquals(text(R.string.onboarding_open_settings))
    }

    @Test
    fun `primary label - allow for in-place dialogs, open settings for settings screens`() {
        assertThat(HealthItem.NOTIFICATIONS.primaryLabelRes).isEqualTo(R.string.onboarding_allow)
        assertThat(HealthItem.BATTERY_OPTIMIZATION.primaryLabelRes).isEqualTo(R.string.onboarding_allow)
        listOf(
            HealthItem.EXACT_ALARMS,
            HealthItem.FULL_SCREEN_INTENT,
            HealthItem.OVERLAY,
            HealthItem.BACKGROUND_RESTRICTION,
            HealthItem.DO_NOT_DISTURB,
        ).forEach { assertThat(it.primaryLabelRes).isEqualTo(R.string.onboarding_open_settings) }
    }

    @Test
    fun `primary sends Primary`() {
        show(stepState(OnboardingStep.NOTIFICATIONS))

        node(OnboardingStepTestTags.PRIMARY).assertTextEquals(text(R.string.onboarding_allow)).performClick()

        assertThat(events).containsExactly(OnboardingEvent.Primary)
    }

    @Test
    fun `critical step before an attempt has no postpone and no warning`() {
        show(stepState(OnboardingStep.NOTIFICATIONS))

        node(OnboardingStepTestTags.SECONDARY).assertDoesNotExist()
        node(OnboardingStepTestTags.WARNING).assertDoesNotExist()
    }

    @Test
    fun `recommended step offers Later without a warning`() {
        show(stepState(OnboardingStep.OVERLAY, canPostpone = true))

        node(OnboardingStepTestTags.WARNING).assertDoesNotExist()
        node(OnboardingStepTestTags.SECONDARY).assertTextEquals(text(R.string.onboarding_later)).performClick()

        assertThat(events).containsExactly(OnboardingEvent.Postpone)
    }

    @Test
    fun `critical step after an attempt offers Continue without it with a warning`() {
        show(stepState(OnboardingStep.FULL_SCREEN_INTENT, canPostpone = true, warn = true))

        node(OnboardingStepTestTags.WARNING)
            .assertIsDisplayed()
            .assertTextEquals(text(R.string.onboarding_skip_warning))
        node(OnboardingStepTestTags.SECONDARY)
            .assertTextEquals(text(R.string.onboarding_continue_without))
            .performClick()

        assertThat(events).containsExactly(OnboardingEvent.Postpone)
    }

    @Test
    fun `oem - Done is disabled until I've done it is checked, then confirms`() {
        show(stepState(OnboardingStep.OEM_BACKGROUND, canPostpone = true))

        node(OnboardingStepTestTags.PRIMARY)
            .assertTextEquals(text(R.string.onboarding_done))
            .assertIsNotEnabled()
            .performClick()
        assertThat(events).isEmpty()

        node(OnboardingTestTags.OEM_CONFIRM).performScrollTo().assertIsOff().performClick()
        node(OnboardingTestTags.OEM_CONFIRM).assertIsOn()
        // Отметка только локальная: шаг не закрывается, пока не нажато «Готово».
        assertThat(events).isEmpty()

        node(OnboardingStepTestTags.PRIMARY).assertIsEnabled().performClick()
        assertThat(events).containsExactly(OnboardingEvent.OemConfirmed(confirmed = true))
    }

    @Test
    fun `oem - without the mark the way out is Later`() {
        show(stepState(OnboardingStep.OEM_BACKGROUND, canPostpone = true))

        node(OnboardingStepTestTags.SECONDARY).assertTextEquals(text(R.string.onboarding_later)).performClick()

        assertThat(events).containsExactly(OnboardingEvent.Postpone)
    }

    @Test
    fun `oem - open app settings sends Primary`() {
        show(stepState(OnboardingStep.OEM_BACKGROUND, canPostpone = true))

        node(OnboardingTestTags.OEM_OPEN_SETTINGS).performScrollTo().performClick()

        assertThat(events).containsExactly(OnboardingEvent.Primary)
    }

    @Test
    fun `oem - guide opens the browser`() {
        show(stepState(OnboardingStep.OEM_BACKGROUND, canPostpone = true))

        node(OnboardingTestTags.OEM_GUIDE).performScrollTo().performClick()

        val intent = shadowOf(composeRule.activity).nextStartedActivity
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.dataString).isEqualTo(OEM_GUIDE_URL)
        assertThat(events).isEmpty()
    }

    @Test
    fun `oem - I've done it survives recreation`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            TestTheme {
                OnboardingScreen(state = stepState(OnboardingStep.OEM_BACKGROUND, canPostpone = true), onEvent = {})
            }
        }
        node(OnboardingTestTags.OEM_CONFIRM).performScrollTo().performClick()

        restoration.emulateSavedInstanceStateRestore()

        node(OnboardingTestTags.OEM_CONFIRM).performScrollTo().assertIsOn()
        node(OnboardingStepTestTags.PRIMARY).assertIsEnabled()
    }

    @Test
    fun `a new step starts with I've done it unchecked`() {
        show(stepState(OnboardingStep.OEM_BACKGROUND, canPostpone = true))
        node(OnboardingTestTags.OEM_CONFIRM).performScrollTo().performClick()

        state = stepState(OnboardingStep.DO_NOT_DISTURB, canPostpone = true)
        composeRule.waitForIdle()
        state = stepState(OnboardingStep.OEM_BACKGROUND, canPostpone = true)
        composeRule.waitForIdle()

        node(OnboardingTestTags.OEM_CONFIRM).performScrollTo().assertIsOff()
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun `font scale 2 on 360dp - every step unclipped, buttons on screen`() {
        show(OnboardingUiState(), fontScale = 2f)
        val variants = OnboardingStep.entries.map { stepState(it, canPostpone = true, warn = it.ordinal < 3) } +
            stepState(OnboardingStep.BATTERY, canPostpone = true, warn = true, restricted = true)
        variants.forEach { s ->
            state = s
            composeRule.waitForIdle()

            composeRule.assertNoTextOverflow()
            val root = composeRule.onRoot().getUnclippedBoundsInRoot()
            listOf(OnboardingStepTestTags.PRIMARY, OnboardingStepTestTags.SECONDARY).forEach { tag ->
                val bounds = node(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
                assertThat(bounds.bottom).isAtMost(root.bottom)
                assertThat(bounds.right).isAtMost(root.right)
            }
        }
    }

    private fun stepState(
        step: OnboardingStep,
        canPostpone: Boolean = false,
        warn: Boolean = false,
        restricted: Boolean = false,
    ) = OnboardingUiState(
        loading = false,
        step = step,
        stepNumber = step.ordinal + 1,
        fixItem = when {
            restricted -> HealthItem.BACKGROUND_RESTRICTION
            step == OnboardingStep.BATTERY -> HealthItem.BATTERY_OPTIMIZATION
            else -> HealthItem.valueOf(step.name)
        },
        batteryRestricted = restricted,
        canPostpone = canPostpone,
        warnOnSkip = warn,
    )
}
