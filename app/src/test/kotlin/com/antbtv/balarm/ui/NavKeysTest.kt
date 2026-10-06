package com.antbtv.balarm.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementDescriptors
import org.junit.Test

/** Ключи и стек: регистрация подтипов для сохранения и защита от двойного открытия редактора. */
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
}
