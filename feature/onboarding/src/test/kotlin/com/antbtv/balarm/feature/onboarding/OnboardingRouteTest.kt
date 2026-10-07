package com.antbtv.balarm.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.designsystem.component.OnboardingStepTestTags
import com.antbtv.balarm.core.domain.health.HealthItem
import com.antbtv.balarm.core.domain.health.SetupState
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakePermissionHealthChecker
import com.antbtv.balarm.core.domain.testing.FakeSetupStateRepository
import com.antbtv.balarm.core.domain.testing.HEALTHY_SNAPSHOT
import com.antbtv.balarm.core.permissions.titleRes
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** Route → настоящая [OnboardingViewModel] на фейках `:core:domain`: `ON_RESUME`, эффекты, завершение. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class OnboardingRouteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val checker = FakePermissionHealthChecker(HEALTHY_SNAPSHOT.copy(notificationsEnabled = false))
    private val setup = FakeSetupStateRepository()
    private val repository = FakeAlarmRepository()
    private var finished = 0

    private fun show(saved: SavedStateHandle = SavedStateHandle()) {
        val viewModel = OnboardingViewModel(checker, setup, repository, saved)
        composeRule.setContent { TestTheme { OnboardingRoute(onFinished = { finished++ }, viewModel = viewModel) } }
    }

    private fun title(item: HealthItem): String = composeRule.activity.getString(item.titleRes)

    /** Возврат из системных настроек: Activity уходит в фон и возвращается — `ON_RESUME`. */
    private fun returnFromSettings() {
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
    }

    @Test
    fun `starts at the first unmet step`() {
        show()

        composeRule.onNodeWithTag(OnboardingStepTestTags.TITLE).assertTextEquals(title(HealthItem.NOTIFICATIONS))
        composeRule.onNodeWithTag(OnboardingStepTestTags.SECONDARY).assertDoesNotExist()
    }

    @Test
    fun `granting in settings and coming back moves to the next step`() {
        checker.current = HEALTHY_SNAPSHOT.copy(notificationsEnabled = false, overlay = false)
        show()

        composeRule.onNodeWithTag(OnboardingStepTestTags.PRIMARY).performClick()
        composeRule.waitForIdle()
        checker.current = checker.current.copy(notificationsEnabled = true)
        returnFromSettings()

        composeRule.onNodeWithTag(OnboardingStepTestTags.TITLE).assertTextEquals(title(HealthItem.OVERLAY))
        assertThat(finished).isEqualTo(0)
    }

    @Test
    fun `coming back without granting offers to continue without it`() {
        show()

        composeRule.onNodeWithTag(OnboardingStepTestTags.PRIMARY).performClick()
        returnFromSettings()

        composeRule.onNodeWithTag(OnboardingStepTestTags.TITLE).assertTextEquals(title(HealthItem.NOTIFICATIONS))
        composeRule.onNodeWithTag(OnboardingStepTestTags.WARNING).assertIsDisplayed()
        composeRule.onNodeWithTag(OnboardingStepTestTags.SECONDARY)
            .assertTextEquals(composeRule.activity.getString(R.string.onboarding_continue_without))
    }

    @Test
    fun `after process death the step is restored from statuses and saved attempts`() {
        // Отзыв уведомлений в настройках убил процесс: в SavedStateHandle осталась попытка.
        show(SavedStateHandle(mapOf("attempted" to arrayListOf(OnboardingStep.NOTIFICATIONS.name))))

        composeRule.onNodeWithTag(OnboardingStepTestTags.TITLE).assertTextEquals(title(HealthItem.NOTIFICATIONS))
        composeRule.onNodeWithTag(OnboardingStepTestTags.WARNING).assertIsDisplayed()
    }

    @Test
    fun `confirming the last step finishes once`() {
        checker.current = HEALTHY_SNAPSHOT
        show()

        composeRule.onNodeWithTag(OnboardingStepTestTags.TITLE).assertTextEquals(title(HealthItem.OEM_BACKGROUND))
        composeRule.onNodeWithTag(OnboardingTestTags.OEM_CONFIRM).performScrollTo().performClick()
        composeRule.onNodeWithTag(OnboardingStepTestTags.PRIMARY).performClick()
        composeRule.waitForIdle()

        assertThat(finished).isEqualTo(1)
        assertThat(runBlocking { setup.state.first() })
            .isEqualTo(SetupState(onboardingCompleted = true, oemBackgroundConfirmed = true))
        composeRule.onNodeWithTag(OnboardingTestTags.BLANK).assertIsDisplayed()

        returnFromSettings()
        assertThat(finished).isEqualTo(1)
    }

    @Test
    fun `nothing to do finishes right away without showing a step`() {
        checker.current = HEALTHY_SNAPSHOT
        setup.setOemBackgroundConfirmedBlocking()
        show()
        composeRule.waitForIdle()

        assertThat(finished).isEqualTo(1)
        composeRule.onNodeWithTag(OnboardingStepTestTags.LAYOUT).assertDoesNotExist()
    }

    @Test
    fun `save failure shows a toast and still finishes`() {
        checker.current = HEALTHY_SNAPSHOT
        setup.setOemBackgroundConfirmedBlocking()
        setup.failOnWrite = IOException("disk full")
        show()
        composeRule.waitForIdle()

        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo(composeRule.activity.getString(R.string.onboarding_save_failed))
        assertThat(finished).isEqualTo(1)
    }

    private fun FakeSetupStateRepository.setOemBackgroundConfirmedBlocking() = runBlocking {
        setOemBackgroundConfirmed(true)
    }
}
