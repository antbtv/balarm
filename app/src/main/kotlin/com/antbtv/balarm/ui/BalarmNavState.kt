package com.antbtv.balarm.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/** Вкладки нижней панели; порядок — порядок пунктов панели. */
internal enum class Tab(val root: NavKey) {
    ALARMS(AlarmListKey),
    SETTINGS(SettingsKey),
}

/** Стек навигации: две вкладки и онбординг. Имя входит в `contentKey` записей — стеки не делят состояние. */
internal enum class StackId { ONBOARDING, ALARMS, SETTINGS }

/**
 * Состояние навигации (ADR-014 §2, рецепт nav3-recipes `multiplestacks`): по стеку на вкладку, выход — через
 * вкладку будильников. Онбординг (ADR-013 §2) — отдельный стек: пока он не пуст, вкладки не показываются.
 *
 * Всё состояние — снапшот-объекты, которые переживают пересоздание Activity и смерть процесса
 * ([rememberBalarmNavState]); логика — обычные методы, проверяемые без Compose (`NavKeysTest`).
 */
@Stable
internal class BalarmNavState(
    currentTab: MutableState<Tab>,
    val onboarding: NavBackStack<NavKey>,
    val alarms: NavBackStack<NavKey>,
    val settings: NavBackStack<NavKey>,
) {
    var currentTab: Tab by currentTab
        private set

    val inOnboarding: Boolean get() = onboarding.isNotEmpty()

    fun stack(id: StackId): NavBackStack<NavKey> = when (id) {
        StackId.ONBOARDING -> onboarding
        StackId.ALARMS -> alarms
        StackId.SETTINGS -> settings
    }

    /**
     * Что получает `NavDisplay`: онбординг; либо стек будильников, а поверх него — стек настроек, если открыта
     * вкладка настроек. Так системный Back (и predictive back) с корня настроек показывает список, а с корня
     * списка — закрывает приложение.
     */
    val visibleStacks: List<StackId>
        get() = when {
            inOnboarding -> ONBOARDING_ONLY
            currentTab == Tab.SETTINGS -> BOTH_TABS
            else -> ALARMS_ONLY
        }

    /** Верхний экран — корень вкладки: только тогда видна нижняя панель (ADR-014 §3). */
    val atTabRoot: Boolean
        get() = !inOnboarding && stack(currentTab.stackId).size == 1

    /** Тап по вкладке; повторный тап по текущей снимает её стек до корня. */
    fun selectTab(tab: Tab) {
        if (inOnboarding) return
        if (tab == currentTab) {
            stack(tab.stackId).popToRoot()
        } else {
            currentTab = tab
        }
    }

    /** Системный Back, когда `NavDisplay` показывает больше одного экрана. */
    fun back() {
        val stack = if (inOnboarding) onboarding else stack(currentTab.stackId)
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            !inOnboarding && currentTab != Tab.ALARMS -> currentTab = Tab.ALARMS
        }
    }

    /** Онбординг пройден: стек заменяется корнем вкладок — Back из списка в онбординг не вернёт. */
    fun finishOnboarding() {
        if (!inOnboarding) return
        alarms.popToRoot()
        settings.popToRoot()
        currentTab = Tab.ALARMS
        onboarding.clear()
    }

    private companion object {
        val ONBOARDING_ONLY = listOf(StackId.ONBOARDING)
        val ALARMS_ONLY = listOf(StackId.ALARMS)
        val BOTH_TABS = listOf(StackId.ALARMS, StackId.SETTINGS)
    }
}

internal val Tab.stackId: StackId
    get() = when (this) {
        Tab.ALARMS -> StackId.ALARMS
        Tab.SETTINGS -> StackId.SETTINGS
    }

/**
 * @param showOnboarding решение по флагу `SetupState` — только для первого запуска экрана: сохранённые стеки
 * (поворот, смерть процесса) восстанавливаются как есть и важнее флага (ADR-013 §2).
 */
@Composable
internal fun rememberBalarmNavState(showOnboarding: Boolean): BalarmNavState {
    val tab = rememberSaveable { mutableStateOf(Tab.ALARMS) }
    // Один call site: ключ сохранения не зависит от флага, восстановленный стек всегда найдётся.
    val onboarding = rememberNavBackStack(NavConfiguration, *if (showOnboarding) ONBOARDING_START else NO_KEYS)
    val alarms = rememberNavBackStack(NavConfiguration, AlarmListKey)
    val settings = rememberNavBackStack(NavConfiguration, SettingsKey)
    return remember(tab, onboarding, alarms, settings) { BalarmNavState(tab, onboarding, alarms, settings) }
}

/**
 * Открыть [target] поверх [from], только если [from] сейчас на вершине. Двойной тап (или тап по второму элементу
 * во время перехода) не кладёт в стек второй экран: Back закрыл бы только один.
 */
internal fun NavBackStack<NavKey>.openFrom(from: NavKey, target: NavKey) {
    if (lastOrNull() == from) add(target)
}

/** Закрыть [key], только если он на вершине: повторный/запоздалый `onClose` не снимет экран под ним. */
internal fun NavBackStack<NavKey>.closeTop(key: NavKey) {
    if (lastOrNull() == key) removeAt(lastIndex)
}

/** Редактор открывается только со списка; двойной тап по FAB или карточке даёт одну запись. */
internal fun NavBackStack<NavKey>.openEditor(alarmId: Long?) = openFrom(AlarmListKey, AlarmEditKey(alarmId))

private val ONBOARDING_START = arrayOf<NavKey>(OnboardingKey)
private val NO_KEYS = emptyArray<NavKey>()

private fun NavBackStack<NavKey>.popToRoot() {
    while (size > 1) removeAt(lastIndex)
}
