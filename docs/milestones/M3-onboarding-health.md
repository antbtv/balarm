# M3: Онбординг и здоровье будильника

| | |
|---|---|
| Статус | In progress |
| Утверждён | 2026-10-07 |
| Завершён | — |
| Требования | FR-LIST-5, FR-REL-7, FR-REL-5 (путь overlay), §3.7 (онбординг), §3.9 (минимум), FR-FLAG-5 (вход в debug-экран), NFR-2; перенесено из M2 (ADR-011 §11 Б) |
| Definition of Done (PRD §8) | Все статусы разрешений/здоровья корректны на API 34 и 37 (переключение через adb); онбординг проходится, баннер появляется при отзыве критичного и ведёт на экран здоровья; тестовый будильник через 1 мин звонит. Сценарии R12, R10, R9, R3, R1 (API 34) |

## Цель
Пользователь при первом запуске проходит онбординг (7 шагов), видит в списке баннер, если будильник может не сработать (отозвано разрешение или планирование не удалось), и открывает вкладку «Настройки» → «Здоровье будильника»: статусы ✅/⚠️ с кнопкой «Исправить» и тестовый будильник через 1 минуту. Появляется нижняя навигация (Будильники / Настройки).

Вне скоупа M3: OEM-интенты автозапуска (бэклог), звук/громкость (M4), миссии (M5), R20 (→ M5), полноценные настройки (язык, автостоп и т. д.), baseline profile и общий Kover (→ M8), возврат экрана звонка наверх после Home (FR-RING-5 — M5).

## Архитектура этапа
Резюме architect (детали — ADR-012…015):
* `:core:domain/health` — `HealthItem` (+severity), `HealthStatus{OK,PROBLEM,UNCONFIRMED}`, `PermissionSnapshot`, чистая `healthReport(snapshot, setup, unscheduledAlarms)`, интерфейсы `PermissionHealthChecker`, `SetupStateRepository`; `AlarmDefaults.testAlarm`, `TestAlarmRunner.HEALTH_DELAY = 1 мин`, try/catch внутри `TestAlarmRunner.schedule`.
* `:core:data` — `DataStoreSetupStateRepository` (DataStore на device-protected, `app_prefs`); схема Room v2: `alarm_runtime.schedule_failed` (AutoMigration 1→2).
* `:core:permissions` (новый) — `AndroidPermissionHealthChecker`, `rememberHealthFixLauncher`, тексты пунктов RU/EN.
* `:core:alarm` — `SYSTEM_ALERT_WINDOW` и путь `startActivity(RingingActivity)` при `canDrawOverlays`.
* `:feature:onboarding` (новый), `:feature:settings` (новый: настройки, здоровье, «О приложении»), `:feature:alarmlist` — баннер, `Resumed` → новый снимок.
* `:app` — два стека Nav3 (`BalarmNavState`, вкладки ALARMS/SETTINGS), ключи `SettingsKey/HealthKey/AboutKey/OnboardingKey` в `NavConfiguration`, splash до чтения `SetupState`, удаление временного запроса `POST_NOTIFICATIONS` из `MainActivity`.
* `:core:designsystem` — `HealthBanner`, строка статуса, раскладка шага онбординга, `BalarmNavigationBar`, иконки.
* Новых feature-флагов нет: онбординг/здоровье — ядро надёжности (FR-FLAG-4).

## Решения на утверждение
- [ ] ADR-012: модель здоровья будильника (Proposed)
- [ ] ADR-013: онбординг (Proposed)
- [ ] ADR-014: нижняя навигация и настройки (Proposed)
- [ ] ADR-015: состояние отказа планирования, схема v2; R20 → M5 (Proposed)

Вопросы к пользователю (рекомендации architect приняты в плане по умолчанию):
1. Баннер — только для пунктов, ломающих звонок (рек.). Overlay и оптимизация батареи — RECOMMENDED, без баннера, только в экране здоровья. *(правка PRD §3.7/FR-LIST-5)*
2. Overlay-путь старта экрана звонка — в M3 (рек.); возврат наверх после Home — M5.
3. Отказ планирования — флаг в схеме Room v2 (рек.).
4. R20 → M5.
5. Строка «Язык» в настройках — **не** в M3 (рек.), если не скажете иначе.
6. Шаг OEM — показывать на всех устройствах (рек.: проще и тестируемо).
7. PRD §3.7: у критичного шага «Продолжить без этого» после первой попытки (иначе отказавший застревает); новый критичный пункт BACKGROUND_RESTRICTION.

## Задачи
| ✓ | ID | Задача | FR | Оценка | Зависит от | Commit |
|---|---|---|---|---|---|---|
| [x] | M3-T01 | Домен: модель здоровья, `healthReport`, интерфейсы, `testAlarm`, try/catch в runner | FR-REL-7 | M | — | |
| [x] | M3-T02 🔔 | Схема v2 `schedule_failed`, движок пишет отказ планирования | FR-LIST-5 | M | T01 | |
| [x] | M3-T03 | `SetupStateRepository` на DataStore (device-protected) | §3.7 | S | T01 | |
| [x] | M3-T04 🔔 | `:core:permissions`: чекер, манифесты, лаунчер «Исправить» | FR-REL-7 | L | T01 | |
| [x] | M3-T05 🔔 | Overlay-путь старта `RingingActivity` из `RingingService` | FR-REL-5 | M | T04 | |
| [x] | M3-T06 🎨 | Компоненты дизайн-системы: баннер, строка статуса, шаг онбординга, nav bar | §4.2 | M | — | |
| [x] | M3-T07 | Логика настроек/здоровья (`HealthViewModel`, retry, тест-будильник, OEM-чекбокс) | FR-REL-7 | M | T02, T03, T04 | |
| [x] | M3-T08 🎨 | Экраны настроек, здоровья, «О приложении» (7 тапов → debug flags) | FR-REL-7, FR-FLAG-5 | M | T06, T07 | |
| [x] | M3-T09 | Логика онбординга (`OnboardingViewModel`, вычисление шага, пропуск) | §3.7 | M | T03, T04 | |
| [x] | M3-T10 🎨 | Экраны онбординга (7 шагов, возврат из настроек) | §3.7 | L | T06, T09 | |
| [x] | M3-T11 | Баннер в списке: `healthWarning`, `Resumed`, `NotScheduled` | FR-LIST-5 | M | T02, T04, T06 | |
| [x] | M3-T12 🎨 | `:app`: вкладки/два стека Nav3, стартовый экран, splash, смок | §4.1 | L | T08, T10, T11 | |
| [x] | M3-T-test | Тестирование этапа (tester) + R12, R10, R9, R3, R1(API 34) | §9.2 | L | T12 | |
| [ ] | M3-T-review | Ревью этапа (reviewer) | — | M | T-test | |
| [ ] | M3-T-docs | PRD/ADR/CLAUDE.md, реестр флагов, итоги | — | S | T-review | |

### M3-T01 — Домен здоровья
**Описание:** `:core:domain/health`: `HealthItem` (NOTIFICATIONS, EXACT_ALARMS, FULL_SCREEN_INTENT, OVERLAY, BATTERY_OPTIMIZATION, BACKGROUND_RESTRICTION, OEM_BACKGROUND, DO_NOT_DISTURB, ALARM_VOLUME, SCHEDULING) с severity; `HealthStatus`, `PermissionSnapshot`, `HealthReport.needsAttention`; чистая `healthReport(...)`; интерфейсы `PermissionHealthChecker`, `SetupStateRepository`/`SetupState`; `AlarmDefaults.testAlarm`, `TestAlarmRunner.HEALTH_DELAY`; try/catch в `TestAlarmRunner.schedule` (перенос M2).
**Модули:** `:core:domain`, `:core:model`
**Критерии приёмки:**
- [x] Без Android-зависимостей; `healthReport` — чистая функция
- [x] Исключение планировщика в `schedule` → результат «отказ», не падение
- [x] Kover `:core:domain` ≥ 80 %
**Тесты:** unit: таблица snapshot → report (все severity, UNCONFIRMED), runner с бросающим планировщиком.

### M3-T02 🔔 — Схема v2: `schedule_failed`
**Описание:** колонка `alarm_runtime.schedule_failed`, AutoMigration 1→2, экспорт схемы; `AlarmEngine` пишет/сбрасывает флаг при результате планирования; `upcomingTrigger`/шапка пропускают `scheduleFailed` (ADR-015, закрывает ADR-011 §11 Б).
**Модули:** `:core:data`, `:core:domain`
**Критерии приёмки:**
- [x] Миграция v1→v2 сохраняет данные (`MigrationTestHelper`)
- [x] Отказ `setAlarmClock` → флаг true; успешный `rescheduleAll` → false
- [x] Включённый будильник с флагом не попадает в «Следующий через»
**Тесты:** unit на фейках; instrumented/Robolectric миграции; сценарий R9 (обновление поверх v1).

### M3-T03 — Состояние онбординга
**Описание:** `DataStoreSetupStateRepository` на `@DeviceProtected`, `app_prefs`; Hilt-модуль.
**Критерии приёмки:**
- [x] `completeOnboarding`/`setOemBackgroundConfirmed` переживают перезапуск процесса
- [x] Чтение работает в Direct Boot (контекст `@DeviceProtected`; проверка на эмуляторе — T-test, R3)
**Тесты:** Robolectric/unit с `TemporaryFolder`.

### M3-T04 🔔 — `:core:permissions`
**Описание:** модуль, `AndroidPermissionHealthChecker` (уведомления + канал `alarm_ringing`, exact alarm, FSI, overlay, батарея, background restriction, DND, громкость будильника), манифест (`SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), `rememberHealthFixLauncher` (runtime-запрос / системные настройки / App details как запасной), тексты RU+EN.
**Модули:** `:core:permissions`, `:core:alarm` (id канала), `:app` (манифест)
**Критерии приёмки:**
- [x] Каждый пункт читает свой платформенный API (PRD §3.7)
- [x] Нет обратной зависимости `:core:alarm → :core:permissions`
- [x] Для каждого пункта есть интент «Исправить» с запасным вариантом
**Тесты:** Robolectric (shadow-менеджеры); ручная проверка на API 34/37 — T-test.

### M3-T05 🔔 — Overlay-путь звонка
**Описание:** при `canDrawOverlays` `RingingService` дублирующе вызывает `startActivity(RingingActivity)` (FR-REL-5); защита от двойного старта (`singleTask`/`onNewIntent`).
**Критерии приёмки:**
- [x] Без разрешения поведение прежнее (FSI)
- [x] С разрешением поверх стороннего приложения — один экран звонка, не два (Robolectric; живой R10 — T-test)
**Тесты:** Robolectric на сервис; **R10, R1**.

### M3-T06 🎨 — Компоненты дизайн-системы
`HealthBanner`, строка статуса ✅/⚠️ с «Исправить», раскладка шага онбординга, `BalarmNavigationBar`, иконки (CC0). Токены только из `:core:designsystem`, превью, fontScale 2f/360dp.
**Тесты:** Compose UI, semantics (TalkBack-описания).

### M3-T07 — Логика настроек и здоровья
`HealthViewModel`: отчёт из чекера + `SetupState` + число не запланированных; «Исправить», «Повторить планирование» (`rescheduleAll(USER_RETRY)`), тестовый будильник 1 мин, OEM-чекбокс; пересчёт на `Resumed`.
**Критерии приёмки:** [x] immutable `UiState`, события одноразовые через effects; [x] ошибка тест-будильника → понятное сообщение.
**Тесты:** Turbine + MockK.

### M3-T08 🎨 — Экраны настроек
`SettingsRoute` (строки «Здоровье будильника», «О приложении»), `HealthRoute`, `AboutRoute` (версия, 7 тапов → `FeatureFlagsActivity` только в debug).
**Тесты:** Compose UI; смок на эмуляторе.

### M3-T09 — Логика онбординга
`OnboardingViewModel`: шаг = первый не-OK и не пропущенный; `skipped/attempted` в `SavedStateHandle`; «Продолжить без этого» после первой попытки; уже выданные шаги пропускаются (шаг 2 на API 34+); завершение → `completeOnboarding`.
**Тесты:** unit-таблица состояний.

### M3-T10 🎨 — Экраны онбординга
7 шагов: иллюстрация, «Зачем это нужно», «Разрешить», «Позже/Продолжить без этого». Возврат из системных настроек и `ON_RESUME` — автопереход. Шаг 6 упрощённый (пояснение + dontkillmyapp.com + App details + чекбокс «Я сделал»).
**Тесты:** Compose UI; возврат на правильный шаг после смерти процесса (отзыв уведомлений).

### M3-T11 — Баннер в списке
`AlarmListUiState.healthWarning`, событие `Resumed` → `snapshot()`, подзаголовок `NotScheduled` на карточке при `scheduleFailed`, `onOpenHealth`. Баннер — только для пунктов, ломающих звонок.
**Тесты:** reducer/ViewModel, Compose UI (баннер виден/скрыт).

### M3-T12 🎨 — Сборка навигации в `:app`
`BalarmNavState` (стек на вкладку; выход — через ALARMS), ключи в `NavConfiguration`, стартовый экран онбординг/список, splash до чтения `SetupState`, нижний inset задаёт `:app`, удалить временный `POST_NOTIFICATIONS` из `MainActivity`. **Смок 4.**
**Критерии приёмки:** [x] `onOpenHealth` подключён; [x] нет двойных/пропавших отступов (fontScale 2f); [x] Back на корне вкладки SETTINGS → ALARMS; [x] `am start -W` до/после (NFR-2).

### M3-T-test — Тестирование этапа (agent: tester)
API 37 (и разово API 34): статусы каждого пункта переключаются через adb (команды — ADR-012 «Consequences»); R12 (основной), R10, R9 (поверх v1), R3, R1 на API 34; тестовый будильник 1 мин; онбординг с нуля; fontScale 2f; живой TalkBack — по желанию. EXACT_ALARMS/SCHEDULING — только Robolectric. Один эмулятор за раз, `./gradlew --stop` до, `adb emu kill` после.

### M3-T-review — Ревью этапа (agent: reviewer)
Весь диф этапа; особое внимание: схема v2, overlay-путь, отсутствие флагов на ядре.

### M3-T-docs — Документация
ADR-012…015 → Accepted; PRD (§3.7 «Продолжить без этого», BACKGROUND_RESTRICTION, severity баннера, §3.9, R20 → M5, §8); CLAUDE.md; заполнить «Уроки/Перенесено».

## Риски
| Риск | Митигация |
|---|---|
| Insets: контракт списка меняется (нижний отступ — у `:app`) | Robolectric fontScale 2f/360dp + визуально на эмуляторе |
| `SavedStateHandle` в записи Nav3 | запасной путь — `rememberSaveable` |
| Отзыв уведомлений убивает процесс | сценарий возврата на правильный шаг в T10/T-test |
| NFR-2: DataStore до первого кадра | splash через `OnPreDrawListener`; замер `am start -W` |
| DND на API 35+ (режимы) | проверка на API 37; пункт INFO, на баннер не влияет |
| Play-ревью `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | запасной `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` |
| Первая миграция Room на AGP 9 | `MigrationTestHelper`, R9 поверх v1 |
| Двойной старт `RingingActivity` (FSI + overlay) | `singleTask`/`onNewIntent`, R10 |
| Нет AVD API 34 | поставить разово, прогнать, удалить |
| Память машины | сборки и эмулятор строго по одному, лимиты Gradle не повышать |

## Добавлено по ходу
- **T01 (ревью ⚠️ → учтено):** в `PermissionSnapshot` добавлено вычисляемое `notificationsReady` (разрешение И канал) — в T-docs привести ADR-012 §1 в соответствие. Исключение планировщика в `TestAlarmRunner.schedule` не логируется отдельно (`ScheduleFailed` без причины) — принято.
- **T02 (ревью ⚠️ → учтено):** принятый успешный snooze при `scheduleFailed` снимает признак (в системе есть snooze-будильник), обычное расписание остаётся не запланированным до следующего `rescheduleAll` — T07/T11 это учитывать; `AlarmSchedulerImpl` сам превращает `SecurityException` в отказ. R9 (поверх v1) — в T-test; `USER_RETRY` добавлен в `RescheduleReason`.
- **T03 (ревью ⚠️ → учтено):** `edit` при `IOException` бросает вызывающему — обработать в ViewModel T07/T09 (не падать на «Я сделал»/завершении онбординга). Direct Boot чтения DataStore — проверить в T-test вместе с R3.
- **T04 (ревью ⚠️ → учтено):** `resolveActivity` заменён на try/catch `ActivityNotFoundException` (package visibility без `<queries>`); отказ POST_NOTIFICATIONS «навсегда» (`shouldShowRequestPermissionRationale == false`) сразу ведёт в настройки, без мёртвого нажатия. `SYSTEM_ALERT_WINDOW` в манифест — в T05 (без него экран overlay не покажет приложение; до T08 обязательно). Ссылка dontkillmyapp.com для OEM — в UI T08/T10. Robolectric не управляет `canScheduleExactAlarms`/`canUseFullScreenIntent` (нет shadow-сеттеров) — эти два пункта проверяются на эмуляторе в T-test (FSI: `appops set … USE_FULL_SCREEN_INTENT deny`); `rememberHealthFixLauncher` без автотеста — проверить на смоках T08/T10.
- **T05 (ревью ⚠️ → учтено):** на Android 15+ исключение `SYSTEM_ALERT_WINDOW` для старта Activity из фона может требовать видимого окна → **в T-test проверить на API 37** (overlay выдан, FSI выключен, экран заблокирован/приложение в фоне); если не работает — оставить FSI основным, формулировку в PRD/UI не обещает гарантий. Проверить очередь из двух будильников (второй экран появляется после dismiss первого). `SYSTEM_ALERT_WINDOW` — пометка для Play-деклараций (M8).
- **T06 (ревью ✅):** добавлен токен `warningContainer`; `BalarmNavigationBar(items: List<NavBarItem>)` — в `:app` (T12) делать `remember` списка; контраст иконок статусов в светлой теме < 3:1 (светлая тема — бэклог, форма иконки дублирует смысл) — записать в бэклог в T-docs; в T-docs обновить таблицу компонентов скилла `alarmy-ui` (`PermissionBanner` → `HealthBanner`, + `HealthStatusRow`, `OnboardingStepLayout`, `BalarmNavigationBar`). Перевод `HealthStatus` → `HealthStatusUi` — в feature-модулях (T08/T11).
- **T07 (ревью ⚠️ → учтено):** `healthReportFlow` в `:core:domain` (общий для списка/настроек/здоровья/онбординга; сбой потока будильников → 0 незапланированных, экран не падает); `HealthUiState.retrying` — в T08 **блокировать** кнопку «Повторить»; платформенный `snapshot()` вызывается на main — проверить StrictMode на смоке T08/T12; фейки `FakePermissionHealthChecker`/`FakeSetupStateRepository`/`HEALTHY_SNAPSHOT` — в testFixtures домена (для T09, T11).
- **T09 (ревью ⚠️ → учтено):** `OnboardingViewModel` принимает ещё и `AlarmRepository` (для `healthReportFlow`) — привести ADR-013 §4 в T-docs. Чек-бокс «Я сделал» держит экран (T10): `OemConfirmed(true)` шлётся по «Продолжить», иначе шаг исчез бы при отметке. `stepNumber = ordinal+1` из 7 — при пропуске выполненных шагов номер скачет (T10: показать позицию среди оставшихся или принять). `SaveFailed` в T10 — не блокирующее сообщение; `Finished` приходит и после сбоя записи (онбординг покажется снова).
- **T11 (ревью ⚠️ → учтено):** `onOpenHealth` обязателен на всех перегрузках `AlarmListScreen`; в `BalarmApp` пока `{}` → **обязательно подключить в T12**. Живой TalkBack баннера (live region) — T-test.
- **T08 (ревью ⚠️ → учтено):** нижний отступ вкладок — через `modifier` из `:app`: `Scaffold(bottomBar, contentWindowInsets = WindowInsets(0)) { innerPadding -> Route(modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) }`; здоровье/«О приложении» — без панели, сами обрабатывают safeDrawing; `onOpenDebugFlags` не-null только из debug source set. Системный Back экраны не перехватывают (predictive back). В T12/T-test: `rememberHealthFixLauncher` на устройстве (OEM → App details), StrictMode `snapshot()`, ссылка dontkillmyapp в браузере. В T-docs: `BalarmTopBar` в таблицу скилла `alarmy-ui`.
- **T10 (ревью ⚠️ → принято):** `Resumed` приходит дважды при возврате (callback лаунчера + ON_RESUME) — идемпотентно, `Finished` один раз по построению; путь «браузера нет» без автотеста; индикатор — ordinal из 7 (скачок при пропуске). В `OnboardingStepLayout` добавлены `primaryEnabled`, `warning` — в T-docs обновить таблицу `alarmy-ui`. Проверить в T12/T-test: `rememberHealthFixLauncher` и ссылка на устройстве.
- **T12 (ревью ⚠️ → учтено):** панель — внутри записей корней (`TabRoot`), а не вокруг `NavDisplay` (иначе FAB прыгает при переходе); `contentKey` с префиксом стека; `openEditor` срабатывает только со списка (отразить в ADR-009 §2 в T-docs); `AppViewModel` читает `SetupState` с таймаутом 2 с (→ онбординг); debug-вход через `@BindsOptionalOf DebugTools`. **Смок на эмуляторе (T-test):** FAB/scrim над панелью при fontScale 2 и реальной системной навигации (жесты/3 кнопки); predictive back между вкладками и со здоровья (fade vs slide — Nav3 берёт `transitionSpec` у входящей или снимаемой записи?); `am kill` на вкладке настроек; NFR-2 `am start -W`; debug 7 тапов; StrictMode `snapshot()`.
- **T-test (HEAD 658bfef, эмулятор API 37):** вердикт — **critical/major нет**; 2 minor + 3 наблюдения.
  * Unit: `testDebugUnitTest lint detekt ktlintCheck` — BUILD SUCCESSFUL, 823 теста, 0 падений/пропусков; Kover `:core:domain` 84,5 % (≥ 80). Release подписан только debug-ключом вручную (`app-release-unsigned.apk` → zipalign + apksigner) — для замеров.
  * Онбординг (А): чистая установка сразу онбординг (покадровая запись: splash → онбординг, списка нет); диалог уведомлений; «не разрешить» → предупреждение + «Продолжить без этого» только после попытки; повторный «Разрешить» сразу в настройки; возврат из настроек/overlay/батареи → авто-переход; шаги 2 (exact) и 3 (FSI, если разрешён) пропускаются; «Позже» у рекомендуемых сразу; FSI deny → «Продолжить без этого» после попытки → список с баннером; завершение → вкладки, Back выходит из приложения; повторный запуск без онбординга; force-stop посреди онбординга → тот же шаг. Обновление M2→M3 поверх: онбординг показан один раз.
  * Статусы здоровья (Б), ожидание = факт во всех строках:

| Пункт (воздействие adb) | Экран «Здоровье» | Настройки | Баннер списка |
|---|---|---|---|
| POST_NOTIFICATIONS revoke | Требует внимания | 1 проблема | есть |
| Канал alarm_ringing importance 2 / 3 / 0 (блок) | Требует внимания (3 тоже: <HIGH) | 1 проблема | есть |
| FSI deny/allow | Требует внимания / OK | 1 проблема / Всё хорошо | есть / нет |
| Overlay deny/allow | Требует внимания / OK | 1 проблема | нет (RECOMMENDED) |
| Whitelist батареи −/+ | Требует внимания / OK | 1 проблема | нет |
| RUN_ANY_IN_BACKGROUND ignore/allow | Требует внимания / OK | 1 проблема | есть |
| DND none (полная тишина) | DND и громкость — Требует внимания | 2 проблемы | нет (INFO) |
| DND alarms / priority | OK | Всё хорошо | нет |
| Громкость STREAM_ALARM | OK (на образе минимум индекса 1, 0 недостижим; «0» достигается только через DND none) | — | нет |
| EXACT_ALARMS, SCHEDULING | только Robolectric (USE_EXACT_ALARM adb не отзывается) | | |

  * «Исправить» (В): FSI → страница FSI приложения; overlay → системный экран «Показ поверх других» (открывается общий список, не страница приложения — minor); батарея → диалог игнорирования; фон и OEM → App details; DND → Modes; громкость → Звук; уведомления/канал → страница канала; возврат пересчитывает статус; dontkillmyapp открывается в Chrome, при отключённом Chrome — тост без падения.
  * Тест-будильник (Г): TEST_SCHEDULED → ALARM_FIRED через 60 с (late 30 мс), RINGING_STARTED, экран звонка есть, «Отложить» нет, `dbg LIST count=0`.
  * Навигация (Е): Back с корня настроек → список, со здоровья/About → настройки, баннер → здоровье → Back → список; панель скрыта на редакторе/здоровье/About; `am kill` на здоровье → восстановление на нём; fontScale 2.0: FAB и scrim над панелью при жестах и при 3 кнопках; debug: 7 быстрых тапов (< 2 с между тапами) → флаги; predictive back About→настройки: масштабирование поверх корня (fade vs slide на корне настроек визуально не различить — зафикс. наблюдение); стеки вкладок по построению не проверяются: панель скрыта на вложенных экранах, переключить вкладку из глубины нельзя.
  * Надёжность (Ж): R1 ✅ (экран выкл, второй звонок), R2 ✅ (deep IDLE, late 39 мс), R3 ✅ (PIN, reboot, LOCKED_BOOT count=1, RingingActivity в RUNNING_LOCKED), R4 ✅ (BOOT без дубликатов), R5 ✅ (Tokyo/Moscow, TIMEZONE_CHANGED, wall-clock сохранён), R8 ✅ (`am kill`, pid сменился), R9 ✅ (v1 из 1569986: 3 будильника, в т.ч. повторяющийся и SNOOZE; `install -r` → PACKAGE_REPLACED count=3, snooze на месте, dumpsys цел, миграция 1→2 без потерь), R10 ✅ (см. ниже), R12 ✅, R13 ✅, R19 ⚠️ известный риск M1 (would be muted, USAGE_ALARM, exemption 4; звук есть; `mHardeningOverride=0`, enforce-команды в образе нет).
  * **R10 на API 37 (вопрос T05):** overlay выдан + FSI отключён — `RingingActivity` открывается поверх Настроек (экран включён) И при выключенном экране (экран просыпается). FSI allow + overlay deny при включённом экране — только heads-up (штатно). FSI deny + overlay deny при выключенном экране — экран не включается, только звук/heads-up (fallback). Уведомления revoked + overlay deny — звук есть, экрана нет (ADR-007 §8), баннер показан. Overlay-старт из сервиса работает.
  * NFR-2 (З): release (debug-ключ), эмулятор x86_64 без GPU: до компиляции 1064/1078/1222/1213/1085 мс; после `speed-profile` 838/788/807/768/800 мс (PRD ≤ 800 мс; M2: 974–1066 до профиля). Регрессии нет, но вердикт на реальном устройстве — M8 (baseline profile). Debug холодный старт 1332–1421 мс.
  * StrictMode (И): в коде приложения StrictMode не включён; `persist.sys.strictmode.visual=1` (adb root) + dropbox `data_app_strictmode` + logcat — нарушений нет, но работу детектора подтвердить не удалось → ⏭ частично; рекомендация: включить StrictMode в debug `Application` (thread policy, penaltyLog).
  * **Дефекты:**
    - [BUG-minor] DataStore `app_prefs` лежит в credential-protected хранилище, хотя создаётся из device-protected контекста: `preferencesDataStoreFile` берёт `applicationContext` (теряет device-protected). Файл: `/data/user/0/<pkg>/files/datastore/app_prefs.preferences_pb`, в `/data/user_de/...` каталога нет. Сейчас не влияет (онбординг-флаг нужен только UI после разблокировки; звонок при LOCKED_BOOT без DataStore — R3 ✅), но настройки звонка (M4: громкость, snooze) в нём будут недоступны до разблокировки → до M4 передать путь через `File(deviceProtectedContext.filesDir, "datastore/app_prefs.preferences_pb")`.
    - [BUG-minor] «Исправить» для overlay открывает общий список «Показ поверх других приложений», а не страницу Balarm (на API 37 `ACTION_MANAGE_OVERLAY_PERMISSION` с `package:` URI) — проверить передачу URI.
  * Наблюдения: после первого отказа POST_NOTIFICATIONS повторное «Разрешить» сразу ведёт в настройки (флаг `notificationsDenied`), хотя система показала бы диалог второй раз — ADR-013 говорит «навсегда»; нумерация шагов скачет при пропусках (принято ранее); нажатие «Тест через 1 минуту» не даёт видимой обратной связи (проверено по `TEST_SCHEDULED` в логе).
  * **Не проверено:** R1 на API 34 (нет образа/диска); отказ планирования ADR-015 (`scheduled=false`) и EXACT_ALARMS/SCHEDULING — только Robolectric; DND priority без категории «будильники»; живой TalkBack баннера; R7 (DST), R18 (OEM) — ручные; CRASH/AUTO не гонялись; различие fade/slide predictive back на корне настроек (нет инструмента покадрового анализа жеста); StrictMode — см. выше.
- **T-test → фикс:** DataStore `app_prefs` лежал в credential-protected `filesDir` (`preferencesDataStoreFile` берёт applicationContext) — исправлено: путь от device-protected контекста + тест `DeviceProtectedDataStoreTest`. Открыто (minor): «Исправить» для overlay открывает общий список, а не страницу Balarm на API 37 (проверить `package:` URI); StrictMode в debug-Application не включён; «Тест через 1 минуту» без видимой обратной связи кроме тоста; R1 на API 34 / отказ планирования / DND-priority / TalkBack — не проверены (см. T-test выше).

## Уроки

## Перенесено в следующий этап
* **M5:** R20 (флаг «звонит» в runtime, схема v3); возврат экрана звонка наверх после Home (FR-RING-5).
* **M8:** baseline profile / вердикт NFR-2 на устройстве; общий Kover; живой TalkBack; `DayChipsRow` при fontScale 2f.
