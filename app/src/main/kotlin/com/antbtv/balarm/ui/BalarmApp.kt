package com.antbtv.balarm.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.antbtv.balarm.feature.alarmlist.AlarmListRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Подтипы [NavKey] для сохранения стека (поворот, смерть процесса): без регистрации `rememberNavBackStack` падает. */
internal val NavConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(AlarmListKey::class)
            subclass(AlarmEditKey::class)
        }
    }
}

/**
 * Корневой composable приложения: стек экранов (ADR-009). Список — стартовый экран; экраны сами обрабатывают
 * системные отступы, поэтому `Scaffold` и `padding(innerPadding)` здесь не нужны (двойные отступы).
 */
@Composable
fun BalarmApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(NavConfiguration, AlarmListKey)
    BalarmNavDisplay(backStack = backStack, modifier = modifier)
}

@Composable
private fun BalarmNavDisplay(backStack: NavBackStack<NavKey>, modifier: Modifier = Modifier) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<AlarmListKey> {
                AlarmListRoute(
                    onAddAlarm = { backStack.openEditor(alarmId = null) },
                    onOpenAlarm = { backStack.openEditor(alarmId = it.value) },
                )
            }
            entry<AlarmEditKey> { key ->
                AlarmEditStub(alarmId = key.alarmId)
            }
        },
    )
}

/** Двойной тап по FAB или карточке не должен класть в стек два редактора: Back закрыл бы только один. */
internal fun NavBackStack<NavKey>.openEditor(alarmId: Long?) {
    if (lastOrNull() !is AlarmEditKey) add(AlarmEditKey(alarmId))
}
