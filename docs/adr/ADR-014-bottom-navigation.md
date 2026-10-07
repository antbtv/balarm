# ADR-014: Нижняя навигация: два top-level стека Navigation3, экраны настроек, здоровья и «О приложении»

## Status
Accepted (2026-10-07, M3 завершён; уточнения по итогам реализации — в конце файла). Дополняет ADR-009 §5 («нижняя навигация — M3, рецепт multiple back stacks»); остальные решения ADR-009 не меняются.

## Context
* PRD §4.1: нижняя навигация «Будильники · Настройки»; полноэкранные потоки вне навигации — онбординг, редактор, выбор мелодии, настройки миссии (звонок и миссия — `RingingActivity`).
* Экран здоровья (FR-REL-7) открывается из двух мест: Настройки → Надёжность и баннер списка (FR-LIST-5).
* PRD §3.9 перечисляет много глобальных настроек; в M3 по скоупу нужны только «Здоровье будильника» и «О приложении» (лицензии мелодий/иконок; FR-FLAG-5 — 7 тапов по версии в debug).
* Nav3 1.2.0: рецепт `multiplestacks` (nav3-recipes) — состояние навигации с отдельным стеком на вкладку, свой `SaveableStateHolder` на стек, выход через стартовую вкладку («exit through home»).
* Контракт insets из M2: экран списка сам обрабатывает `safeDrawing`, `:app` не оборачивает его в `Scaffold` (иначе двойные отступы).

## Decision
1. **Ключи** (`:app`, `BalarmKeys.kt`, все регистрируются в `NavConfiguration`, `NavKeysTest` проверяет): `AlarmListKey`, `SettingsKey` (корни вкладок), `AlarmEditKey(alarmId: Long?)`, `HealthKey`, `AboutKey`, `OnboardingKey`.
2. **Состояние навигации** — `BalarmNavState` в `:app` (по рецепту): `currentTab: Tab` (`enum Tab { ALARMS, SETTINGS }`, `rememberSaveable`) и по `NavBackStack` на вкладку (`rememberNavBackStack(NavConfiguration, AlarmListKey)` / `(…, SettingsKey)`). `NavDisplay` получает плоский список «стек ALARMS + стек SETTINGS, если текущая — SETTINGS»: Back с корня настроек ведёт к списку, с корня списка — закрывает приложение. Повторный тап по текущей вкладке снимает её стек до корня. Онбординг — отдельный режим `BalarmApp` (ADR-013): пока он не пройден, вкладки не создаются.
3. **Панель видна только на корнях вкладок** (`AlarmListKey`, `SettingsKey`); всё остальное (редактор, здоровье, «О приложении») — полноэкранно, поверх текущей вкладки. Правило одно и без исключений; здоровье из баннера кладётся в стек ALARMS, из настроек — в стек SETTINGS (переключать вкладку при переходе из баннера не нужно).
4. **Панель** — `BalarmNavigationBar` в `:core:designsystem` (Material 3 `NavigationBar` с токенами, иконки — авторские CC0, подписи RU/EN, TalkBack). `:app` рисует её в `Scaffold(bottomBar, contentWindowInsets = WindowInsets(0))`; корневым экранам передаётся нижний отступ панели, а `navigationBars` помечаются потреблёнными (`consumeWindowInsets`) — экран списка продолжает сам обрабатывать верх и бока. FAB и нижний `SystemBarScrim` списка — над панелью (проверка fontScale 2f/360dp).
5. **`:feature:settings`** (`balarm.android.feature`) — три Route:
   ```kotlin
   @Composable fun SettingsRoute(onOpenHealth: () -> Unit, onOpenAbout: () -> Unit, modifier: Modifier = Modifier)
   @Composable fun HealthRoute(onClose: () -> Unit, modifier: Modifier = Modifier)
   @Composable fun AboutRoute(onClose: () -> Unit, onOpenDebugFlags: (() -> Unit)?, modifier: Modifier = Modifier)
   ```
   * Настройки M3: строка «Здоровье будильника» со сводкой (✅ «Всё в порядке» / ⚠️ «N проблем») и «О приложении». Остальные пункты §3.9 — с их этапами (автостоп — M7, миссии — M5); язык — по решению пользователя (одна строка → `Settings.ACTION_APP_LOCALE_SETTINGS`, `generateLocaleConfig` уже включён).
   * Здоровье: `HealthViewModel(checker, setup, repository, engine, testAlarmRunner, clock)` — отчёт ADR-012, пересчёт на resume, «Исправить» по пунктам, «Повторить планирование» (`engine.rescheduleAll(RescheduleReason.USER_RETRY)`, try/catch), «Тестовый будильник через 1 минуту» (ADR-012 §8; эффект с моментом → строка «Зазвонит в HH:MM», совет заблокировать экран), чек-бокс OEM.
   * О приложении: версия, лицензии (мелодии/иконки, из `docs/LICENSES.md` → строковые ресурсы), в debug 7 тапов по версии → `onOpenDebugFlags` (`:app` передаёт не-null только из debug source set; `FeatureFlagsActivity` уже есть).
   * Здоровье и настройки — в одном модуле: PRD §6.2 не выделяет `:feature:health`, второго потребителя модуля нет (баннер списка ходит через колбэк `:app`).
6. **Feature-модули по-прежнему не знают друг друга и навигацию** (ADR-009 §3).

## Alternatives considered
* **`NavigationSuiteScaffold`** (material3-adaptive) — новая зависимость ради двух пунктов на телефоне. Отклонено.
* **Один стек + «вкладка» как корень** — Back из настроек и состояние списка при переключении теряются. Отклонено.
* **Здоровье внутри вкладки настроек с панелью; из баннера — переключать вкладку** — два правила видимости панели и неожиданный прыжок вкладки. Отклонено.
* **Отдельный `:feature:health`** — без второго потребителя. Отклонено (можно выделить позже без изменения Route).

## Consequences
* (+) Одно правило видимости панели; баннер и настройки открывают тот же экран.
* (−) ≈ 60–80 строк состояния навигации в `:app`; меняется контракт insets списка (нижний отступ — от `:app`).
* (−) Новые ключи — обязательная регистрация в `NavConfiguration` (падение в рантайме иначе; ловит `NavKeysTest`).
* Проверка: Robolectric `:app` — переключение вкладок сохраняет стеки и прокрутку, Back с корня настроек → список, с корня списка → выход, баннер → здоровье → Back → список, повторный тап по вкладке, пересоздание Activity и смерть процесса (текущая вкладка и оба стека восстановлены), панель скрыта на редакторе/здоровье/онбординге; эмулятор — визуально FAB/scrim над панелью, predictive back жестом между вкладками.

## Related
ADR-009 §2–§5, ADR-012, ADR-013; PRD §3.9, §4.1, §6.2, FR-LIST-5, FR-REL-7, FR-FLAG-5, NFR-7.

## Уточнения по итогам M3
* Панель живёт внутри записей корней (`TabRoot`: `Scaffold(bottomBar, contentWindowInsets = WindowInsets(0))`), а не вокруг `NavDisplay`: иначе при переходе в редактор панель исчезает мгновенно, и FAB уходящего списка прыгает. Запись `SettingsKey` несёт fade-`transitionSpec` для переключения вкладок; `visibleStacks` — плоский список для `NavDisplay`, `contentKey` с префиксом стека.
* `Tab` — просто `enum { ALARMS, SETTINGS }`; корневые ключи задаёт `BalarmNavState`.
* Старт: `AppViewModel` + платформенный `OnPreDrawListener` (splash); `DebugTools` с `@BindsOptionalOf` — в release `Optional` пуст, `onOpenDebugFlags = null`.
* Нижний отступ вкладок: `Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)`; Health/About/Edit/Onboarding обрабатывают `safeDrawing` сами.
* Не подтверждено инструментально: fade vs slide при predictive back на корне настроек (смотреть на устройстве, M8).
