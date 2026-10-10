package com.antbtv.balarm.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.MainActivity
import com.antbtv.balarm.core.designsystem.component.ConfirmDialogTestTags
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.MissionSpec
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.antbtv.balarm.di.FeatureFlagsModule
import com.antbtv.balarm.feature.alarmedit.AlarmEditTestTags
import com.antbtv.balarm.feature.alarmlist.AlarmListTestTags
import com.antbtv.balarm.feature.missions.api.MissionPreviewTestTags
import com.antbtv.balarm.feature.missions.math.MathMission
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Миссия в навигации (M5-T05, FR-MIS-5): строка «Миссия» → подэкран редактора → «Превью» (отдельная запись стека
 * поверх редактора) → Back/«Закрыть» → снова подэкран с тем же черновиком → «Сохранить». Флаг Math включён подменой
 * провайдера (в конфиге он выключен до M5-T-docs).
 */
@HiltAndroidTest
@UninstallModules(FeatureFlagsModule::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-rUS-w360dp-h640dp")
@RunWith(AndroidJUnit4::class)
class MissionNavigationTest {

    private val hilt = HiltAndroidRule(this)
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(composeRule)

    private var missionFlag = true

    @BindValue
    @JvmField
    val flags: FeatureFlagProvider = FeatureFlagProvider { feature ->
        if (feature == Feature.MISSION_MATH) missionFlag else feature.defaultEnabled
    }

    @Inject lateinit var repository: FakeAlarmRepository

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hilt.inject()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
    }

    private fun back() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun tag(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun click(tag: String) {
        tag(tag).performClick()
        composeRule.waitForIdle()
    }

    private fun stored(mission: MissionSpec? = null): Long = runBlocking {
        repository.save(Alarm(time = LocalTime.of(7, 30), mission = mission)).value
    }

    private fun savedMission(id: Long): MissionSpec? = runBlocking { repository.loadAll() }
        .firstOrNull { it.alarm.id.value == id }?.alarm?.mission

    private fun openEditor(id: Long) {
        click(AlarmListTestTags.card(AlarmId(id)))
        tag(AlarmEditTestTags.ROOT).assertIsDisplayed()
    }

    private fun openMissionSettings() {
        tag(AlarmEditTestTags.MISSION).performScrollTo().performClick()
        composeRule.waitForIdle()
        tag(AlarmEditTestTags.MISSION_SETTINGS).assertIsDisplayed()
    }

    private fun openPreview() {
        tag(AlarmEditTestTags.MISSION_PREVIEW).performScrollTo().performClick()
        composeRule.waitForIdle()
        tag(MissionPreviewTestTags.ROOT).assertIsDisplayed()
        tag(AlarmEditTestTags.ROOT).assertDoesNotExist()
    }

    private val mathOption = AlarmEditTestTags.missionOption(MissionTypeId.MATH.value)
    private val defaultMath = MissionSpec(MissionTypeId.MATH, MathMission.defaultConfig())

    @Test
    fun `choose math, preview, back and close return to the settings, save stores the mission`() {
        val id = stored()
        launch()
        openEditor(id)
        tag(AlarmEditTestTags.MISSION).performScrollTo().assertContentDescriptionEquals("Mission, None")

        openMissionSettings()
        click(mathOption)

        openPreview()
        back()
        tag(MissionPreviewTestTags.ROOT).assertDoesNotExist()
        tag(AlarmEditTestTags.MISSION_SETTINGS).assertIsDisplayed()

        openPreview()
        click(MissionPreviewTestTags.CLOSE)
        tag(AlarmEditTestTags.MISSION_SETTINGS).assertIsDisplayed()

        click(AlarmEditTestTags.MISSION_DONE)
        tag(AlarmEditTestTags.MISSION)
            .performScrollTo()
            .assertContentDescriptionEquals("Mission, Level 2, 3 problems")
        click(AlarmEditTestTags.SAVE)

        tag(AlarmListTestTags.ROOT).assertIsDisplayed()
        assertThat(savedMission(id)).isEqualTo(defaultMath)
    }

    @Test
    fun `the preview survives recreation of the activity and the draft is kept`() {
        val id = stored()
        launch()
        openEditor(id)
        openMissionSettings()
        click(mathOption)
        openPreview()

        scenario!!.recreate()
        composeRule.waitForIdle()
        tag(MissionPreviewTestTags.ROOT).assertIsDisplayed()

        back()
        tag(AlarmEditTestTags.MISSION_SETTINGS).assertIsDisplayed()
        back()
        // Черновик с миссией цел: Back с правками спрашивает «Отменить изменения?».
        back()
        tag(ConfirmDialogTestTags.DIALOG).assertIsDisplayed()
    }

    @Test
    fun `with the flag off there is no mission row and the stored mission survives saving`() {
        missionFlag = false
        val spec = MissionSpec(MissionTypeId.MATH, """{"level":4,"count":5}""")
        val id = stored(mission = spec)
        launch()
        openEditor(id)

        tag(AlarmEditTestTags.MISSION).assertDoesNotExist()
        click(AlarmEditTestTags.SAVE)

        tag(AlarmListTestTags.ROOT).assertIsDisplayed()
        assertThat(savedMission(id)).isEqualTo(spec)
    }
}
