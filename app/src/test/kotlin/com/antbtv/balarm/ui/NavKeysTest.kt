package com.antbtv.balarm.ui

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.SoundRef
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementDescriptors
import org.junit.Test

/**
 * Ключи и логика стеков без Compose: регистрация подтипов для сохранения, защита от двойного открытия,
 * вкладки и онбординг [BalarmNavState] (ADR-013 §2, ADR-014 §2).
 */
@OptIn(ExperimentalSerializationApi::class)
class NavKeysTest {

    @Test
    fun `every key subtype is registered for saving the back stack`() {
        // У sealed-сериализатора элемент 1 («value») перечисляет все подтипы; их имена — те, что нужны в модуле.
        val subtypes = BalarmKey.serializer().descriptor.getElementDescriptor(1).elementDescriptors
            .map { it.serialName }

        assertWithMessage("BalarmKey has no subtypes").that(subtypes).isNotEmpty()
        subtypes.forEach { name ->
            assertWithMessage("$name is not registered in NavConfiguration")
                .that(NavConfiguration.serializersModule.getPolymorphic(NavKey::class, name))
                .isNotNull()
        }
    }

    @Test
    fun `opening the editor twice in a row adds one entry`() {
        val backStack = NavBackStack<NavKey>(AlarmListKey)

        backStack.openEditor(alarmId = null)
        backStack.openEditor(alarmId = null)
        backStack.openEditor(alarmId = 5)

        assertThat(backStack.toList()).containsExactly(AlarmListKey, AlarmEditKey(alarmId = null)).inOrder()
    }

    @Test
    fun `the editor can be opened again after returning to the list`() {
        val backStack = NavBackStack<NavKey>(AlarmListKey)
        backStack.openEditor(alarmId = null)
        backStack.removeLastOrNull()

        backStack.openEditor(alarmId = 7)

        assertThat(backStack.toList()).containsExactly(AlarmListKey, AlarmEditKey(alarmId = 7)).inOrder()
    }

    @Test
    fun `every key kind is a registered subtype`() {
        val keys = listOf(
            AlarmListKey,
            SettingsKey,
            AlarmEditKey(1),
            HealthKey,
            AboutKey,
            OnboardingKey,
            SoundPickerKey(SoundRef.DEFAULT.encode()),
            SoundLibraryKey,
        )

        keys.forEach { key ->
            assertWithMessage(key.toString())
                .that(NavConfiguration.serializersModule.getPolymorphic(NavKey::class, key))
                .isNotNull()
        }
    }

    @Test
    fun `a screen opens only from the screen on top, once`() {
        val backStack = NavBackStack<NavKey>(SettingsKey)

        backStack.openFrom(SettingsKey, HealthKey)
        backStack.openFrom(SettingsKey, HealthKey)
        backStack.openFrom(SettingsKey, AboutKey)

        assertThat(backStack.toList()).containsExactly(SettingsKey, HealthKey).inOrder()
    }

    @Test
    fun `close removes only the screen on top`() {
        val backStack = NavBackStack<NavKey>(AlarmListKey, HealthKey)

        backStack.closeTop(HealthKey)
        backStack.closeTop(HealthKey)

        assertThat(backStack.toList()).containsExactly(AlarmListKey)
    }

    @Test
    fun `alarms tab shows only its stack, settings tab shows it on top of the alarms stack`() {
        val nav = navState()
        assertThat(nav.visibleStacks).containsExactly(StackId.ALARMS)
        assertThat(nav.atTabRoot).isTrue()

        nav.selectTab(Tab.SETTINGS)

        assertThat(nav.currentTab).isEqualTo(Tab.SETTINGS)
        assertThat(nav.visibleStacks).containsExactly(StackId.ALARMS, StackId.SETTINGS).inOrder()
    }

    @Test
    fun `switching tabs keeps both stacks`() {
        val nav = navState()
        nav.alarms.openEditor(alarmId = 3)
        nav.selectTab(Tab.SETTINGS)
        nav.settings.openFrom(SettingsKey, AboutKey)

        nav.selectTab(Tab.ALARMS)
        nav.selectTab(Tab.SETTINGS)

        assertThat(nav.alarms.toList()).containsExactly(AlarmListKey, AlarmEditKey(3)).inOrder()
        assertThat(nav.settings.toList()).containsExactly(SettingsKey, AboutKey).inOrder()
        assertThat(nav.atTabRoot).isFalse()
    }

    @Test
    fun `back pops the current stack, then leaves settings for the alarms tab`() {
        val nav = navState()
        nav.selectTab(Tab.SETTINGS)
        nav.settings.openFrom(SettingsKey, HealthKey)

        nav.back()
        assertThat(nav.settings.toList()).containsExactly(SettingsKey)
        assertThat(nav.currentTab).isEqualTo(Tab.SETTINGS)

        nav.back()
        assertThat(nav.currentTab).isEqualTo(Tab.ALARMS)
        assertThat(nav.settings.toList()).containsExactly(SettingsKey)
        assertThat(nav.alarms.toList()).containsExactly(AlarmListKey)
    }

    @Test
    fun `tapping the current tab pops its stack to the root, the other stack stays`() {
        val nav = navState()
        nav.alarms.openFrom(AlarmListKey, HealthKey)
        nav.selectTab(Tab.SETTINGS)
        nav.settings.openFrom(SettingsKey, AboutKey)

        nav.selectTab(Tab.SETTINGS)

        assertThat(nav.settings.toList()).containsExactly(SettingsKey)
        assertThat(nav.alarms.toList()).containsExactly(AlarmListKey, HealthKey).inOrder()
        assertThat(nav.currentTab).isEqualTo(Tab.SETTINGS)
    }

    @Test
    fun `health from the banner stays in the alarms stack without switching tabs`() {
        val nav = navState()

        nav.alarms.openFrom(AlarmListKey, HealthKey)

        assertThat(nav.currentTab).isEqualTo(Tab.ALARMS)
        assertThat(nav.visibleStacks).containsExactly(StackId.ALARMS)
        assertThat(nav.atTabRoot).isFalse()
        nav.back()
        assertThat(nav.alarms.toList()).containsExactly(AlarmListKey)
    }

    @Test
    fun `onboarding hides the tabs and its finish replaces the stack with the tab roots`() {
        val nav = navState(onboarding = true)
        assertThat(nav.visibleStacks).containsExactly(StackId.ONBOARDING)
        assertThat(nav.atTabRoot).isFalse()
        nav.selectTab(Tab.SETTINGS)
        assertThat(nav.currentTab).isEqualTo(Tab.ALARMS)

        nav.finishOnboarding()

        assertThat(nav.inOnboarding).isFalse()
        assertThat(nav.onboarding.toList()).isEmpty()
        assertThat(nav.visibleStacks).containsExactly(StackId.ALARMS)
        assertThat(nav.alarms.toList()).containsExactly(AlarmListKey)
        assertThat(nav.atTabRoot).isTrue()
    }

    @Test
    fun `the sound picker opens only from the editor on top, once, with the current sound`() {
        val nav = navState()
        nav.openSoundPicker(StackId.ALARMS, BELLS)
        assertThat(nav.alarms.toList()).containsExactly(AlarmListKey)

        nav.alarms.openEditor(alarmId = 3)
        nav.openSoundPicker(StackId.ALARMS, BELLS)
        nav.openSoundPicker(StackId.ALARMS, SoundRef.DEFAULT)

        assertThat(nav.alarms.toList())
            .containsExactly(AlarmListKey, AlarmEditKey(3), SoundPickerKey("builtin:snd_bells"))
            .inOrder()
    }

    @Test
    fun `a pick is kept until the editor consumes it`() {
        val nav = navState()
        assertThat(nav.pickedSound).isNull()

        nav.pickSound(BELLS)
        assertThat(nav.pickedSound).isEqualTo(BELLS)

        nav.consumeSoundPick()
        assertThat(nav.pickedSound).isNull()
    }

    @Test
    fun `opening the picker drops an unconsumed old pick`() {
        val nav = navState()
        nav.alarms.openEditor(alarmId = null)
        nav.pickSound(BELLS)

        nav.openSoundPicker(StackId.ALARMS, SoundRef.DEFAULT)

        assertThat(nav.pickedSound).isNull()
    }

    @Test
    fun `my ringtones open from the picker and from settings in their own stacks`() {
        val nav = navState()
        nav.alarms.openEditor(alarmId = null)
        nav.openSoundPicker(StackId.ALARMS, SoundRef.DEFAULT)
        val picker = nav.alarms.last()
        nav.alarms.openFrom(picker, SoundLibraryKey)
        nav.alarms.openFrom(picker, SoundLibraryKey)
        nav.selectTab(Tab.SETTINGS)
        nav.settings.openFrom(SettingsKey, SoundLibraryKey)

        assertThat(nav.alarms.toList()).containsExactly(AlarmListKey, AlarmEditKey(null), picker, SoundLibraryKey)
            .inOrder()
        assertThat(nav.settings.toList()).containsExactly(SettingsKey, SoundLibraryKey).inOrder()
        assertThat(nav.atTabRoot).isFalse()
    }

    private fun navState(onboarding: Boolean = false) = BalarmNavState(
        currentTab = mutableStateOf(Tab.ALARMS),
        onboarding = if (onboarding) NavBackStack(OnboardingKey) else NavBackStack(),
        alarms = NavBackStack(AlarmListKey),
        settings = NavBackStack(SettingsKey),
    )

    private companion object {
        val BELLS: SoundRef = SoundRef.Builtin(BuiltinSound.BELLS)
    }
}
