# ADR-009: Навигация (Navigation3) и границы UI-модулей

## Status
Accepted (2026-10-07, реализовано и проверено в M2: T13, T15, смок 3). Предложено 2026-10-04. Закрывает отложенный выбор ADR-003 §2 («navigation-compose 2.10.2 **или** navigation3 1.2.0 — ADR в M2») и PRD §6.1.

## Context
* В M2 появляются два экрана-потока: список будильников (FR-LIST) и полноэкранный редактор (FR-EDIT, PRD §4.1). В M3 добавятся онбординг, здоровье, настройки; в M4 — выбор мелодии; в M5 — настройки миссии. Экран звонка и миссии — отдельная `RingingActivity` (ADR-007 §10) и в граф навигации не входят.
* Версии на 2026-10-04 (developer.android.com, страницы релизов):
  * `androidx.navigation:navigation-compose` **2.10.2** (2026-09-23). Страница релизов несёт пометку: «This library is in maintenance mode and will only receive critical fixes; new features are not planned». Есть type-safe routes (kotlinx.serialization), predictive back с 2.9.
  * `androidx.navigation3:navigation3-runtime/ui` **1.2.0** stable (2026-09-23; 1.0.0 — 2025-11-19). 1.2.0: Result API, Deep Link API, `NavigationBackHandler` и исправления анимации predictive back. minSdk 23, compileSdk 37.
  * `androidx.lifecycle:lifecycle-viewmodel-navigation3` **2.11.0** — входит в уже используемый Lifecycle 2.11.0; `rememberViewModelStoreNavEntryDecorator()` скоупит ViewModel на запись стека и очищает её при pop.
* targetSdk 37 → predictive back включён по умолчанию (ADR-003 §1). Back в редакторе с несохранёнными изменениями должен показывать диалог (AC FR-EDIT).
* Hilt: `hiltViewModel()` (`androidx.hilt:hilt-lifecycle-viewmodel-compose` 1.4.0, уже в `balarm.android.feature`) берёт `LocalViewModelStoreOwner` — от декоратора Nav3 он работает так же, как от `NavBackStackEntry`. Параметры экрана (id будильника) передаются через assisted injection (`hiltViewModel<VM, VM.Factory>(creationCallback = …)`), а не через `SavedStateHandle`.
* Принцип проекта: не вводить абстракцию без второго потребителя; feature-модули не зависят друг от друга (PRD §6.2).

## Decision
1. **Navigation3 1.2.0** (`navigation3-runtime`, `navigation3-ui`) + `lifecycle-viewmodel-navigation3` 2.11.0. `navigation-compose` не подключается.
2. **Граф и стек — только в `:app`.** `:app` держит:
   * ключи `@Serializable sealed interface BalarmKey : NavKey` — `AlarmListKey` (data object), `AlarmEditKey(alarmId: Long?)` (`null` — новый будильник; `Long`, а не `AlarmId`: value class в сериализации ключа не нужен);
   * стек `rememberNavBackStack(NavConfiguration, AlarmListKey)` (переживает поворот и смерть процесса). `NavConfiguration` (`SavedStateConfiguration` в `BalarmApp.kt`) регистрирует **все** подтипы `NavKey` (полиморфная сериализация): забытый ключ падает в рантайме при сохранении стека, а не при компиляции. `NavKeysTest` сверяет регистрацию с дескриптором sealed-интерфейса `BalarmKey` (проверено мутацией) — **каждый новый ключ (M3: онбординг, настройки, «Надёжность») регистрируется в `NavConfiguration`**, тест падает иначе. Ключи `internal` в `:app`;
   * защита от двойного тапа: открытие редактора не кладёт второй `AlarmEditKey` поверх первого (`NavKeysTest`);
   * `onClose` редактора снимает запись, только если на вершине стека — редактор (повторный/запоздалый `onClose` не снимает список);
   * `AlarmEditKey` восстанавливается после смерти процесса; если будильник к этому времени удалён, редактор закрывается сам (`Close`/`LoadFailed`), а не показывает пустую форму;
   * `NavDisplay(entryDecorators = [rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()])` и `entryProvider`, где колбэки экранов превращаются в `backStack.add(...)` / `removeLastOrNull()`.
3. **Feature-модули не знают о навигации.** Публичный контракт feature-модуля — один `@Composable` Route с колбэками, без `NavKey` и без зависимости от navigation3:
   ```kotlin
   // :feature:alarmlist
   @Composable fun AlarmListRoute(onAddAlarm: () -> Unit, onOpenAlarm: (AlarmId) -> Unit, modifier: Modifier = Modifier)
   // :feature:alarmedit
   @Composable fun AlarmEditRoute(alarmId: AlarmId?, onClose: () -> Unit, modifier: Modifier = Modifier)
   ```
   Так `:feature:alarmlist` и `:feature:alarmedit` не зависят друг от друга; отдельные `:feature:*:api`-модули не нужны (нет второго потребителя). Если позже экрану понадобится результат другого экрана — Result API Nav3 1.2 в `:app`, не прямые зависимости.
4. **Back.** Системный Back/predictive back по умолчанию снимает верхнюю запись (`NavDisplay.onBack`). Редактор перехватывает Back своим `BackHandler(enabled = state.isDirty || state.saving)` (`androidx.activity.compose`, `:feature:alarmedit` подключает `activity-compose` сам):
   * `isDirty` — событие `Back` во ViewModel → диалог «Отменить изменения?»;
   * `saving` — Back поглощается (ViewModel игнорирует `Back` во время сохранения): экран закроется сам после ответа движка и тоста, а не раньше него;
   * иначе обработчик выключен — обычный predictive back навигации с анимацией.

   Результат проверки (T15, смок 3, эмулятор API 37): `BackHandler` внутри записи `NavDisplay` получает Back раньше `NavDisplay` — и для кнопки/`KEYCODE_BACK`, и для **реального жеста predictive back**; жест не обходит обработчик. Запасной вариант `NavigationBackHandler` не понадобился. Закреплено тестами: `AlarmEditScreenTest` (внешний `BackHandler` получает Back, только если обработчик экрана выключен) и `BalarmNavigationTest` (порядок обработчиков в настоящем `NavDisplay`).
5. **Нижняя навигация** (PRD §4.1 «Будильники · Настройки») — не в M2: экрана «Настройки» в M2 нет, панель с одним пунктом бессмысленна. Появится в M3 вместе с экраном здоровья (Настройки → Надёжность) как второй top-level стек в `:app` (рецепт «multiple back stacks» Nav3 — список стеков по вкладкам).
6. **Модули M2** (по ADR-003 §3 — создаются с первым кодом): `:feature:alarmlist`, `:feature:alarmedit` (плагин `balarm.android.feature`), `:core:format` (общий форматтер времени — ADR-011 §6). `:app` → оба feature-модуля; feature → `:core:domain`, `:core:model`, `:core:format`, `:core:designsystem`.
7. **Каталог:** `navigation3 = "1.2.0"`, `androidx-navigation3-runtime`, `androidx-navigation3-ui`, `androidx-lifecycle-viewmodel-navigation3` (version.ref `androidxLifecycle`); в `:app` — плагин `kotlin-serialization` (уже в каталоге) и `kotlinx-serialization-core`.

```
:app ── BalarmApp: NavDisplay(backStack: [AlarmListKey, AlarmEditKey(id?)])
  │        entry<AlarmListKey> { AlarmListRoute(onAddAlarm = { openEditor(null) },
  │                                             onOpenAlarm = { openEditor(it.value) }) }   // не кладёт второй редактор
  │        entry<AlarmEditKey> { AlarmEditRoute(it.alarmId?.let(::AlarmId),
  │                                             onClose = { if (last is AlarmEditKey) removeLastOrNull() }) }
  ├─► :feature:alarmlist ─┐
  ├─► :feature:alarmedit ─┼─► :core:domain ─► :core:model
  └─► :feature:ringing  ──┘    :core:format, :core:designsystem
```

## Alternatives considered
* **navigation-compose 2.10.2** — зрелая, встроенные multiple back stacks для нижней навигации, `hilt-navigation-compose` с `SavedStateHandle.toRoute()`. Отклонено: библиотека в maintenance mode (новых функций не будет), стек — непрозрачный `NavController`, тесты навигации требуют `TestNavHostController`; на горизонте релиза (M8, 2027) это техдолг. Аргументы ключей через `SavedStateHandle` нам не нужны — id передаётся assisted injection.
* **Без библиотеки** (`var screen by rememberSaveable` + `AnimatedContent` + `BackHandler`) — < 50 строк для двух экранов, но к M3–M5 стек вырастет до 6–8 экранов, понадобятся ViewModel-скоупы на экран, predictive back-анимации и сохранение стека — фактически переписанный Nav3. Отклонено.
* **`NavKey` в feature-модулях + `EntryProviderScope.alarmListEntry(...)`-расширения** (стиль nav3-recipes «modular») — каждому feature нужен navigation3-runtime и serialization-плагин, а list должен знать ключ редактора (зависимость feature → feature) или нужен `:feature:alarmedit:api`. Отклонено как преждевременное: колбэков Route достаточно.
* **Отдельная Activity для редактора** — дешёвый back stack системы, но два хоста Compose, дублирование темы/edge-to-edge, сложнее передать результат (тост). Отклонено.

## Consequences
* (+) Стек — обычный `SnapshotStateList` ключей: навигацию в `:app` можно проверить Robolectric-тестом без моков контроллера.
* (+) Feature-модули тестируются изолированно (Route + ViewModel), без навигационной инфраструктуры.
* (+) Predictive back и сохранение стека — из коробки.
* (−) Нижняя навигация с несколькими стеками в M3 — вручную (рецепт), ≈ 50–80 строк в `:app`.
* (−) Nav3 моложе (stable с 2025-11); риск несовпадения транзитивной версии Compose с BOM 2026.09.00 — проверить `./gradlew :app:dependencies` в задаче навигации; при конфликте — поднять BOM отдельной задачей (ADR-003, правило «обновления — отдельными задачами»).
* (−) Черновик редактора переживает поворот (ViewModel), но не смерть процесса (стек восстановится, черновик — нет); приемлемо для v0.1.
* Проверка (выполнено в M2): Robolectric-тесты `:app` (`BalarmNavigationTest`: FAB ↔ Back, карточка → редактор с id, Back на корне закрывает приложение, пересоздание Activity; `NavKeysTest`); `AlarmEditScreenTest` — Back с изменениями → диалог; смок 3 на эмуляторе API 37 — кнопка Back и жест predictive back с правками → диалог, без правок → закрытие с predictive-анимацией.
* Транзитивные версии Compose (T13): максимум, запрошенный Nav3, — 1.11.0, BOM 2026.09.00 даёт 1.12.1 — конфликта нет.

## Related
ADR-003 §2–§4, ADR-007 §9–§10, ADR-010, ADR-011; PRD §4.1, §6.1, §6.2; FR-LIST-3, FR-LIST-4, FR-EDIT AC (Back → диалог).
