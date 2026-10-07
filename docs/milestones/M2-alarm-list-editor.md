# M2: UI списка и редактора будильников

| | |
|---|---|
| Статус | In progress |
| Утверждён | 2026-10-04 |
| Завершён | — |
| Требования | FR-LIST-1…4, FR-EDIT-1, 2, 4, 7, 10, 11 (+ AC «Назад» и AC FR-LIST), NFR-2, NFR-7, NFR-9; перенесено из M1 |
| Definition of Done (PRD §8) | E2E: создать → включить → звонит. Дополнительно: AC FR-LIST (тумблер выкл → `dumpsys alarm` без интента), Kover `:core:domain` ≥ 80 %, R1, R5, R13 на API 37 |

## Цель
Пользователь создаёт, редактирует, включает/выключает и удаляет будильники из интерфейса (список с шапкой «Следующий через …», редактор с колесом времени, днями, меткой и настройками snooze), нажимает «Тест» и слышит полный сценарий звонка через 5 с. Движок M1 не меняется по сути; единственное изменение движка — семантика `save/setEnabled`, нужная UI (ADR-011).

Вне скоупа M2: звук/громкость/fade-in/вибрация (M4), миссии (M5), баннер разрешений и экран здоровья (M3), экран настроек и нижняя навигация (M3), «дублировать», свайп-меню, «пропустить следующий» (бэклог).

## Архитектура этапа
Резюме от architect (детали — ADR-009…011):
```
:app ─ BalarmApp: NavDisplay [AlarmListKey, AlarmEditKey(alarmId: Long?)]   (Navigation3, граф только в :app)
 ├─► :feature:alarmlist ─┐
 ├─► :feature:alarmedit ─┼─► :core:domain ─► :core:model
 ├─► :feature:ringing  ──┤   :core:format (новый), :core:designsystem
 └─► :core:alarm ────────┴─► :core:format
```
* **Навигация:** Navigation3 1.2.0 + `lifecycle-viewmodel-navigation3`. Feature-модули отдают `@Composable`-Route с колбэками (`AlarmListRoute(onAddAlarm, onOpenAlarm)`, `AlarmEditRoute(alarmId, onClose)`) и друг о друге не знают.
* **Источник «когда зазвонит»:** `runtime.nextTriggerAt` (ровно то, что отдано `setAlarmClock`), без пересчёта в UI. `AlarmRepository.observeAlarmsWithRuntime()`; чистые функции `upcomingTrigger/isActive/nextTrigger`, `timeUntil` (округление вверх до минуты). Шапка обновляется Room Flow + минутным тиком только пока экран на переднем плане (NFR-9).
* **Движок:** `save` → `ScheduleResult(id, nextTriggerAt, scheduled)`; сохраняет `lastFiredAt` и ожидающий snooze (раньше сбрасывались — «открыл, нажал Сохранить во время snooze — проспал»); `setEnabled(false)` отменяет всё, включая snooze; «Сохранить» всегда включает будильник.
* **«Тест»:** настоящий `setAlarmClock` с зарезервированным `AlarmId.TEST = Long.MAX_VALUE`, снимок черновика в памяти (`TestAlarmStore`), БД не трогается; `AlarmEngine.onFired(TEST)` идёт мимо репозитория и `Mutex`; `rescheduleAll` тест не видит. Тот же механизм — для «Тестового будильника» M3.
* **`:core:format` (новый):** единый форматтер времени (3 копии → 1), «через X ч Y мин» через ICU `MeasureFormat`, порядок дней по `LocalePreferences`, `rememberClockFormat()` (12h/24h перечитывается на `ON_START`).
* **Дизайн-система:** `AlarmCard`, `DayPillsRow`, `DayChipsRow`, `PresetChips`, `BalarmSwitch`, `BalarmFab`, `NextAlarmHeader`, `SettingRow`, `SingleChoiceDialog`, `ConfirmDialog`, `LabelField`, `TimeWheelPicker` (LazyColumn + snap, «бесконечный», haptic, semantics для TalkBack). Компоненты не знают `:core:model`.
* **Метка:** лимит 40 в code points (`takeCodePoints`).
* **Feature-флаги:** новых нет. Список и редактор — ядро (FR-FLAG-4). Секция snooze в редакторе — под существующим `feature.snooze`.

## Решения на утверждение
- [x] **ADR-009** (Proposed) — Navigation3 вместо Navigation Compose (тот в maintenance mode); граф в `:app`; нижняя навигация — M3.
- [x] **ADR-010** (Proposed) — тестовый звонок: `AlarmId.TEST` + `setAlarmClock` + снимок в памяти.
- [x] **ADR-011** (Proposed) — контракт списка/редактора: `observeAlarmsWithRuntime`, `ScheduleResult`, новая семантика `save/setEnabled`, `:core:format`, метка в code points, `hasFiredFor`.
- [x] **Нижняя навигация и экран настроек — в M3** (в M2 вкладка была бы одна).
- [x] **Флаги `mission.math` и `customSounds` → `false`** в T01 (кода у фич нет; противоречит PRD §2 и правилу плана «false до конца этапа»); включить в T-docs M4/M5; поправить FR-FLAG-2.
- [x] **Экран звонка: системные панели видимы** (убрать `hideSystemBars()`): подсказка «Viewing full screen» перекрывает экран, на надёжность не влияет. Пересмотреть в M5 для миссии.
- [x] Дефолт нового будильника — **следующий целый час**, разовый, `SnoozeSettings.DEFAULT`.
- [x] В тестовом звонке кнопка «Отложить» **скрыта**.
- [x] Тост «Будильник зазвонит через …» показывается и **при включении тумблером**.
- [x] Удаление — **диалог подтверждения** (snackbar «Отменить» — бэклог); из списка — только **долгий тап** (свайп — бэклог).
- [x] «Будни» = Пн–Пт всегда (выходные Пт–Сб в отдельных локалях — бэклог).

## Задачи
| ✓ | ID | Задача | FR/NFR | Оценка | Зависит от | Commit |
|---|---|---|---|---|---|---|
| [x] | M2-T01 | 🔔 Модель: `AlarmId.TEST`, метка в code points, `hasFiredFor`, варианты snooze; флаги math/customSounds → false | FR-EDIT-4, 7 | S | — | |
| [x] | M2-T02 | 🔔 Движок: `ScheduleResult`, `save` сохраняет snooze/`lastFiredAt`, `setEnabled` | FR-LIST AC, FR-EDIT-11 | M | T01 | |
| [x] | M2-T03 | 🔔 `TestAlarmRunner` + `TestAlarmStore`, debug-команда `TEST`; **смок 1** | FR-EDIT-10 | M | T02 | |
| [x] | M2-T04 | Чистый домен: `upcomingTrigger/isActive/nextTrigger`, `timeUntil`, `AlarmDefaults`, `minuteTicks` | FR-LIST-1, 2 | S | T01 | |
| [x] | M2-T05 | 🔔 Данные: `observeAlarmsWithRuntime` (`@Relation`) | FR-LIST-1, 2 | S | T01 | |
| [x] | M2-T06 | `:core:format`: форматтер времени, «через X», дни недели; убрать 3 копии | NFR-7, перенос M1 | M | T04 | |
| [x] | M2-T07 | 🎨 Экран звонка: видимые системные панели | перенос M1 | S | T06 | |
| [x] | M2-T08 | 🎨 Дизайн-система ч.1: карточка, дни (чтение), переключатель, FAB, шапка, диалог, иконки | FR-LIST-1…3 | M | — | |
| [x] | M2-T09 | 🎨 `TimeWheelPicker` | FR-EDIT-1 | L | — | |
| [x] | M2-T10 | 🎨 Дизайн-система ч.2: выбор дней, пресеты, строка настройки, диалог выбора, поле метки | FR-EDIT-2, 4, 7 | M | — | |
| [x] | M2-T11 | `:feature:alarmlist`: ViewModel и состояние | FR-LIST-1, 2, 4 | M | T02, T04, T05 | |
| [x] | M2-T12 | 🎨 Экран списка | FR-LIST-1…4 | M | T06, T08, T11 | |
| [x] | M2-T13 | `:app`: Navigation3, список — стартовый экран; **смок 2** | NFR-2 | M | T12 | |
| [x] | M2-T14 | `:feature:alarmedit`: ViewModel, reducer, сохранение/тест/удаление | FR-EDIT-4, 7, 10, 11 | M | T02, T03, T04 | |
| [x] | M2-T15 | 🎨 Экран редактора + запись в граф; **смок 3 (DoD)** | FR-EDIT-1…11, AC | L | T09, T10, T13, T14 | |
| [ ] | M2-T-test | Тестирование этапа (tester) + R-матрица | — | L | T15 | |
| [ ] | M2-T-review | Ревью этапа (reviewer) | — | M | T-test | |
| [ ] | M2-T-docs | PRD/ADR/CLAUDE.md/скиллы, ADR-009…011 → Accepted | — | S | T-review | |

Порядок: T01 → T02 → T03 ∥ T04 ∥ T05 → T06 → T11 → T12 → T13 → T14 → T15. UI-компоненты T08–T10 независимы от логики, их можно делать в любой момент, но **сборки — строго по одной** (CLAUDE.md).

### M2-T01 — Модель 🔔
**Описание:** `AlarmId.TEST = Long.MAX_VALUE` (зарезервирован, `isSaved` для него — false для БД/репозитория, закрепить тестом); лимит метки в code points (`Alarm.init` — `codePointCount ≤ 40`) + `String.takeCodePoints(n)` (не режет суррогатную пару), применить в маппере Room и `DebugAlarmCommands`; `AlarmRuntimeState.hasFiredFor(at)` вместо трёх копий выражения `lastFiredAt?.let { !it.isBefore(x) } == true` в `AlarmEngine`; `SnoozeSettings.INTERVAL_OPTIONS` (1, 3, 5, 10, 15, 20, 30 мин) и `LIMIT_OPTIONS` (1, 2, 3, 5, 10, ∞). `config/features.properties`: `mission.math=false`, `customSounds=false` (если утверждено).
**Модули:** `:core:model`, `:core:data`, `:app` (debug), `config`
**Критерии приёмки:**
- [x] Метка из эмодзи/суррогатных пар длиной 40 code points принимается, 41 — отклоняется; `takeCodePoints` не оставляет «половину» пары
- [x] Маппер не падает на «битой» метке (толерантность M1 сохранена)
- [x] `./gradlew testDebugUnitTest` зелёный
**Тесты:** unit — `AlarmTest`, `AlarmIdTest`, `SnoozeSettingsTest`, `AlarmMapperTest`, `DebugAlarmCommandsTest`; `DebugFeatureFlagProviderTest` не должен ломаться от смены значений.

### M2-T02 — Движок: контракт для UI 🔔
**Описание:** `ScheduleResult(id, nextTriggerAt, scheduled)`; `save(alarm): ScheduleResult` — сохраняет `lastFiredAt` (защита от повторного звонка после перевода часов назад) и ожидающий SNOOZE/CATCH_UP, если он в будущем и раньше нового обычного срабатывания; `setEnabled(id, enabled): ScheduleResult?` (`null` — будильника нет); `setEnabled(false)` отменяет всё, включая snooze; `save/setEnabled/delete` отвергают `AlarmId.TEST`; `hasFiredFor` в `fire/restoredPlan/isMissed`. Обновить вызывающих (`DebugAlarmCommands` и др.).
**Модули:** `:core:domain`, `:app` (debug)
**Критерии приёмки:**
- [x] Snooze переживает `save` другого поля; `save` с новым временем раньше snooze — побеждает более раннее
- [x] `setEnabled(false)` на будильнике с ожидающим snooze → `FakeAlarmScheduler` без интента, runtime очищен
- [x] Редактирование и удаление во время звонка: звук/сессия не затронуты, `dismiss` после удаления ничего не пишет, RESUME удалённого → `Skip(DELETED)`
- [x] Отказ системы (`scheduled=false`) отражён в результате
- [x] Kover `:core:domain` ≥ 80 %
**Тесты:** unit на фейках из `testFixtures` — `AlarmEngineScheduleTest`/`AlarmEngineRobustnessTest` + новые кейсы; реальный движок, не mockk.

### M2-T03 — «Тест» 🔔
**Описание:** `TestAlarmStore` (интерфейс + `InMemoryTestAlarmStore`), `TestAlarmRunner.schedule(alarm, delay): Instant?`, `decision(): FireDecision.Ring` (снимок ?: дефолты, `canSnooze=false`), `finish()`; `AlarmEngine.onFired(TEST)` → runner (без репозитория и `Mutex`), `dismiss(TEST)` → `finish()`, `snooze(TEST)` → `NotAllowed`; Hilt-связка; событие `TEST_SCHEDULED` в логе; debug-команда `TEST seconds=5`. Процесс умер до срабатывания → звонок с настройками по умолчанию.
**Модули:** `:core:domain`, `:core:alarm`, `:app` (debug)
**Критерии приёмки:**
- [x] `rescheduleAll` не отменяет и не создаёт тест; PendingIntent теста ≠ PendingIntent будильника пользователя (`ShadowAlarmManager`)
- [x] Звонок теста вместе с будильником пользователя в одну минуту — очередь, один экран (R13)
- [x] **Смок 1 (эмулятор API 37):** `TEST seconds=5` → `RINGING_STARTED` ≤ 7 с после команды; при выключенном экране экран включается
**Тесты:** unit `TestAlarmRunner`; Robolectric — `ShadowAlarmManager`; эмулятор — смок 1.

### M2-T04 — Чистый домен списка
**Описание:** `AlarmWithRuntime.upcomingTrigger(now)` (REGULAR/SNOOZE/CATCH_UP, только будущее), `isActive(now)` (enabled **или** ожидающий SNOOZE/CATCH_UP — разовый отложенный имеет `enabled=false`, а тумблер должен показывать «вкл»), `List<AlarmWithRuntime>.nextTrigger(now)`; `TimeUntil(days, hours, minutes)` + `timeUntil(now, at)` с округлением вверх до минуты (0 не бывает); `AlarmDefaults.newAlarm(now)` (следующий целый час); `minuteTicks(clock)` из `RingingViewModel` в `:core:domain`.
**Модули:** `:core:domain`, `:feature:ringing`
**Критерии приёмки:**
- [x] `timeUntil`: 59 с → 1 мин, 60 с → 1 мин, 23:59:30 → 0 ч 1 мин на границе дня, 7 д — корректно
- [x] Выключенный без snooze → не активен; разовый с ожидающим snooze → активен
- [x] `RingingViewModel` работает на общей `minuteTicks`, тесты зелёные
**Тесты:** unit, табличные.

### M2-T05 — Данные списка 🔔
**Описание:** `AlarmRepository.observeAlarmsWithRuntime(): Flow<List<AlarmWithRuntime>>` (`@Transaction` + `@Relation`, `ORDER BY hour, minute, id`) вместо `observeAlarms()`; обновить `RoomAlarmRepository` и фейк в `testFixtures`. Схема БД не меняется.
**Модули:** `:core:data`, `:core:domain` (testFixtures)
**Критерии приёмки:**
- [x] Flow эмитит при изменении runtime (не только alarm)
- [x] Сортировка по времени; «битая» строка не ломает поток
- [x] Схема v1 без изменений (`schemas/1.json` не в диффе)
**Тесты:** Robolectric `RoomAlarmRepositoryTest`.

### M2-T06 — `:core:format`
**Описание:** новый Android-модуль (`balarm.android.library`, Compose runtime только для `remember*`): `ClockFormat` (24h/12h, ICU-шаблон + запасной формат из M1), `formatTimeUntil` (ICU `MeasureFormat`: `SHORT` на экране, `WIDE` для TalkBack; нулевые части опускаются), `WeekdayFormat` (порядок и подписи Пн–Вс по `LocalePreferences.getFirstDayOfWeek()`), `rememberClockFormat()` (перечитывает `DateFormat.is24HourFormat` на `ON_START`). Перевести `AlarmNotifications.kt`, `RingingScreen.kt`, `BalarmApp.kt` на модуль, удалить копии. Строка-обёртка тоста «Будильник зазвонит через %1$s» RU+EN — в `:core:format`; строки шапки списка («Следующий будильник через %1$s», «Нет активных будильников») — в `:feature:alarmlist` (T12).
**Модули:** `:core:format`, `:core:alarm`, `:feature:ringing`, `:app`, `settings.gradle.kts`
**Критерии приёмки:**
- [x] Ни одной копии форматтера времени вне `:core:format` (grep в ревью)
- [x] Тест всех локалей (из M1) проходит на общем форматтере
- [x] «через 7 ч 12 мин» / «7 hr, 12 min» (ICU; запятая в EN — норма), TalkBack-вариант с падежами — RU/EN
**Тесты:** Robolectric ru/en, 12h/24h; существующие тесты уведомления и экрана звонка зелёные. Ожидания — тем же форматтером; литералы — только для Robolectric SDK 37 (ICU зависит от версии Android).

### M2-T07 — 🎨 Экран звонка: системные панели (agent: ui-developer)
**Описание:** убрать `hideSystemBars()` в `RingingActivity` (прозрачные бары edge-to-edge, контент не перекрыт), удалить связанный код/комментарии; закрывает подсказку «Viewing full screen» (перенос M1).
**Модули:** `:feature:ringing`
**Критерии приёмки:**
- [x] Превью/тест: экран звонка при fontScale 2f, 360dp без перекрытия кнопок системными барами
- [x] Регрессионный тест `RingingActivityTest` — флаги окна showWhenLocked/turnScreenOn не затронуты
**Тесты:** Compose/Robolectric; визуально — смок 3.

### M2-T08 — 🎨 Дизайн-система ч.1 (agent: ui-developer)
**Описание:** `AlarmCard(time, amPm?, label, days: List<DayPillUi>, active, subtitle?, onToggle, onClick, onLongClick)` (подзаголовок: «Сегодня/Завтра», «Отложен до 07:05»), `DayPillsRow` (только чтение), `BalarmSwitch` (M3 `Switch` с токенами), `BalarmFab` (64dp, круг, primary), `NextAlarmHeader`, `ConfirmDialog`; векторные иконки (add, delete, chevron, keyboard) из Material Symbols Rounded в `res/drawable` + запись в `docs/LICENSES.md` (Apache 2.0); `material-icons-extended` не подключать. Компоненты принимают примитивы и строки, `:core:model` не знают.
**Модули:** `:core:designsystem`
**Критерии приёмки:**
- [x] Превью: тёмная тема, fontScale 2f, 360dp; зона тапа ≥ 48dp; TalkBack-описания (тумблер «Будильник 07:30, включён»)
- [x] Контраст проверяется `ContrastTest` для новых токенов, если они добавлены
**Тесты:** Compose UI (Robolectric): клик/долгий тап/переключение, семантика.

### M2-T09 — 🎨 `TimeWheelPicker` (agent: ui-developer)
**Описание:** колонка — `LazyColumn` + `rememberSnapFlingBehavior(state, SnapPosition.Center)`; «бесконечность» — `count = n × 1000`, старт из середины, значение `index % n`; выбранное — `derivedStateOf` по `layoutInfo`; haptic — `snapshotFlow { centered }.distinctUntilChanged()` → `HapticFeedbackType.SegmentFrequentTick` (проверить наличие в Compose 1.12, запасной `TextHandleMove`); 12h — колонки 1–12 и AM/PM; fontScale ≥ 1.5 → видно 3 строки вместо 5; semantics (`stateDescription`, `progressBarRangeInfo`, `setProgress`, `customActions` «больше/меньше»), по желанию кнопка «клавиатура» → M3 `TimeInput`.
**Модули:** `:core:designsystem`
**Критерии приёмки:**
- [x] Математика индексов — чистые функции, unit на JVM (в т.ч. границы 23→0, 59→0, 12h)
- [x] Compose-тесты через `performSemanticsAction(SetProgress)` и `performScrollToIndex`, без опоры на дальность fling и без `Thread.sleep`
- [x] Превью fontScale 2f; на эмуляторе (смок 3) прокрутка плавная, haptic срабатывает на шаг
**Тесты:** unit + Compose (Robolectric).

### M2-T10 — 🎨 Дизайн-система ч.2 (agent: ui-developer)
**Описание:** `DayChipsRow` (чип 40dp, зона тапа ≥ 48dp, порядок дней — параметр), `PresetChips` («Будни/Выходные/Каждый день»; выбран, если набор дней совпадает; повторный тап очищает → разовый), `SettingRow`, `SingleChoiceDialog` (AlertDialog с radio-строками; переиспользуется для fade-in в M4 и уровня Math в M5), `LabelField` (однострочный, счётчик «12/40», считает code points через переданный предикат/лимит).
**Модули:** `:core:designsystem`
**Критерии приёмки:**
- [x] Превью RU/EN, fontScale 2f; TalkBack — состояния «выбрано/не выбрано»
- [x] Поле метки не позволяет превысить 40 code points (вставка длинного текста обрезается без разрыва суррогатной пары)
**Тесты:** Compose UI (Robolectric).

### M2-T11 — `:feature:alarmlist`: логика
**Описание:** модуль на `balarm.android.feature`; `AlarmListViewModel` (immutable `AlarmListUiState`: карточки, шапка «через …», пусто/загрузка; `onEvent`: Toggle, Delete, Open, Add); подписка на `observeAlarmsWithRuntime` + `minuteTicks` через `stateIn(WhileSubscribed(5 s))`; тумблер — без оптимистичного состояния (отражает БД через Flow); тост при включении тумблером — эффект с `ScheduleResult`; ошибка `scheduled=false` → эффект «Не удалось запланировать».
**Модули:** `:feature:alarmlist`, `settings.gradle.kts`
**Критерии приёмки:**
- [x] Шапка: «Нет активных будильников» при пустом/выключенных; обновляется при смене runtime и по тику
- [x] Toggle/Delete идут только через `AlarmEngine`
- [x] Модуль не зависит от других `:feature:*`
**Тесты:** unit + Turbine, реальный движок на фейках.

### M2-T12 — 🎨 Экран списка (agent: ui-developer)
**Описание:** `AlarmListRoute(onAddAlarm, onOpenAlarm, modifier)`: `NextAlarmHeader`, `LazyColumn` карточек, FAB, пустое состояние, долгий тап → меню «Удалить» → `ConfirmDialog`; тост через `applicationContext`; 12h/24h и дни — из `:core:format`; строки RU+EN.
**Модули:** `:feature:alarmlist`
**Критерии приёмки:**
- [x] Превью: тёмная тема, fontScale 2f, 360dp, пустое состояние
- [x] Тап по карточке → `onOpenAlarm(id)`, FAB → `onAddAlarm`
**Тесты:** Compose UI (Robolectric): тумблер, меню долгого тапа, подтверждение удаления, пустое состояние, RU/EN.

### M2-T13 — `:app`: навигация
**Описание:** каталог (`navigation3-runtime/ui` 1.2.0, `lifecycle-viewmodel-navigation3`), плагин `kotlin-serialization` в `:app`; `@Serializable sealed interface BalarmKey : NavKey` (`AlarmListKey`, `AlarmEditKey(alarmId: Long?)`); `NavDisplay` с декораторами SaveableStateHolder и ViewModelStore; список — стартовый экран; удалить `PlaceholderScreen`, `PlaceholderTestTags`, копию форматтера; запись редактора — заглушка до T15.
**Модули:** `:app`, `gradle/libs.versions.toml`
**Критерии приёмки:**
- [x] `./gradlew :app:dependencies` — Nav3 не тянет Compose новее BOM 2026.09.00 (иначе — отдельная задача на BOM) — максимум запрошенной Nav3 версии 1.11.0, BOM даёт 1.12.1
- [x] Тест навигации: FAB → запись редактора → Back → список
- [x] **Смок 2 (эмулятор API 37):** тумблер ↔ `dumpsys alarm | grep com.antbtv.balarm` (выкл — интента нет, вкл — есть); смена часового пояса → шапка обновилась (R5) ✅; холодный старт списка (NFR-2 ≤ 800 мс, замер `am start -W`) — **⚠️ не выполнен на эмуляторе** (медиана ≈ 1,05 с, макс. 1,10–1,35 с, debug без R8/baseline profile, headless software-рендер; Settings на том же эмуляторе 530–680 мс) — вердикт по NFR-2 в T-test на release-сборке
**Тесты:** Robolectric/Compose в `:app`; эмулятор — смок 2.

### M2-T14 — `:feature:alarmedit`: логика
**Описание:** модуль на `balarm.android.feature`; `AlarmEditViewModel` (`@AssistedInject`, `hiltViewModel<VM, VM.Factory>(creationCallback = …)`); `AlarmEditUiState(loading, isNew, initial, draft, snoozeVisible, saving, dialog)` с `isDirty = draft != initial`; события `TimeChanged/DayToggled/PresetSelected/LabelChanged/SnoozeIntervalSelected/SnoozeLimitSelected/Save/Test/Delete/ConfirmDelete/Back/DiscardConfirmed/DialogDismissed`; эффекты `Saved(ScheduleResult)/TestScheduled(Instant?)/Close`; чистый reducer; «Сохранить» ставит `enabled = true`, гасит повторные нажатия (`saving`), **всегда вызывает движок, даже без правок** (см. «Добавлено по ходу» T14); метка обрезается пробелами по краям; при `feature.snooze=false` секция скрыта и `alarm.snooze` не трогается; «Тест» → `TestAlarmRunner.schedule(draft, 5 с)`; «Удалить» — только для существующего.
**Модули:** `:feature:alarmedit`, `settings.gradle.kts`
**Критерии приёмки:**
- [x] Dirty-логика: изменение и возврат к исходному значению → не dirty
- [x] `Save` не отменяется уходом с экрана (операция движка `NonCancellable`)
- [x] `feature.snooze=false`: `snoozeVisible=false`, сохранённый snooze не изменён
- [x] Модуль не зависит от других `:feature:*`
**Тесты:** unit reducer; ViewModel + Turbine на реальном движке с фейками.

### M2-T15 — 🎨 Экран редактора и сквозной прогон (agent: ui-developer)
**Описание:** `AlarmEditRoute(alarmId, onClose, modifier)`: `TimeWheelPicker`, `PresetChips` + `DayChipsRow`, `LabelField`, секция snooze (`SettingRow` + `SingleChoiceDialog` для интервала и лимита), кнопки «Сохранить»/«Тест»/«Удалить» (+ `ConfirmDialog`), `BackHandler(enabled = isDirty)` → диалог «Отменить изменения?»; тост «Будильник зазвонит через …» (через `applicationContext`, переживает закрытие экрана); запись `AlarmEditKey` в граф `:app`; строки RU+EN.
**Модули:** `:feature:alarmedit`, `:app`
**Критерии приёмки:**
- [x] Back с изменениями → диалог (и системный predictive back); без изменений → закрытие. Если `NavDisplay` перехватывает Back раньше `BackHandler` — переход на `NavigationBackHandler`
- [x] Compose-тесты при `feature.snooze=true/false`, лимит метки, «Тест», «Удалить»
- [x] **Смок 3 (эмулятор API 37, DoD):** создать будильник на +2 мин из UI → выключить экран → звонок (`RINGING_STARTED`) → «Отключить»; «Тест» из редактора звонит через ~5 с; Back с изменениями → диалог; визуальная проверка скриншотами (колесо, fontScale 2f, RU)
**Тесты:** Compose UI (Robolectric); эмулятор — смок 3.

### M2-T-test — Тестирование этапа (agent: tester)
Unit/Robolectric полностью (`./gradlew testDebugUnitTest`, `lint detekt ktlintCheck`, Kover ≥ 80 %). Эмуляторы строго по одному, headless, `-memory 2048`, `./gradlew --stop` до запуска, `adb emu kill` после:
| API | Сценарии |
|---|---|
| 37 | **R1** (будильник создан из UI), **R5** (шапка после смены зоны), **R13** (тест + будильник пользователя в одну минуту), AC FR-LIST (`dumpsys`), R2, R3 (созданный из UI, reboot до разблокировки), R6, R9; сценарии «сохранить во время snooze», «выключить тумблером ожидающий snooze», «удалить звонящий», «Тест» при `rescheduleAll`; a11y: TalkBack-описания, fontScale 2f |
| 34 | R1, формат 12h, редактор |

### M2-T-review — Ревью этапа (agent: reviewer)
Особое внимание: семантика `save/setEnabled` (регрессия надёжности), `AlarmId.TEST` не просачивается в БД/`rescheduleAll`, нет флагов на ядро, feature-модули не зависят друг от друга, нет копий форматтера, только `setAlarmClock`.

### M2-T-docs — Документация
* PRD: FR-FLAG-2 («по умолчанию false до завершения этапа»), §4.1 (нижняя навигация — M3), §6.1 (Navigation3, ADR-009), §6.2 (`:core:format`, граф в `:app`, Route с колбэками), FR-LIST-4 (только долгий тап в v0.1), FR-EDIT-10 (механизм «Тест»), FR-EDIT-11, FR-RING-1/экран звонка (панели видимы).
* ADR-009, 010, 011 → Accepted; ADR-003 — поправка M2 (`balarm.android.feature` для списка/редактора, Nav3).
* Скилл `alarmy-ui`: убрать `QuoteBlock` и строку «Цитата» из компоновки редактора; первый день недели — `LocalePreferences`; haptic — `SegmentFrequentTick`; добавить новые компоненты.
* CLAUDE.md — при необходимости (модули). Скилл `verify-alarm-reliability` — команда `TEST`.
* Feature-флаги: новых нет; реестр без изменений.

## Риски
| Риск | Митигация |
|---|---|
| Новая семантика `save` (сохраняет snooze/`lastFiredAt`) — регрессия надёжности | тесты движка на фейках, ревью T02, R1/R5/R13 |
| `AlarmId.TEST` попадёт в БД/`rescheduleAll` | `save/setEnabled/delete` отвергают TEST; тесты; ревью |
| Nav3 тянет Compose новее BOM | проверка `:app:dependencies` в T13; при конфликте — поднять BOM отдельной задачей |
| `NavDisplay` перехватывает Back раньше `BackHandler` | тест в T15; запасной вариант — `NavigationBackHandler` |
| `TimeWheelPicker`: производительность «бесконечного» списка, snap в Robolectric, TalkBack, fontScale 200 % | задача L; тесты на semantics, не на fling; смок 3 на эмуляторе |
| `HapticFeedbackType.SegmentFrequentTick` отсутствует в Compose 1.12 | запасной `TextHandleMove` |
| ICU `MeasureFormat` даёт разный текст на разных версиях Android | тесты сравнивают с тем же форматтером; литералы только для SDK 37 |
| `kotlin-serialization` при встроенном Kotlin AGP 9 в `:app` | проверка в T13 |
| Смена 12h/24h не меняет конфигурацию | `rememberClockFormat` перечитывает на `ON_START` |
| Черновик редактора теряется при смерти процесса | принято для v0.1 |
| Память машины: три смока на эмуляторе | строго последовательно; `./gradlew --stop` до, `adb emu kill` после; лимиты Gradle не повышать |
| Metaspace OOM демона после серии сборок (M1) | `./gradlew --stop` перед полной сборкой |

## Добавлено по ходу
- **T01:** `AlarmId.isSaved` остаётся `true` для `TEST` (планировщику нужен любой адресуемый id); «есть в хранилище» = `isSaved && !isTest` — KDoc, `AlarmRuntimeState`, Room-репозиторий и фейк отвергают `TEST`. Реестр флагов в PRD §3.12 приведён к конфигу (math/customSounds = false); формулировку правила FR-FLAG-2 — в T-docs. **Ревью T01 (⚠️ → исправлено):** KDoc `AlarmId`, тесты на одиночный суррогат (маппер, `takeCodePoints`), тест фейка репозитория. Известно (nit): `AlarmMapper.toDomain(AlarmRuntimeEntity)` с `alarmId = Long.MAX_VALUE` теперь бросает — только при повреждённой БД (AUTOINCREMENT такой id не выдаёт).

- **T02:** `AlarmEngine.save/setEnabled/delete` проверяют `AlarmId.TEST` до захвата мьютекса. Разовый, отредактированный во время snooze, не выключается звонком snooze (`kind != SNOOZE` в `recordFire`), иначе новое время потерялось бы. `resume` выключает разовый только при незаписанном срабатывании (`nextTriggerAt` в прошлом). Повторный `setEnabled(true)` на включённом = `editedPlan` (snooze не стирается). `locked(block)` больше не путает `null` результата с «движок занят». **Ревью T02 (⚠️ → исправлено):** +8 тестов (`AlarmEngineEditTest`: CATCH_UP, `clampPending`, равенство, snooze off в редакторе). **Известные компромиссы** (комментарий в `recordFire`): если выключение разового при первом звонке не записалось, или snooze стал догоном (CATCH_UP) после перезагрузки, разовый остаётся включённым до следующего звонка; после перевода часов назад осознанная правка времени «на сегодня» уходит на завтра (защита `lastFiredAt`). **Учесть в T04/T11/M3:** при `scheduled=false` `nextTriggerAt` — момент для повтора, не реально запланированный: шапка/список должны учитывать статус. **Эмулятор:** R* для T02 не гонялись отдельно (в задаче нет UI/команд) — сквозная проверка нового `save` — смок 1 в T03 (debug `SCHEDULE_IN` идёт через `save`) и T-test.
- **T03:** смок 1 (tester, API 37) ✅ 5/5: `TEST` → `ALARM_FIRED` → `RINGING_STARTED` +77 мс, ≈ 5,1 с от команды; нет «Snooze»; R1 (экран выключен); очередь с будильником пользователя (R13); `RESCHEDULE_ALL` не трогает тест; CRASH → RESUME через 5,3 с. **Ревью T03 (⚠️ → исправлено):** снимок теста больше не очищается при `dismiss` (иначе стирался снимок уже поставленного следующего теста — легально в M3); отказ системы возвращает прежний снимок; автостоп теста не публикует «Пропущен будильник»; +4 теста сервиса (очередь в обе стороны, автостоп, crash re-arm + RESUME без снимка), debug `TEST seconds` ≥ 1. **Наблюдение (не воспроизведено):** один раз при переходе из очереди в логе две строки `SOUND_STARTED` (в повторных прогонах — одна, один MediaPlayer) — следить в T-test. **В T-docs:** скилл `verify-alarm-reliability` (таблица CRASH) говорит, что у RESUME нет `ALARM_FIRED`, а фактически он логируется (`kind=RESUME`); ADR-010 → Accepted: снимок не очищается после звонка (§5), автостоп без уведомления «пропущен».
- **T04/T05 (ревью ⚠️ → исправлено):** `timeUntil` считает в миллисекундах (субсекундная граница округляла вниз); KDoc `upcomingTrigger`: при `scheduled=false` runtime хранит момент «для повтора» — на minSdk 34 `USE_EXACT_ALARM` не отзывается, статус — экран здоровья M3; тест «битого» runtime. **В T11:** `now` для `timeUntil` брать как `clock.instant()` на каждом тике, а не из усечённого `LocalDateTime` тика; добавить `distinctUntilChanged()` на потоке списка (Room-тесты на Turbine рассчитывают на одну эмиссию на запись). **Nit:** запрет `\n` в метке (ADR-011 §7) — в T14; при его появлении маппер Room должен чистить перевод строки, иначе `toDomain` бросит на «битой» строке.
- **T06 (ревью ⚠️ → исправлено):** AM/PM вырезается вне кавычек ICU-шаблона (`stripAmPm`), тест по всем локалям проверяет «цифры + маркер = полное время»; `ClockFormat.time/date` принимают `LocalTime/LocalDate/LocalDateTime`; `TimeUntil` не допускает 0/отрицательных значений; имена дней — `*_STANDALONE`. **Решение:** маркер AM/PM в карточке рисуется после цифр во всех локалях. ICU в EN даёт «7 hr, 12 min», в RU «2 дн. 3 ч 5 мин» (не «2 д»). Строки шапки списка перенесены в T12. **В T-docs:** добавить `:core:format` в PRD §6.2. **Метка:** Metaspace OOM KSP после серии сборок — `./gradlew --stop` помогает (как в M1).
- **T07 (ui-developer):** `hideSystemBars()` удалён, бары видимы и прозрачны (edge-to-edge), превью `RingingSystemBarsPreview`; тесты: бары видимы (через рефлексию `getRequestedVisibleTypes()` — Robolectric не пересчитывает `rootWindowInsets`; при обновлении Robolectric/SDK может сломаться), кнопки не перекрыты барами при fontScale 2f/360dp. Визуальная проверка — смок 3. **В T-docs:** скилл `alarmy-ui` и PRD §4: «экран звонка: бары видимы»; ревью T07 — вместе с UI-задачами (T08).
- **T08 (ui-developer, ревью T07+T08 ⚠️ → исправлено):** `AlarmCard(time, amPm, label, days, active, subtitle, contentDescription, toggleDescription, onToggle, onClick, onClickLabel, onLongClick, onLongClickLabel, modifier)` — три описания обязательны (TalkBack), `NextAlarmHeader` читается одним описанием с подписью, `DayPillsRow` при fontScale 2/360dp не обрезается (`DayPillLabelMinFontSize` 10sp, тест в Robolectric NATIVE). Новые токены: `captionStrong`, `BalarmShapes.Circle`, размеры (`CardPadding`, `SpacingTiny`, `DayPillIndicator`, `DayPillsRowMaxWidth`, `Icon`, `DayPillLabelMinFontSize`). Иконки (add, delete, chevron_right, keyboard) — **авторские CC0, не Material Symbols** (`docs/LICENSES.md`); при желании заменить на Material Symbols без смены API. **Отклонение от скилла/PRD:** выбранный день в карточке — текст primary + Bold + точка, а не красная заливка (мелкий белый текст на #FF4D4F = 3.27:1, PRD §4.2 ¹); в карточке `DayPillsRow` (read-only), а не `DayChipsRow`. **В T11/T12:** мемоизировать `List<DayPillUi>` (нестабильный параметр → лишние рекомпозиции карточек). **В T-docs:** скилл `alarmy-ui` (бары звонка видимы; `DayPillsRow`; иконки; `captionStrong`), `.claude/agents/ui-developer.md:26`, `verify-alarm-reliability` (подсказка «Viewing full screen» больше не появляется), PRD §4; при светлой теме (бэклог) стиль иконок системных баров звонка (`SystemBarStyle.dark`) выбирать по теме; для экранов миссий (M5) решение «скрытые бары» — отдельное.
- **T09 (ui-developer, ревью ⚠️ → исправлено):** `TimeWheelPicker(hour, minute, is24Hour, onTimeChange, …)` — управляемый компонент: `onTimeChange` вызывается по мере прохождения центра (не после остановки), внешняя смена значения — без haptic/колбэка и **только после остановки прокрутки** (эхо устаревшего значения от ViewModel откатывало бы колесо посреди fling); `WheelColumnState` не зависит от `rowHeightPx` в ключах `remember`. Удалён мёртвый `to24h`. Haptic — `SegmentFrequentTick`. Строки компонента передаёт вызывающий (подписи колонок, AM/PM, действия «больше/меньше»). **В T15:** центрировать компонент снаружи (Row не центрируется в `BoxWithConstraints`); кнопка «клавиатура» → M3 `TimeInput` не сделана (по желанию — бэклог). **В смок 3:** прокрутка плавная на реальном `ViewModel`, быстрый fling без дрожания/отката, haptic на шаг. **Nit (низкий приоритет):** `centered` читается в теле `WheelColumn` → рекомпозиция колонки на каждом шаге (можно перенести чтение внутрь `semantics {}`); при двух одновременных жестах и асинхронном родителе `onTimeChange` может отправить старую минуту — оговорить в KDoc при использовании в T14/T15.

- **T10 (ui-developer, ревью ⚠️ → исправлено):** `DayChipsRow(days: List<DayChipUi>, onToggle, selectedDescription, notSelectedDescription)`, `PresetChips` + чистый `DayPreset.matches/toggle` (тип дня — `java.time.DayOfWeek`, :core:model не нужен), `SettingRow(title, value, onClick, icon?, onClickLabel?, contentDescription?)`, `SingleChoiceDialog` (тап по варианту сразу `onSelect(index)`, закрывает вызывающий; `selectedIndex` вне диапазона = ничего не выбрано), `LabelField(value, onValueChange, label, maxLength, counterDescription?)` + `labelLength`. Метка: переводы строк (LF/CR/CRLF/NEL/U+2028/2029) → пробел; при превышении лимита обрезается вставленный фрагмент, а не хвост; курсор — после принятой части (`TextFieldValue`). **Отклонения от скилла/PRD:** выбранный пресет — обводка 2dp primary + текст primary, а не красная заливка (контраст 16sp на primary 3.27:1); чипы дней — заливка primary, но текст 19sp Bold и не мельче 19sp при любом fontScale; «Выходные» = Сб+Вс во всех локалях; у `SettingRow` иконка необязательна. **В T15:** (1) строке `DayChipsRow` дать горизонтальный отступ **≤ 12dp** на 360dp (ячейка = 1/7 ширины; при `ScreenPadding` 20dp ≈ 45,7dp < 48dp), (2) строки RU+EN: подписи пресетов, «Выбрано/Не выбрано», plurals описания счётчика метки («12 из 40 символов»), «Отмена», заголовки диалогов snooze, `onClickLabel` для `SettingRow`, (3) после `onSelect` диалог закрывает ViewModel, (4) тег вызывающего перекрывает внутренний тег `SettingRowTestTags.ROW`. **В T14:** `LabelChanged` приходит уже очищенным; reducer обрезает только пробелы по краям; Room-маппер всё равно защищать от `\n` (nit T04/T05); `DayPreset` можно импортировать из designsystem (feature → designsystem зависеть можно). **Ограничение `LabelField`:** при внешней смене `value` выделение сохраняется (обрезается по длине), composition сбрасывается; при асинхронном ViewModel и двух событиях IME до рекомпозиции второе считается от устаревшего текста (так же у `String`-перегрузки). **В T-docs:** скилл `alarmy-ui` — `DayChipsRow` (без иконки дня, 19sp Bold на primary), обводка выбранного `PresetChips`, `SingleChoiceDialog`, `LabelField` (счётчик в code points). **Смок 3:** визуально проверить подпись `LabelField` (всплывшая подпись в вырезе рамки), перенос `SettingRow` при fontScale 2f.

- **T11 (ревью ⚠️ → исправлено):** `AlarmListViewModel(repository, engine, clock)`; `AlarmListUiState(loading, alarms: List<AlarmItemUi>, nextIn: TimeUntil?)` + `isEmpty`; `AlarmItemUi(id, time, label, repeatDays, active, subtitle)`; `AlarmSubtitle` = `Today | Tomorrow | SnoozedUntil(time)` (дальше завтра — `null`, дни видны в `DayPillsRow`). Состояние без строк и локали — время, дни и «через …» форматирует экран через `:core:format` (**в T12 добавить `implementation(projects.core.format)`** и строки шапки). **Отклонения от плана:** `Open`/`Add` — не события ViewModel, а колбэки Route (ADR-009: навигация — дело Route); события только `Toggle(id, enabled)` (UI передаёт `!active`) и `Delete(id)`. Эффекты (`effects: Flow`, Channel + `receiveAsFlow`): `RingsIn(TimeUntil)`, `ScheduleFailed` (отказ системы или сбой **включения**), `DisableFailed` (сбой выключения — будильник остался включённым, текст «Не удалось запланировать» тут врал бы), `DeleteFailed`. Мутации идут в `withContext(NonCancellable)` — тап за миг до закрытия экрана не теряется. **В T12:** собирать `effects` одним сборщиком (`receiveAsFlow` делит элементы между сборщиками); `RingsIn` считается в момент тапа — при возврате на экран тост может показать устаревшее время (допустимо); строки RU+EN для `ScheduleFailed/DisableFailed/DeleteFailed`; мемоизировать `List<DayPillUi>` из `repeatDays` (`remember(item.repeatDays, weekdayFormat)`); после остановки `WhileSubscribed` состояние устаревшее до первого пересчёта — проверить мигание на экране. **Тесты (22):** реальный движок на фейках; NFR-9 (после отмены подписки часы не читаются), полночь (Tomorrow→Today), смена зоны на тике, точный `now` (не усечённая минута). Мутация `Eagerly` вместо `WhileSubscribed` не даёт чистого падения теста NFR-9, а вешает прогон (бесконечный тик в `viewModelScope`) — регрессия не пройдёт незамеченной, но выглядеть будет как зависание. **Известно (граница движка, не T11):** при исключении в `setEnabled(true)` после записи в БД остаётся «вкл», в `AlarmManager` ничего нет — следующий `rescheduleAll` исправит; причина сбоя пользователю не показывается и в лог не пишется (ViewModel не зависит от `AlarmEventLog`).

- **T12 (ui-developer, ревью ⚠️ → исправлено):** `AlarmListRoute(onAddAlarm, onOpenAlarm, modifier, viewModel = hiltViewModel())` + тонкий `AlarmListScreen(state, onEvent, onAddAlarm, onOpenAlarm, modifier)` (internal-перегрузка с явными `ClockFormat/WeekdayFormat` — для превью и тестов), `AlarmListTestTags` (`ROOT/LIST/FAB/EMPTY/MENU_DELETE/card(id)`), чистые `alarmCardTexts/dayPills` (`AlarmCardTexts.kt`), `AlarmListEffectsHandler` + `alarmListEffectText`. Экран лежит в корневом пакете модуля (не `ui/`, как у ringing). Решения: шапка — первый элемент списка (не закреплена); во время `loading` нет ни шапки, ни «пусто»; долгий тап → `DropdownMenu` «Удалить» → `ConfirmDialog` (подпись действия для TalkBack — «Показать действия»); `menuFor/deleteFor` в `rememberSaveable`, диалог исчезает вместе с будильником, очистка только при `!loading`; тост — один за раз (`cancel()` предыдущего), через `applicationContext`; повтор в TalkBack: однократно / каждый день / по будням / по выходным / полные названия (наборы дней — `DayPreset` из designsystem); `onToggle` игнорирует значение и шлёт `!item.active`. **Контракт insets (в KDoc Route, в T13 учесть):** экран сам обрабатывает `safeDrawing` (верх и низ — в `contentPadding`, бока — `windowInsetsPadding`); `:app` **не** оборачивает его в `Scaffold`/`padding(innerPadding)` — иначе отступы удвоятся. В T13: `AlarmListRoute(onAddAlarm = { backStack += AlarmEditKey(null) }, onOpenAlarm = { backStack += AlarmEditKey(it.value) })`, `NavDisplay` с декоратором ViewModelStore (иначе `hiltViewModel()` живёт на уровне Activity), `:app` зависит от `:feature:alarmlist`, тест навигации ищет FAB по `AlarmListTestTags.FAB`. **Тесты (58 в модуле):** сквозной `AlarmListRouteTest` (реальные ViewModel+движок: тумблер не оптимистичный, тост после записи, удаление), восстановление меню/диалога (`StateRestorationTester`), исчезновение будильника при открытом диалоге, FAB не перекрывает последнюю карточку при fontScale 2f/360dp, 12h/24h через `Settings.System.TIME_12_24`, RU/EN. Что тост создан на `applicationContext`, Robolectric не проверяет (`ShadowToast` контекст не хранит) — только код-ревью. **Не сделано (осознанно):** единый `DropdownMenu` на экран (по одному на карточку), `PARTS_SEPARATOR` — не в ресурсах (одинаков для RU/EN; при новых локалях вынести). **В смок 2/3:** визуально — шапка и карточки под прозрачным статус-баром при прокрутке, FAB над навигационной панелью, меню, fontScale 2f, мигание состояния после возврата из фона >5 с. **В T-docs:** скилл `alarmy-ui` — пустое состояние и меню долгого тапа.

- **T13 (ревью ⚠️ → исправлено; смок 2 tester'ом):** Nav3 1.2.0 + `lifecycle-viewmodel-navigation3`, граф только в `:app` (`BalarmApp.kt`, `BalarmKeys.kt`: `AlarmListKey`, `AlarmEditKey(alarmId: Long?)`, ключи `internal`), `NavDisplay` с декораторами SaveableStateHolder и ViewModelStore, `onBack = removeLastOrNull`; `rememberNavBackStack(NavConfiguration, AlarmListKey)` — **подтипы `NavKey` нужно регистрировать в `NavConfiguration`** (иначе падает при сохранении стека; тест `NavKeysTest` проверяет полноту по дескриптору sealed-интерфейса — **при новом ключе (M3: онбординг, настройки) регистрировать и там**). Защита от двойного тапа: `openEditor` не кладёт второй редактор (`NavKeysTest`). Редактор — заглушка `AlarmEditStub` (строки `edit_stub_*`) → **T15 заменяет на `AlarmEditRoute` и удаляет заглушку, её строки и теги**; `AlarmEditKey` восстанавливается после смерти процесса — если будильник уже удалён, редактор должен закрыться, а не показать пустую форму. Удалены `PlaceholderScreen/TestTags/Test`; из `:app` убраны лишние `core.format` и `hilt-lifecycle-viewmodel-compose` (feature-модули подключают сами). **Тесты `:app` (27):** навигация (FAB↔Back, карточка → редактор с id, Back на корне закрывает приложение, пересоздание Activity с `id` и без), `NavKeysTest` (регистрация проверена мутацией). **Смок 2 (tester, API 37):** тумблер ↔ `dumpsys` ✅ (выкл — интента нет, вкл — есть, тост), R5 ✅ (`RESCHEDULE_ALL TIMEZONE_CHANGED`, шапка и «Today/Tomorrow» обновились ≈ 2 с), навигация/удаление ✅, Back на корне ✅, визуально ✅ после фиксов. **Дефекты смока (исправлены в этой же задаче):** BUG-1 — подписи дней обрезались при fontScale 2 («Mon»→«Mor»): тест T08 проверял двухбуквенные «Mo/Пн», а `:core:format` даёт трёхбуквенные; строка дней стояла в узкой колонке с зазорами и порогом `10sp`. Теперь: дни отдельной строкой во всю ширину карточки (под строкой «время + тумблер»), 7 равных ячеек, общий размер подписи по самой широкой (`fitDayLabelFontSize`, `TextMeasurer`, минимум `DayPillLabelMinSize = 10dp` — **заменил `DayPillLabelMinFontSize` из записи T08**), LRU-кэш подгонки на весь список, `DayPillsRowMaxWidth` 336dp, реальные подписи в превью и тесте (проверено: тест падает на старом коде). BUG-2 — карточки при прокрутке шли под прозрачный статус-бар без подложки: новый компонент `SystemBarScrim` (градиент `background`: сплошной под баром → прозрачный, `SystemBarScrimFade = 16dp`, касания не перехватывает, нет в дереве доступности), подложки сверху и снизу в списке; тесты: пиксельный, геометрия, касание сквозь подложку к карточке (проверено мутацией). **Наблюдения tester'а (не дефекты, в T-test/T-docs):** (1) при выключении тумблера движок не пишет `CANCELLED` (при удалении пишет) — согласовать лог или поправить скилл `verify-alarm-reliability`; (2) после `force-stop` приходят отложенные `LOCKED_BOOT/BOOT` — три `RESCHEDULE_ALL` подряд, дубликатов в `dumpsys` нет; для R3/R4 force-stop ≠ холодный старт после kill; (3) TalkBack-описание шапки «3 hours» при визуальном «3 hr, 1 min» — вероятно, граница минуты (проверить в T-test); (4) список сортируется по времени суток, а не по ближайшему срабатыванию (решение ADR-011 §1); (5) диалог удаления без метки («Alarm 11:41 PM will be deleted.») — бэклог; (6) метка без пробелов переносится посреди слова — бэклог; (7) не покрыто: RU-локаль на устройстве, predictive back жестом, поворот, пересоздание процесса — T15/T-test. **В T-docs:** скилл `alarmy-ui` — карточка (дни отдельной строкой, трёхбуквенные подписи), `SystemBarScrim`, пустое состояние и меню долгого тапа; ADR-009 §2 обновлён здесь (`NavConfiguration`).

- **T14 (ревью ⚠️ → исправлено):** `AlarmEditViewModel` (`@HiltViewModel(assistedFactory = Factory::class)`, **assisted-параметр `Long?`**, не `AlarmId?` — value class с Dagger/KSP хрупок; в T15: `hiltViewModel<AlarmEditViewModel, AlarmEditViewModel.Factory>(creationCallback = { it.create(alarmId?.value) })`, **Hilt-граф проверит только сборка `:app` — в T15 нужен smoke на создание ViewModel**), `AlarmEditUiState(loading, isNew, initial, draft: Alarm, snoozeVisible, saving, dialog)` + `isDirty/canDelete`, чистый `AlarmEditReducer`, `EditDialog = SnoozeInterval | SnoozeLimit | ConfirmDelete | ConfirmDiscard`. **События:** `TimeChanged(LocalTime)`, `DayToggled`, `PresetSelected(DayPreset)`, `LabelChanged`, `SnoozeIntervalSelected(Duration?)` (`null` = выкл), `SnoozeLimitSelected(Int?)` (`null` = ∞), `ShowSnoozeIntervalDialog/ShowSnoozeLimitDialog` (добавлены: диалоги в состоянии), `Save`, `Test`, `Delete` (открывает подтверждение), `ConfirmDelete`, `Back`, `DiscardConfirmed`, `DialogDismissed`. **Эффекты** (одним сборщиком): `Saved(result, until: TimeUntil?)` (`until == null` при отказе системы → «Не удалось запланировать»; экран: тост + закрыть), `TestScheduled(at: Instant?)` (`null` — отказ), `SaveFailed`, `DeleteFailed`, `LoadFailed` (тост + закрыть), `Close`. **Отклонения от плана:** (1) «Сохранить» вызывает `engine.save` **всегда**, даже без правок: для нового будильника это создание, для выключенного — включение (ADR-011 §5 «Сохранить всегда включает»), для включённого движок идемпотентен (snooze/`lastFiredAt` сохраняются, есть тест), тост подтверждает результат — формулировка плана «без изменений просто закрывает» противоречила ADR; (2) `LoadFailed` и закрытие редактора, если будильник уже удалён (`Close`), — по замечаниям ревью T13. **Гейты (ревью):** `closing` — после `Close/Saved` события игнорируются (двойной Back даёт один `Close`), `Back/DiscardConfirmed` игнорируются во время `saving` (экран не закроется раньше тоста), `ConfirmDelete` только из открытого диалога и не поверх сохранения (двойной тап удаляет один раз), `Delete` не открывается при `saving`. **Решения:** snooze: `interval = null` выключает, сохраняя лимит; включение из `DISABLED` (лимит `null`) берёт `DEFAULT.maxCount`, а не ∞ — следствие: «∞ → выкл → вкл» даёт лимит по умолчанию; при `feature.snooze=false` события/диалоги snooze не действуют, `alarm.snooze` не меняется; метка: переводы строк (LF/CR/CRLF/NEL/U+2028/2029) → пробел, `takeCodePoints(40)`, `trim` при сохранении; «Тест» звонит снимком в форме сохранения (`enabled = true`, метка без пробелов по краям), БД не трогает. `isDirty` сравнивает метку без `trim` — «abc» → «abc » считается правкой (принято). **Тесты (48):** reducer (19), ViewModel (29) на реальном движке с фейками; мутацией проверено — без `NonCancellable` в `save` падает `save waiting for a busy engine survives leaving the screen` (движок занят `rescheduleAll`, экран закрывают в очереди; без занятого мьютекса тест ничего бы не различал — движок сам держит `NonCancellable` после захвата). **Для T15:** добавить `:core:format` в зависимости модуля (`rememberClockFormat`, `alarmRingsInText`, `rememberWeekdayFormat`); на `Saved` показывать тост через `applicationContext`; **`DayPreset` лежит в `:core:designsystem` (T10) и попадает в событие `PresetSelected`** — приемлемо (feature → designsystem разрешён), либо заменить событием `DaysSelected(Set<DayOfWeek>)`; Back — `BackHandler(enabled = state.isDirty)` + `onEvent(Back)`; при `Close`/`Saved` вызывать `onClose` один раз.

## Уроки

## Перенесено в следующий этап
* **M3:** нижняя навигация и экран настроек (второй стек Nav3), баннер FR-LIST-5, «Тестовый будильник через 1 минуту» на `TestAlarmRunner`; из M1: `PermissionHealthChecker` (FSI, уведомления, DND, громкость 0), онбординг, R20 (флаг «звонит» в runtime, схема v2).

- **T15:** смок 3 на эмуляторе API 37 пройден: будильник +2 мин из UI → звонок (`RINGING_STARTED`) → «Отключить»; «Тест» звонит через 5,05 с и не попадает в БД; Back с правками (кнопка и **реальный жест predictive back**) → диалог, `BackHandler` приоритетнее `NavDisplay` — `NavigationBackHandler` не понадобился; без правок — закрытие с predictive-анимацией; тост «зазвонит через …» виден после закрытия редактора; скриншоты (колесо, fontScale 2f, RU) без дефектов функциональности. **Ревью T15 (⚠️ → исправлено):** `onClose` снимает экран только с вершины-редактора; `Test` игнорируется при `saving`; тест тоста проверяет префикс (и разрешает точные будильники в Robolectric); нижний отступ контента учитывает градиент над «Сохранить» (иначе «Тест» перекрыт до прокрутки). **Известно:** (1) `DayChipsRow` при fontScale 2f — autosize подбирается на каждый чип отдельно, кегль подписей неравномерный (компромисс T10 ради контраста WCAG) — кандидат на правку в T-test/M3; (2) при `scheduled=false` редактор закрывается с тостом «Не удалось запланировать» (решение T14) — покрыть экраном «Надёжность» в M3; (3) в ADR-009 §4 отразить `BackHandler(enabled = isDirty || saving)`; (4) heads-up «Тест» показывает время снимка, а не пометку «Тест» (косметика).
