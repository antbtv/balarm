package com.antbtv.balarm.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.MainActivity
import com.antbtv.balarm.core.designsystem.component.NavigationBarTestTags
import com.antbtv.balarm.core.designsystem.component.OnboardingStepTestTags
import com.antbtv.balarm.core.designsystem.component.TopBarTestTags
import com.antbtv.balarm.core.domain.health.SetupState
import com.antbtv.balarm.core.domain.testing.FakePermissionHealthChecker
import com.antbtv.balarm.core.domain.testing.HEALTHY_SNAPSHOT
import com.antbtv.balarm.di.TestSetupStateRepository
import com.antbtv.balarm.feature.alarmedit.AlarmEditTestTags
import com.antbtv.balarm.feature.alarmlist.AlarmListTestTags
import com.antbtv.balarm.feature.settings.AboutTestTags
import com.antbtv.balarm.feature.settings.HealthTestTags
import com.antbtv.balarm.feature.settings.SettingsTestTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Вкладки, нижняя панель и стартовый экран в настоящем `MainActivity` (ADR-013 §2, ADR-014). Activity
 * запускается в самом тесте — после того как заданы состояние онбординга и статусы разрешений.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = "en-rUS-w360dp-h640dp")
@RunWith(AndroidJUnit4::class)
class TabNavigationTest {

    private val hilt = HiltAndroidRule(this)
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(composeRule)

    @Inject lateinit var setup: TestSetupStateRepository

    @Inject lateinit var checker: FakePermissionHealthChecker

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var activity: MainActivity

    @Before
    fun setUp() = hilt.inject()

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java).also { it.onActivity { a -> activity = a } }
        composeRule.waitForIdle()
    }

    private fun recreate() {
        scenario!!.recreate().onActivity { activity = it }
        composeRule.waitForIdle()
    }

    /** Системный Back — через диспетчер Activity, как жест/кнопка на устройстве. */
    private fun back() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun tag(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun bar() = tag(NavigationBarTestTags.BAR)

    private fun tab(tab: Tab) = tag(NavigationBarTestTags.item(tab.ordinal))

    private fun click(tag: String) {
        tag(tag).performClick()
        composeRule.waitForIdle()
    }

    private fun openSettings() = click(NavigationBarTestTags.item(Tab.SETTINGS.ordinal))

    @Test
    fun `the bar is shown on the alarm list with the alarms tab selected`() {
        launch()

        tag(AlarmListTestTags.ROOT).assertIsDisplayed()
        bar().assertIsDisplayed()
        tab(Tab.ALARMS).assertIsSelected()
    }

    @Test
    fun `the settings tab shows settings with the bar, back returns to the list, back again closes the app`() {
        launch()

        openSettings()
        tag(SettingsTestTags.ROOT).assertIsDisplayed()
        tag(AlarmListTestTags.ROOT).assertDoesNotExist()
        bar().assertIsDisplayed()
        tab(Tab.SETTINGS).assertIsSelected()

        back()
        tag(AlarmListTestTags.ROOT).assertIsDisplayed()
        tag(SettingsTestTags.ROOT).assertDoesNotExist()
        tab(Tab.ALARMS).assertIsSelected()
        assertThat(activity.isFinishing).isFalse()

        back()
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `switching tabs back and forth returns to each tab root`() {
        launch()

        openSettings()
        click(NavigationBarTestTags.item(Tab.ALARMS.ordinal))
        tag(AlarmListTestTags.ROOT).assertIsDisplayed()
        tag(SettingsTestTags.ROOT).assertDoesNotExist()

        // Повторный тап по текущей вкладке на её корне ничего не ломает.
        click(NavigationBarTestTags.item(Tab.ALARMS.ordinal))
        tag(AlarmListTestTags.ROOT).assertIsDisplayed()
        back()
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `health from settings hides the bar and back returns to settings`() {
        launch()
        openSettings()

        click(SettingsTestTags.HEALTH_ROW)

        tag(HealthTestTags.ROOT).assertIsDisplayed()
        bar().assertDoesNotExist()
        back()
        tag(SettingsTestTags.ROOT).assertIsDisplayed()
        tab(Tab.SETTINGS).assertIsSelected()
    }

    @Test
    fun `about from settings hides the bar and its top bar back returns to settings`() {
        launch()
        openSettings()

        click(SettingsTestTags.ABOUT_ROW)

        tag(AboutTestTags.ROOT).assertIsDisplayed()
        bar().assertDoesNotExist()
        click(TopBarTestTags.BACK)
        tag(SettingsTestTags.ROOT).assertIsDisplayed()
        bar().assertIsDisplayed()
    }

    @Test
    fun `the editor hides the bar`() {
        launch()

        click(AlarmListTestTags.FAB)

        tag(AlarmEditTestTags.ROOT).assertIsDisplayed()
        bar().assertDoesNotExist()
        back()
        bar().assertIsDisplayed()
    }

    @Test
    fun `the health banner opens health over the list and back returns to the list`() {
        checker.current = HEALTHY_SNAPSHOT.copy(notificationsEnabled = false)
        launch()

        click(AlarmListTestTags.HEALTH_BANNER)

        tag(HealthTestTags.ROOT).assertIsDisplayed()
        bar().assertDoesNotExist()
        back()
        tag(AlarmListTestTags.ROOT).assertIsDisplayed()
        tab(Tab.ALARMS).assertIsSelected()
        back()
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `the settings tab and its stack survive recreation`() {
        launch()
        openSettings()
        click(SettingsTestTags.HEALTH_ROW)

        recreate()

        tag(HealthTestTags.ROOT).assertIsDisplayed()
        back()
        tag(SettingsTestTags.ROOT).assertIsDisplayed()
        tab(Tab.SETTINGS).assertIsSelected()
        back()
        tag(AlarmListTestTags.ROOT).assertIsDisplayed()
    }

    @Test
    fun `the alarms stack survives recreation while on the alarms tab`() {
        launch()
        click(AlarmListTestTags.FAB)

        recreate()

        tag(AlarmEditTestTags.ROOT).assertIsDisplayed()
        bar().assertDoesNotExist()
        back()
        tag(AlarmListTestTags.ROOT).assertIsDisplayed()
    }

    @Test
    fun `onboarding is the start screen without the bar until it is completed`() {
        setup.value = SetupState(onboardingCompleted = false, oemBackgroundConfirmed = false)
        launch()

        tag(OnboardingStepTestTags.LAYOUT).assertIsDisplayed()
        bar().assertDoesNotExist()
        tag(AlarmListTestTags.ROOT).assertDoesNotExist()
    }

    @Test
    fun `onboarding survives recreation even when the flag changes meanwhile`() {
        setup.value = SetupState(onboardingCompleted = false, oemBackgroundConfirmed = false)
        launch()

        // Поворот: экран не перескакивает, хотя флаг уже «пройден». Смерть процесса (флаг перечитывается) —
        // NavStateRestorationTest.
        setup.value = setup.value.copy(onboardingCompleted = true)
        recreate()

        tag(OnboardingStepTestTags.LAYOUT).assertIsDisplayed()
        bar().assertDoesNotExist()
    }

    @Test
    fun `finished onboarding is replaced by the alarm list and back does not return to it`() {
        // Все пункты в порядке и OEM подтверждён — шагов нет, онбординг сразу завершается.
        setup.value = SetupState(onboardingCompleted = false, oemBackgroundConfirmed = true)
        launch()

        tag(AlarmListTestTags.ROOT).assertIsDisplayed()
        bar().assertIsDisplayed()
        assertThat(setup.value.onboardingCompleted).isTrue()

        back()
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `completed onboarding starts on the alarm list`() {
        launch()

        tag(AlarmListTestTags.ROOT).assertIsDisplayed()
        tag(OnboardingStepTestTags.LAYOUT).assertDoesNotExist()
    }

    /** ADR-014 §4: нижний отступ корня — от панели; FAB и нижняя подложка списка над ней, без зазора и наложения. */
    @Test
    fun `fab and bottom scrim of the list sit above the bar at font scale 2`() {
        RuntimeEnvironment.setFontScale(2f)
        launch()

        val barTop = bar().getUnclippedBoundsInRoot().top
        val listBottom = tag(AlarmListTestTags.ROOT).getUnclippedBoundsInRoot().bottom
        val fabBottom = tag(AlarmListTestTags.FAB).getUnclippedBoundsInRoot().bottom
        val scrimBottom = tag(AlarmListTestTags.BOTTOM_SCRIM).getUnclippedBoundsInRoot().bottom

        assertThat(listBottom).isEqualTo(barTop)
        assertThat(scrimBottom).isEqualTo(barTop)
        assertThat(fabBottom).isLessThan(barTop)
        assertThat(barTop).isGreaterThan(Dp(0f))
    }

    /** То же для настроек: контент заканчивается у верхнего края панели. */
    @Test
    fun `settings content ends at the bar`() {
        launch()
        openSettings()

        val barTop = bar().getUnclippedBoundsInRoot().top
        assertThat(tag(SettingsTestTags.ROOT).getUnclippedBoundsInRoot().bottom).isEqualTo(barTop)
    }
}
