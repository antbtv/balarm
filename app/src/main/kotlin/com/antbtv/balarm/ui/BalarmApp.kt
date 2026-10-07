package com.antbtv.balarm.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.antbtv.balarm.R
import com.antbtv.balarm.core.designsystem.component.BalarmIcons
import com.antbtv.balarm.core.designsystem.component.BalarmNavigationBar
import com.antbtv.balarm.core.designsystem.component.NavBarItem
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.feature.alarmedit.AlarmEditRoute
import com.antbtv.balarm.feature.alarmlist.AlarmListRoute
import com.antbtv.balarm.feature.onboarding.OnboardingRoute
import com.antbtv.balarm.feature.settings.AboutRoute
import com.antbtv.balarm.feature.settings.HealthRoute
import com.antbtv.balarm.feature.settings.SettingsRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Подтипы [NavKey] для сохранения стеков (поворот, смерть процесса): без регистрации `rememberNavBackStack` падает. */
internal val NavConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(AlarmListKey::class)
            subclass(SettingsKey::class)
            subclass(AlarmEditKey::class)
            subclass(HealthKey::class)
            subclass(AboutKey::class)
            subclass(OnboardingKey::class)
        }
    }
}

/**
 * Корневой composable приложения (ADR-009, ADR-013 §2, ADR-014).
 *
 * @param showOnboarding стартовое решение по `SetupState`; `null` — ещё не прочитано: ничего не компонуется,
 * `MainActivity` держит системный splash.
 * @param onOpenDebugFlags экран feature flags; не-null только в debug-сборке.
 */
@Composable
fun BalarmApp(showOnboarding: Boolean?, onOpenDebugFlags: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (showOnboarding == null) return
    val nav = rememberBalarmNavState(showOnboarding)
    BalarmNavDisplay(nav = nav, onOpenDebugFlags = onOpenDebugFlags, modifier = modifier)
}

/**
 * Каждый стек декорируется отдельно (свой `SaveableStateHolder`, ключи записей с префиксом стека) — экраны
 * вкладки, которая сейчас не видна, сохраняют состояние и ViewModel. `NavDisplay` получает плоский список
 * [BalarmNavState.visibleStacks].
 */
@Composable
private fun BalarmNavDisplay(nav: BalarmNavState, onOpenDebugFlags: (() -> Unit)?, modifier: Modifier = Modifier) {
    val navItems = rememberNavBarItems()
    val onboarding = rememberStackEntries(nav, StackId.ONBOARDING, navItems, onOpenDebugFlags)
    val alarms = rememberStackEntries(nav, StackId.ALARMS, navItems, onOpenDebugFlags)
    val settings = rememberStackEntries(nav, StackId.SETTINGS, navItems, onOpenDebugFlags)
    val entries = nav.visibleStacks.flatMap { id ->
        when (id) {
            StackId.ONBOARDING -> onboarding
            StackId.ALARMS -> alarms
            StackId.SETTINGS -> settings
        }
    }
    val onBack = remember(nav) { { nav.back() } }
    NavDisplay(entries = entries, modifier = modifier, onBack = onBack)
}

@Composable
private fun rememberStackEntries(
    nav: BalarmNavState,
    id: StackId,
    navItems: List<NavBarItem>,
    onOpenDebugFlags: (() -> Unit)?,
): List<NavEntry<NavKey>> {
    val provider = remember(nav, id, navItems, onOpenDebugFlags) {
        balarmEntryProvider(nav = nav, id = id, navItems = navItems, onOpenDebugFlags = onOpenDebugFlags)
    }
    return rememberDecoratedNavEntries(
        backStack = nav.stack(id),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = provider,
    )
}

/**
 * Колбэки экранов → операции над стеком [id]. Здоровье открывается в том стеке, откуда пришёл пользователь
 * (баннер списка — будильники, настройки — настройки), вкладка не переключается (ADR-014 §3).
 */
private fun balarmEntryProvider(
    nav: BalarmNavState,
    id: StackId,
    navItems: List<NavBarItem>,
    onOpenDebugFlags: (() -> Unit)?,
): (NavKey) -> NavEntry<NavKey> {
    val stack = nav.stack(id)
    // HealthKey может жить в двух стеках: без префикса записи делили бы состояние и ViewModel.
    val contentKey: (NavKey) -> Any = { key -> "${id.name}/$key" }
    return entryProvider {
        entry<AlarmListKey>(clazzContentKey = contentKey) {
            TabRoot(nav = nav, tab = Tab.ALARMS, navItems = navItems) { rootModifier ->
                AlarmListRoute(
                    onAddAlarm = { stack.openEditor(alarmId = null) },
                    onOpenAlarm = { stack.openEditor(alarmId = it.value) },
                    onOpenHealth = { stack.openFrom(AlarmListKey, HealthKey) },
                    modifier = rootModifier,
                )
            }
        }
        entry<SettingsKey>(clazzContentKey = contentKey, metadata = TabSwitchTransition) {
            TabRoot(nav = nav, tab = Tab.SETTINGS, navItems = navItems) { rootModifier ->
                SettingsRoute(
                    onOpenHealth = { stack.openFrom(SettingsKey, HealthKey) },
                    onOpenAbout = { stack.openFrom(SettingsKey, AboutKey) },
                    modifier = rootModifier,
                )
            }
        }
        entry<AlarmEditKey>(clazzContentKey = contentKey) { key ->
            AlarmEditRoute(
                alarmId = key.alarmId?.let(::AlarmId),
                // Только если редактор ещё на вершине: лишний pop снял бы список.
                onClose = { if (stack.lastOrNull() is AlarmEditKey) stack.removeAt(stack.lastIndex) },
            )
        }
        entry<HealthKey>(clazzContentKey = contentKey) {
            HealthRoute(onClose = { stack.closeTop(HealthKey) })
        }
        entry<AboutKey>(clazzContentKey = contentKey) {
            AboutRoute(onClose = { stack.closeTop(AboutKey) }, onOpenDebugFlags = onOpenDebugFlags)
        }
        entry<OnboardingKey>(clazzContentKey = contentKey) {
            OnboardingRoute(onFinished = nav::finishOnboarding)
        }
    }
}

/**
 * Корень вкладки с нижней панелью (ADR-014 §3–§4). Панель — часть корневого экрана: при переходе в редактор,
 * здоровье или «О приложении» она уходит вместе с ним, и отступы списка не прыгают во время анимации.
 * Экрану передаётся отступ панели, а `navigationBars` помечаются потреблёнными: сам экран обрабатывает только
 * верх и бока `safeDrawing`, его FAB и нижняя подложка оказываются над панелью.
 */
@Composable
private fun TabRoot(
    nav: BalarmNavState,
    tab: Tab,
    navItems: List<NavBarItem>,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        bottomBar = {
            BalarmNavigationBar(
                items = navItems,
                selectedIndex = tab.ordinal,
                onSelect = { index -> nav.selectTab(Tab.entries[index]) },
            )
        },
        containerColor = BalarmTheme.colors.background,
        contentColor = BalarmTheme.colors.textPrimary,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        content(Modifier.padding(innerPadding).consumeWindowInsets(innerPadding))
    }
}

/** Пункты панели в порядке [Tab]. */
@Composable
private fun rememberNavBarItems(): List<NavBarItem> {
    val alarms = stringResource(R.string.nav_tab_alarms)
    val settings = stringResource(R.string.nav_tab_settings)
    return remember(alarms, settings) {
        Tab.entries.map { tab ->
            when (tab) {
                Tab.ALARMS -> NavBarItem(label = alarms, icon = BalarmIcons.Alarm)
                Tab.SETTINGS -> NavBarItem(label = settings, icon = BalarmIcons.Settings)
            }
        }
    }
}

/**
 * Переключение вкладок — затухание, а не «сдвиг экрана»: панель одинакова у обоих корней и не должна уезжать.
 * Висит только на корне настроек: в плоском списке он входит (тап «Настройки») и снимается (Back с корня
 * настроек, тап «Будильники») ровно при переключении вкладок; Nav3 берёт спецификацию у входящей/снимаемой записи.
 */
private val TabSwitchTransition: Map<String, Any> =
    NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() } +
        NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() } +
        NavDisplay.predictivePopTransitionSpec { fadeIn() togetherWith fadeOut() }
