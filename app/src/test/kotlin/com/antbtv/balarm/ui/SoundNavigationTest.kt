package com.antbtv.balarm.ui

import android.os.Bundle
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.MainActivity
import com.antbtv.balarm.core.designsystem.component.ConfirmDialogTestTags
import com.antbtv.balarm.core.designsystem.component.NavigationBarTestTags
import com.antbtv.balarm.core.designsystem.component.TopBarTestTags
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.antbtv.balarm.di.FeatureFlagsModule
import com.antbtv.balarm.feature.alarmedit.AlarmEditTestTags
import com.antbtv.balarm.feature.alarmlist.AlarmListTestTags
import com.antbtv.balarm.feature.settings.SettingsTestTags
import com.antbtv.balarm.feature.sounds.SoundsTestTags
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
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Мелодии в навигации (M4-T11, ADR-009 §3, ADR-016 §8): редактор → пикер → выбор → редактор через результат
 * [BalarmNavState.pickedSound]; Back из пикера без выбора; «Мои мелодии» из пикера и из настроек.
 * Флаги звука включены подменой провайдера (в конфиге они выключены до M4-T-docs).
 */
@HiltAndroidTest
@UninstallModules(FeatureFlagsModule::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-rUS-w360dp-h640dp")
@RunWith(AndroidJUnit4::class)
class SoundNavigationTest {

    private val hilt = HiltAndroidRule(this)
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(composeRule)

    @BindValue
    @JvmField
    val flags: FeatureFlagProvider = FeatureFlagProvider { feature ->
        feature == Feature.ALARM_SOUND || feature == Feature.CUSTOM_SOUNDS || feature.defaultEnabled
    }

    @Inject lateinit var repository: FakeAlarmRepository

    private var scenario: ActivityScenario<MainActivity>? = null
    private var controller: ActivityController<MainActivity>? = null

    @Before
    fun setUp() {
        hilt.inject()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        scenario?.close()
        controller?.pause()?.stop()?.destroy()
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

    private fun stored(sound: SoundRef = SoundRef.DEFAULT): Long = runBlocking {
        repository.save(Alarm(time = LocalTime.of(7, 30), sound = SoundSettings(sound = sound))).value
    }

    private fun savedSound(id: Long): SoundRef? = runBlocking { repository.loadAll() }
        .firstOrNull { it.alarm.id.value == id }?.alarm?.sound?.sound

    private fun openEditor(id: Long) {
        click(AlarmListTestTags.card(AlarmId(id)))
        tag(AlarmEditTestTags.ROOT).assertIsDisplayed()
    }

    private fun openPicker() {
        tag(AlarmEditTestTags.SOUND).performScrollTo().performClick()
        composeRule.waitForIdle()
        tag(SoundsTestTags.PICKER).assertIsDisplayed()
        tag(AlarmEditTestTags.ROOT).assertDoesNotExist()
        tag(NavigationBarTestTags.BAR).assertDoesNotExist()
    }

    private fun selectInPicker(sound: BuiltinSound) {
        val row = SoundsTestTags.builtin(sound)
        tag(SoundsTestTags.LIST).performScrollToNode(hasTestTag(row))
        click(row)
    }

    /** Строка «Мелодия» редактора: значение — в описании TalkBack («Ringtone, Bells»). */
    private fun editorSound(): SemanticsNodeInteraction = tag(AlarmEditTestTags.SOUND).performScrollTo()

    @Test
    fun `editor - picker - pick returns to the editor with the picked sound, save stores it`() {
        val id = stored()
        launch()
        openEditor(id)
        editorSound().assertContentDescriptionContains("Classic", substring = true)

        openPicker()
        selectInPicker(BuiltinSound.BELLS)
        click(SoundsTestTags.CONFIRM)

        tag(SoundsTestTags.PICKER).assertDoesNotExist()
        tag(AlarmEditTestTags.ROOT).assertIsDisplayed()
        editorSound().assertContentDescriptionContains("Bells", substring = true)

        click(AlarmEditTestTags.SAVE)
        tag(AlarmListTestTags.ROOT).assertIsDisplayed()
        assertThat(savedSound(id)).isEqualTo(SoundRef.Builtin(BuiltinSound.BELLS))
    }

    @Test
    fun `back from the picker without confirming keeps the sound and the editor clean`() {
        val id = stored(sound = SoundRef.Builtin(BuiltinSound.CHIMES))
        launch()
        openEditor(id)

        openPicker()
        selectInPicker(BuiltinSound.SIREN)
        back()

        tag(AlarmEditTestTags.ROOT).assertIsDisplayed()
        editorSound().assertContentDescriptionContains("Chimes", substring = true)
        // Черновик не изменился: Back закрывает редактор без «Отменить изменения?».
        back()
        tag(ConfirmDialogTestTags.DIALOG).assertDoesNotExist()
        tag(AlarmListTestTags.ROOT).assertIsDisplayed()
        assertThat(savedSound(id)).isEqualTo(SoundRef.Builtin(BuiltinSound.CHIMES))
    }

    @Test
    fun `the top bar back of the picker returns to the editor without a pick`() {
        val id = stored()
        launch()
        openEditor(id)
        openPicker()
        selectInPicker(BuiltinSound.PIANO)

        click(TopBarTestTags.BACK)

        tag(AlarmEditTestTags.ROOT).assertIsDisplayed()
        editorSound().assertContentDescriptionContains("Classic", substring = true)
    }

    @Test
    fun `the picked sound survives recreation of the activity`() {
        val id = stored()
        launch()
        openEditor(id)
        openPicker()
        selectInPicker(BuiltinSound.BELLS)
        click(SoundsTestTags.CONFIRM)

        scenario!!.recreate()
        composeRule.waitForIdle()

        tag(AlarmEditTestTags.ROOT).assertIsDisplayed()
        editorSound().assertContentDescriptionContains("Bells", substring = true)
    }

    /**
     * Повторного применения нет: результат очищен, как только редактор его применил. Смерть процесса теряет
     * черновик (ADR-009, Consequences) — редактор снова читает будильник из БД («Classic»); неочищенный
     * результат из сохранённого состояния подменил бы его на «Bells».
     */
    @Test
    fun `a consumed pick is not applied again after the process is restored`() {
        val id = stored()
        val first = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller = first
        composeRule.waitForIdle()
        openEditor(id)
        openPicker()
        selectInPicker(BuiltinSound.BELLS)
        click(SoundsTestTags.CONFIRM)
        editorSound().assertContentDescriptionContains("Bells", substring = true)

        val saved = Bundle()
        first.pause().saveInstanceState(saved).stop().destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup(saved)
        composeRule.waitForIdle()

        tag(AlarmEditTestTags.ROOT).assertIsDisplayed()
        editorSound().assertContentDescriptionContains("Classic", substring = true)
    }

    @Test
    fun `the picker is restored with the selected sound after the process is restored`() {
        val id = stored(sound = SoundRef.Builtin(BuiltinSound.MARIMBA))
        val first = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller = first
        composeRule.waitForIdle()
        openEditor(id)
        openPicker()

        val saved = Bundle()
        first.pause().saveInstanceState(saved).stop().destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup(saved)
        composeRule.waitForIdle()

        tag(SoundsTestTags.PICKER).assertIsDisplayed()
        click(SoundsTestTags.CONFIRM)
        tag(AlarmEditTestTags.ROOT).assertIsDisplayed()
        editorSound().assertContentDescriptionContains("Marimba", substring = true)
    }

    @Test
    fun `my ringtones open from the picker and back returns to the picker`() {
        val id = stored()
        launch()
        openEditor(id)
        openPicker()

        tag(SoundsTestTags.LIST).performScrollToNode(hasTestTag(SoundsTestTags.MANAGE))
        click(SoundsTestTags.MANAGE)
        tag(SoundsTestTags.LIBRARY).assertIsDisplayed()
        tag(NavigationBarTestTags.BAR).assertDoesNotExist()

        back()
        tag(SoundsTestTags.PICKER).assertIsDisplayed()
    }

    @Test
    fun `ringtones in settings open my ringtones without the bar, back returns to settings`() {
        launch()
        click(NavigationBarTestTags.item(Tab.SETTINGS.ordinal))

        click(SettingsTestTags.SOUNDS_ROW)
        tag(SoundsTestTags.LIBRARY).assertIsDisplayed()
        tag(NavigationBarTestTags.BAR).assertDoesNotExist()

        click(TopBarTestTags.BACK)
        tag(SettingsTestTags.ROOT).assertIsDisplayed()
        tag(NavigationBarTestTags.BAR).assertIsDisplayed()
    }
}
