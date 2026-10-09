package com.antbtv.balarm.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.SoundRef
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Смерть процесса: состояние навигации сохраняется в Bundle и восстанавливается из него (все ключи
 * сериализуются через `NavConfiguration`). Сохранённое важнее стартового флага (ADR-013 §2).
 */
@Config(application = Application::class)
@RunWith(AndroidJUnit4::class)
class NavStateRestorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val restoration = StateRestorationTester(composeRule)

    private var showOnboarding by mutableStateOf(false)
    private lateinit var nav: BalarmNavState

    private fun setContent() = restoration.setContent { nav = rememberBalarmNavState(showOnboarding) }

    private fun restore() {
        val before = nav
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        assertThat(nav).isNotSameInstanceAs(before)
    }

    @Test
    fun `current tab and both stacks are restored`() {
        setContent()
        composeRule.runOnIdle {
            nav.alarms.openEditor(alarmId = 42)
            nav.alarms.removeAt(nav.alarms.lastIndex)
            nav.alarms.openFrom(AlarmListKey, HealthKey)
            nav.selectTab(Tab.SETTINGS)
            nav.settings.openFrom(SettingsKey, AboutKey)
        }

        restore()

        assertThat(nav.currentTab).isEqualTo(Tab.SETTINGS)
        assertThat(nav.alarms.toList()).containsExactly(AlarmListKey, HealthKey).inOrder()
        assertThat(nav.settings.toList()).containsExactly(SettingsKey, AboutKey).inOrder()
        assertThat(nav.inOnboarding).isFalse()
    }

    @Test
    fun `the editor key with its id is restored`() {
        setContent()
        composeRule.runOnIdle { nav.alarms.openEditor(alarmId = 42) }

        restore()

        assertThat(nav.alarms.toList()).containsExactly(AlarmListKey, AlarmEditKey(42)).inOrder()
    }

    @Test
    fun `the sound picker and my ringtones over it are restored`() {
        val bells = SoundRef.Builtin(BuiltinSound.BELLS)
        setContent()
        composeRule.runOnIdle {
            nav.alarms.openEditor(alarmId = 42)
            nav.openSoundPicker(StackId.ALARMS, bells)
            nav.alarms.openFrom(nav.alarms.last(), SoundLibraryKey)
        }

        restore()

        assertThat(nav.alarms.toList())
            .containsExactly(AlarmListKey, AlarmEditKey(42), SoundPickerKey(bells.encode()), SoundLibraryKey)
            .inOrder()
    }

    @Test
    fun `an unconsumed pick survives like the stack, a consumed one does not come back`() {
        val bells = SoundRef.Builtin(BuiltinSound.BELLS)
        setContent()
        composeRule.runOnIdle { nav.pickSound(bells) }

        restore()
        assertThat(nav.pickedSound).isEqualTo(bells)

        composeRule.runOnIdle { nav.consumeSoundPick() }
        restore()
        assertThat(nav.pickedSound).isNull()
    }

    @Test
    fun `onboarding in progress is restored even if the flag now says completed`() {
        showOnboarding = true
        setContent()

        showOnboarding = false
        restore()

        assertThat(nav.inOnboarding).isTrue()
        assertThat(nav.onboarding.toList()).containsExactly(OnboardingKey)
    }

    @Test
    fun `finished onboarding stays finished even if the flag was not written`() {
        showOnboarding = true
        setContent()
        composeRule.runOnIdle { nav.finishOnboarding() }

        // Запись флага не удалась (ADR-013: онбординг покажется при следующем холодном старте), но стек — вкладки.
        restore()

        assertThat(nav.inOnboarding).isFalse()
        assertThat(nav.alarms.toList()).containsExactly(AlarmListKey)
    }
}
