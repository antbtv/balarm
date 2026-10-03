# M1: Движок будильника

| | |
|---|---|
| Статус | In progress |
| Утверждён | 2026-09-29 (с поправкой: Android 14+, minSdk 34) |
| Завершён | — |
| Требования | FR-RING-1…3, 6, 7 (минимально), FR-REL-1…5, 8; FR-EDIT-7 (snooze — движок); NFR-5, NFR-6, NFR-9 |
| Definition of Done | Будильник звенит в Doze, после `adb reboot` до разблокировки (Direct Boot), после смены TZ; минимальный экран звонка с «Отключить»/«Отложить»; Kover `:core:domain` ≥ 80 %; сценарии R1–R9, R11, R13, R19 пройдены на API 37 и выборочно на API 34 |

## Цель
Надёжное ядро: будильник, созданный (пока через debug-команду), срабатывает точно в срок в любых условиях — Doze, перезагрузка до разблокировки, смена времени/зоны, обновление приложения, падение UI. Полноценного UI списка/редактора нет (M2), миссий нет (M5), мелодий нет (M4 — пока встроенный звук + резервный тон).

## Архитектура этапа
Резюме от architect (детали — ADR-004…008):
```
:app ─► :feature:ringing ─► :core:domain ─► :core:model
  ├───► :core:alarm ───────► :core:domain
  └───► :core:data  ───────► :core:domain
```
* **`:core:domain`** (JVM): `NextTriggerCalculator` (чистая функция; DST-gap → момент перехода 02:30→03:00, overlap → первое наступление), `SystemZoneClock` (зона читается на каждый вызов), `AlarmEngine` — единственная точка изменения состояния под `Mutex` (save/setEnabled/delete/rescheduleAll/onFired/snooze/dismiss), интерфейсы `AlarmRepository`, `AlarmScheduler`, `AlarmEventLog`, `RingingController`.
* **`:core:data`**: Room 2.8.5 в device-protected storage, таблицы `alarm` + `alarm_runtime`, экспорт схем, только аддитивные миграции.
* **`:core:alarm`**: `AlarmSchedulerImpl` (`setAlarmClock`, идентичность PendingIntent через `data=balarm://alarm/<id>`), `AlarmReceiver` → `RingingService` (FGS `systemExempted`, старт до любого I/O), `RescheduleReceiver` (6 системных событий, `goAsync`), `MediaPlayer` + фолбэк `ToneGenerator`, вибрация, уведомления (FSI), очередь, автостоп 30 мин, crash re-arm (+3 с).
* **`:feature:ringing`**: минимальный `RingingActivity` (showWhenLocked/turnScreenOn), время/метка, «Отключить», «Отложить (N)».
* **Логи** тег `Balarm`, формат `EVENT key=value` без пользовательских данных — основа скилла `verify-alarm-reliability`.
* **Новые feature-флаги:** нет (snooze уже `feature.snooze`; звонок — ядро, FR-FLAG-4).

## Решения на утверждение
- [x] **minSdk 34 (Android 14+)** — решение пользователя 2026-09-29; ветки для API 26–33 не нужны.
- [x] **ADR-004** — Room 2.8.5 (не room3), схема v1, миграции через `SQLiteConnection` (совместимо с room3), DataStore — позже.
- [x] **ADR-005** — бэкап и перенос между устройствами выключены до M8 (`allowBackup=false` + `dataExtractionRules`, исключающие все домены). Исправляет заметку M0: авто-бэкап *включает* DE-домены.
- [x] **ADR-006** — модель планирования: `AlarmId.UNSAVED = 0`, DST-правило, `SystemZoneClock`, догон пропуска ≤ 10 мин (CATCH_UP), snooze входит в M1.
- [x] **ADR-007** — сессия звонка: FGS + WakeLock на всю сессию, очередь, автостоп, crash re-arm; до онбординга (M3) `MainActivity` сама запрашивает `POST_NOTIFICATIONS` (без него на API 33+ звук есть, экрана звонка нет).
- [x] **ADR-008** — платформенный `MediaPlayer` (не Media3), резерв `ToneGenerator`; `alarm_default.ogg` — собственный сгенерированный звук.
- [x] **8 разрешений** в манифест и allowlist: `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `VIBRATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, `POST_NOTIFICATIONS`.
- [x] **Эмуляторы:** API 37 (есть) + API 34 (минимальный, ≈ 1.5 ГБ) — строго по одному.

## Задачи
| ✓ | ID | Задача | FR/NFR | Оценка | Зависит от | Commit |
|---|---|---|---|---|---|---|
| [x] | M1-T01 | build-logic: `balarm.android.room`, `balarm.kover`, `balarm.android.feature`; каталог | NFR-6 | M | — | |
| [x] | M1-T02 | `:core:model`: `AlarmId.UNSAVED`, `Alarm`, `SnoozeSettings`, `AlarmRuntimeState` | — | S | — | |
| [x] | M1-T03 | `:core:domain`: `NextTriggerCalculator`, `SystemZoneClock` + табличные DST/TZ-тесты 🔔 | FR-REL-4 | M | T01, T02 | |
| [x] | M1-T04 | `AlarmEngine` ч.1: save/setEnabled/delete/rescheduleAll, CATCH_UP 🔔 | FR-REL-1…3 | M | T03 | |
| [x] | M1-T05 | `AlarmEngine` ч.2: onFired/snooze/dismiss, `RingingController` 🔔 | FR-EDIT-7, FR-RING-3 | M | T04 | |
| [x] | M1-T06 | `:core:data`: Room в DE-storage, DAO, репозиторий, схема, тесты 🔔 | FR-REL-2, ADR-001 | M | T01, T04 | |
| [x] | M1-T07 | `:core:alarm`: `AlarmSchedulerImpl`, лог событий 🔔 | FR-REL-1 | M | T04 | |
| [x] | M1-T08 | `RescheduleReceiver` (6 событий) + разрешения + тест манифеста 🔔 | FR-REL-2, 3 | M | T06, T07 | |
| [x] | M1-T09 | Каналы уведомлений: ringing / fallback / missed, FSI | FR-RING-1 | S | T07 | |
| [x] | M1-T10 | Звук (`MediaPlayer` → `ToneGenerator`) и вибрация 🔔 | FR-REL-8, FR-SND-7 | M | T07 | |
| [x] | M1-T11 | `AlarmReceiver` + `RingingService` (FGS, WakeLock, команды, fallback) 🔔 | FR-RING-1, FR-REL-5 | L | T05, T08, T09, T10 | |
| [x] | M1-T12 | Очередь, автостоп 30 мин, crash re-arm 🔔 | FR-RING-6, 7, NFR-5 | M | T11 | |
| [x] | M1-T13 | 🎨 `:feature:ringing`: минимальный экран звонка (agent: ui-developer) | FR-RING-2 | M | T01, T05 | |
| [x] | M1-T14 | `:app`: Hilt-связки, запрос `POST_NOTIFICATIONS`, rescheduleAll при запуске, ADR-005 🔔 | FR-REL-2 | S | T06, T12, T13 | |
| [x] | M1-T15 | `DebugAlarmReceiver`, обновление скилла `verify-alarm-reliability` | — | S | T14 | |
| [x] | M1-T-test | Тестирование этапа (tester) + R-матрица | — | L | T15 | |
| [ ] | M1-T-review | Ревью этапа (reviewer) | — | M | T-test | |
| [ ] | M1-T-docs | PRD-правки, ADR-004…008 → Accepted | — | S | T-review | |

### M1-T01 — build-logic
**Описание:** `balarm.android.room` (KSP, `room { schemaDirectory }`, runtime/compiler/testing, `schemas/` в assets тестов); `balarm.kover` (Kover 0.9.9, `koverVerify` строк ≥ 80 %, в `check`); `balarm.android.feature` (library + compose + hilt + lifecycle-viewmodel-compose + hilt-navigation/viewmodel + `:core:designsystem`). Каталог: room 2.8.5, `androidx.sqlite:sqlite-framework`, kover, `javax.inject`.
**Модули:** `build-logic`, `gradle/libs.versions.toml`
**Критерии приёмки:**
- [ ] Плагины резолвятся; `./gradlew build` зелёный
- [ ] Kover подключается только к `:core:domain`
**Тесты:** проверяются сборкой T03/T06/T13

### M1-T02 — Модель
**Описание:** `AlarmId(value >= 0)` + `UNSAVED`/`isSaved`; `Alarm` (time без секунд, repeatDays, label, enabled, vibrate, snooze), `SnoozeSettings(interval?, maxCount?)`, `TriggerKind`, `AlarmRuntimeState`.
**Модули:** `:core:model`
**Критерии приёмки:**
- [ ] Инварианты через `require` (время без секунд, лимиты snooze)
- [ ] Обновлён `AlarmIdTest`
**Тесты:** unit на инварианты

### M1-T03 — `NextTriggerCalculator`, `SystemZoneClock` 🔔
**Описание:** `next(time, repeatDays, after, zone): Instant` — строго после `after`; DST gap → момент перехода; overlap → первое наступление; `SystemZoneClock` берёт `ZoneId.systemDefault()` на каждый вызов.
**Модули:** `:core:domain`
**Критерии приёмки:**
- [ ] Все кейсы из таблицы ниже зелёные; Kover по классу ≥ 90 %
**Тесты:** табличные: 7 дней, разовый сегодня/завтра, «ровно сейчас», конец месяца/года, 29 февраля; Europe/Berlin 2026-03-29 02:30 → 03:00 и 2026-10-25 02:30 → первое наступление, без второго звонка; Asia/Kolkata, Australia/Lord_Howe, Pacific/Apia 2011-12-30, Pacific/Kiritimati; `SystemZoneClock` видит `TimeZone.setDefault`.

### M1-T04 — `AlarmEngine` ч.1 🔔
**Описание:** интерфейсы `AlarmRepository`, `AlarmScheduler`, `AlarmEventLog`; `save/setEnabled/delete/rescheduleAll(reason)`; CATCH_UP: пропуск ≤ 10 мин → звонок через 3 с; идемпотентность; `Mutex`. Фейки для тестов.
**Критерии приёмки:**
- [ ] Любое изменение будильника синхронизирует расписание (schedule/cancel)
- [ ] `rescheduleAll` дважды подряд (LOCKED_BOOT → BOOT) не создаёт дубликатов
**Тесты:** unit на фейках + изменяемом `Clock`: смена зоны, CATCH_UP внутри/за пределами окна, выключенный будильник не планируется

### M1-T05 — `AlarmEngine` ч.2 🔔
**Описание:** `onFired` (разовый → выключить; повторяющийся → сразу следующий REGULAR; устаревший > 10 мин → Skip), `snooze` (лимит, `Feature.SNOOZE`), `dismiss(USER|AUTO_STOP)`; `RingingController`/`RingingState`; при исключении в движке звонок всё равно происходит с дефолтами.
**Критерии приёмки:**
- [ ] Kover `:core:domain` ≥ 80 % (NFR-6), `koverVerify` в `check`
**Тесты:** лимит snooze, snooze при выключенном флаге → NotAllowed, RESUME не трогает runtime, исключение репозитория → Ring

### M1-T06 — `:core:data` 🔔
**Описание (дополнено ревью T01–T03):** маппер entity → domain *толерантный* — значения вне диапазона (label, snooze, hour/minute) приводятся `coerceIn`/`take`, одна «битая» строка не должна ронять `rescheduleAll` на LOCKED_BOOT (тест «мусорная строка не мешает остальным будильникам»); `AlarmId` — через маппер, не в Entity. **Описание:** `BalarmDatabase` на `createDeviceProtectedStorageContext()` (`@DeviceProtected Context`), `AndroidSQLiteDriver`, entity/DAO/маппер, `RoomAlarmRepository`, экспорт `schemas/1.json`, Hilt-модуль. `fallbackToDestructive*` запрещён.
**Критерии приёмки:**
- [ ] Файл БД лежит в DE-storage (тест `isDeviceProtectedStorage` / путь `user_de`)
- [ ] Схема v1 экспортирована и в git
**Тесты:** Robolectric — DAO CRUD, каскадное удаление runtime, `MigrationTestHelper` для v1 (фолбэк — инструментальный тест)

### M1-T07 — `AlarmSchedulerImpl` 🔔
**Описание:** `setAlarmClock(AlarmClockInfo(triggerAt, showIntent → список), PI)`; PI: `data=balarm://alarm/<id>`, `FLAG_IMMUTABLE|UPDATE_CURRENT`, extras `scheduledFor`/`kind`; `canScheduleExactAlarms()==false` → `false` без падения; `LogcatAlarmEventLog`; `AlarmUiIntents` (интерфейс, реализация в `:app`).
**Критерии приёмки:**
- [ ] Используется только `setAlarmClock` (grep-проверка в ревью)
**Тесты:** Robolectric `ShadowAlarmManager`: время, alarmClockInfo, уникальность PI на id, cancel, отказ в exact alarm

### M1-T08 — Перепланирование и манифест 🔔
**Описание:** `RescheduleReceiver` (directBootAware, exported=false где возможно): LOCKED_BOOT_COMPLETED, BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED, MY_PACKAGE_REPLACED, LOCALE_CHANGED → `goAsync` + `rescheduleAll`; никогда не стартует звонок. 8 разрешений в манифест и allowlist (со ссылками на FR). `SCHEDULE_EXACT_ALARM` не объявляется (minSdk 34, `USE_EXACT_ALARM` выдаётся при установке); ресивер `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` не нужен.
**Критерии приёмки:**
- [x] Каждый action → `rescheduleAll` с правильной причиной
- [x] `check-permissions.sh` зелёный
**Тесты:** Robolectric ресивера; тест merged-манифеста: цепочка звонка `directBootAware`, всё кроме debug `exported=false`, `foregroundServiceType=systemExempted`

### M1-T09 — Уведомления
**Описание:** каналы `alarm_ringing` (IMPORTANCE_HIGH, без звука — звук играет сервис), `alarm_fallback` (USAGE_ALARM, INSISTENT, FSI), `alarm_missed`; билдер уведомления звонка (CATEGORY_ALARM, FSI + content → экран звонка, действия «Отключить»/«Отложить», FGS_IMMEDIATE). Строки RU/EN.
**Тесты:** Robolectric — атрибуты канала и уведомления

### M1-T10 — Звук и вибрация 🔔
**Описание:** `AlarmSoundPlayer`: MediaPlayer, `USAGE_ALARM`, loop, `res/raw/alarm_default.ogg` (собственный сгенерированный, CC0), если за 1 с не стартовал → `ToneGenerator`; `STREAM_ALARM` громкость сохраняется/восстанавливается; `AlarmVibrator` (паттерн, USAGE_ALARM).
**Критерии приёмки:**
- [x] Звук на потоке ALARM (слышен в беззвучном режиме, R11) — unit-уровень; на эмуляторе — в T-test
- [x] Лицензия звука записана в `docs/LICENSES.md`
- [x] Канал `alarm_fallback` звучит `android.resource://<pkg>/raw/alarm_default` (не системный URI — до разблокировки недоступен); убран TODO в `AlarmNotificationChannels`; на dev-устройствах канал пересоздать переустановкой
**Тесты:** `ShadowMediaPlayer` — атрибуты, фолбэк при ошибке

### M1-T11 — `AlarmReceiver` + `RingingService` 🔔
**Описание:** ресивер: статический WakeLock 60 с → `startForegroundService` до любого I/O; при исключении старта FGS → fallback-уведомление. Сервис: `ServiceCompat.startForeground` сразу с типом `SYSTEM_EXEMPTED`, WakeLock сессии, `engine.onFired`, звук+вибрация, `RingingControllerImpl`, команды DISMISS/SNOOZE (из экрана и уведомления).
**Критерии приёмки:**
- [x] Событие `RINGING_STARTED` в логе ≤ 2 с после срабатывания — watchdog (unit); замер на эмуляторе — T-test
- [x] WakeLock освобождается во всех путях (в т.ч. исключения)
**Тесты:** Robolectric — startForeground с FSI и CATEGORY_ALARM, fallback при отказе FGS, dismiss/snooze останавливают звук

### M1-T12 — Очередь, автостоп, crash re-arm 🔔
**Описание:** второй будильник во время звонка → QUEUED, звонит после первого (FR-RING-7); автостоп 30 мин → `dismiss(AUTO_STOP)` + уведомление «пропущен» (FR-RING-6); uncaught exception → `setAlarmClock(now+3 с, RESUME)` (NFR-5).
**Тесты:** Robolectric на виртуальном времени — очередь, автостоп; unit — RESUME

### M1-T13 — 🎨 Экран звонка (agent: ui-developer)
**Описание:** `:feature:ringing`: `RingingActivity` (directBootAware, showWhenLocked, turnScreenOn, singleTask, excludeFromRecents, keepScreenOn, скрытые системные бары), `RingingViewModel` ← `RingingController.state`, экран по `alarmy-ui`: дата, время `timeHuge`, метка, «Отложить (N)» (скрыта при выключенном флаге/лимите), пульсирующая «Отключить». Back и громкость поглощаются. `Idle` → `finish()`. Строки RU/EN.
**Критерии приёмки:**
- [x] Превью: тёмная тема, fontScale 2f, 360dp
**Тесты:** Compose (Robolectric): snooze скрыта при флаге/лимите, dismiss → команда, RU/EN

### M1-T14 — Сборка в `:app` 🔔
**Описание:** Hilt-связки модулей, `AlarmUiIntents` impl, `@ApplicationScope`; `MainActivity`: `rescheduleAll(APP_LAUNCH)` и временный запрос `POST_NOTIFICATIONS` (до онбординга M3); ADR-005: `dataExtractionRules` + `fullBackupContent` исключают всё.
**Критерии приёмки:**
- [x] Приложение собирается и запускается на эмуляторе, разрешение запрашивается один раз

### M1-T15 — Debug-инструменты
**Описание:** `DebugAlarmReceiver` (только `src/debug`): `SCHEDULE_IN` (minutes/seconds/label/days), `LIST`, `DISMISS`, `SNOOZE`, `RESCHEDULE_ALL`, `CLEAR_ALL`, `CRASH`. Обновить скилл `verify-alarm-reliability`: `adb install -g`, первый запуск приложения после установки (stopped state), проверка `ro.crypto.type=file` для R3, актуальные имена событий, R19.
**Критерии приёмки:**
- [x] `check-permissions.sh` подтверждает, что `DebugAlarmReceiver` не попал в release

### M1-T-test — Тестирование (agent: tester)
Unit/Robolectric полностью. R-матрица (эмуляторы строго по одному, headless, `-memory 2048`; образ ставится → тест → удаляется):
| API | Сценарии |
|---|---|
| 37 | R1, R2, R3, R4, R5, R6, R7, R8, R9, R11, R13, R19, debug `CRASH` |
| 34 | R1, R2, R3, R5, R11; поведение без `POST_NOTIFICATIONS` и с отозванным FSI |

### M1-T-review — Ревью (agent: reviewer)
### M1-T-docs — Документация
PRD: NFR-5 (+ crash re-arm), FR-REL-5 (WakeLock на всю сессию), §6.4 `AlarmRuntimeState`, §3.7/FR-RING-1 (без уведомлений нет экрана звонка), FR-RING-6 (константа в M1), FR-REL-6 (CATCH_UP ≤ 10 мин), §11.1 закрыт; ADR-003 — `balarm.android.feature` в M1; reviewer — «уникальная идентичность PendingIntent (data URI)»; ADR-004…008 → Accepted; исправить заметку M0 о бэкапе.

## Риски
| Риск | Митигация |
|---|---|
| `Clock.systemDefaultZone()` «застревает» в старой зоне | `SystemZoneClock`, unit-тест, R5 |
| java.time по умолчанию сдвигает DST-gap на 03:30 | явная обработка, тесты Berlin, R7 |
| Нет `POST_NOTIFICATIONS` → нет экрана звонка | запрос в `MainActivity`, `-g` в тестах, онбординг в M3 |
| Падение UI убивает процесс с сервисом | crash re-arm + debug `CRASH`; отдельный процесс — запасной вариант к M5 |
| I/O в ресивере съедает окно старта FGS | FGS стартует первым; R2 (Doze) |
| Двойное срабатывание | хранение `nextTriggerAt`/kind, идемпотентный `rescheduleAll` |
| Robolectric + `MigrationTestHelper` в AGP 9 | фолбэк — инструментальный тест |
| Stopped state после `adb install` | первый запуск в скилле |
| Память при тестах на эмуляторах | один эмулятор, `./gradlew --stop` перед запуском, `adb emu kill` после |
| Диск: образ API 34 ≈ 1.5 ГБ | один дополнительный образ |

## Добавлено по ходу
- **2026-09-29:** поддержка только Android 14+ → `minSdk 34` (каталог, PRD NFR-1, ADR-003 поправка, CLAUDE.md). Убраны ветки API 26–33, `SCHEDULE_EXACT_ALARM`, эмуляторы 26/29/31/33. ADR-002/006/007/008 упоминают API 26–33 — вычистить в T-docs.
- **2026-09-29:** первый CI-прогон M0 упал на `android-actions/setup-android@v3` (SDK на `ubuntu-latest` уже есть). Шаг убран, экшены обновлены (checkout v7, setup-java v6, setup-gradle v6, upload-artifact v7).

- **Ревью T01–T03 (⚠️ Approve with comments):** добавлены 6 граничных DST-тестов (overlap между наступлениями, `after` = момент перехода, повторяющийся в день gap, New York, Havana gap в полночь, Apia + повтор по пятницам); уточнён критерий «выпавшего дня» (разрыв накрывает весь день); KDoc про порядок применения `balarm.android.room`. Не исправлено (nit): длина label в UTF-16, а не code points — учесть в редакторе M2.
- **Ревью T04–T05 (❌ Request changes → исправлено):** snooze при отказе системы теперь `NotAllowed` без записи runtime; `rescheduleAll` изолирует ошибки по будильникам и не считает отказы; операции движка `NonCancellable`; `delete` сначала БД, потом `cancel`; `onFired` ждёт движок ≤ 2 с (иначе degraded-звонок), ошибка планирования следующего срабатывания не портит текущий звонок; отложенный звонок ограничивается при переводе часов назад; разовый, пропущенный > 10 мин, выключается (`SkipReason.MISSED`, уведомление — M7); повторный snooze не тратит лимит; перевод часов назад после звонка не даёт второго звонка; повторная доставка → `SkipReason.DUPLICATE`. +15 тестов (`AlarmEngineRobustnessTest`), фейки с внедрением ошибок. Повторное ревью движка — в M1-T-review.
- **T06:** `MigrationTestHelper` под Robolectric работает (риск не сработал); маппер толерантен к «битым» строкам (тест).
- **Ревью T08 (⚠️ Approve with comments → исправлено):** сбой лога в `catch` ресивера не роняет процесс; рефлексия над Robolectric вынесена в `BroadcastTesting.kt` с пометкой версии. **В T14:** проверить, что ни один `@Provides` графа Hilt не трогает CE-storage (`filesDir`, обычный DataStore/SharedPreferences) — `RescheduleReceiver` инжектится на LOCKED_BOOT; подтверждается R3. **В T-docs:** ADR-001 §3 и ADR-006 §7 упоминают ресивер/причину exact-alarm permission changed (`EXACT_ALARM_PERMISSION_GRANTED`) — на minSdk 34 с `USE_EXACT_ALARM` не нужны, убрать.
- **Ревью T09 (⚠️ Approve with comments → исправлено):** fallback-уведомление снимается через `RingingPolicy.AUTO_STOP_AFTER` (общая с T12 константа в `:core:domain`); `RingingActions` — отдельный файл; локаль времени — из конфигурации контекста; тесты 24h/12h, fallback autoCancel/не ongoing, каналы из DE-контекста. Конфликт план↔ADR-007 §2.1 по кнопке «Отключить»: обе кнопки nullable, в M5 для будильников с миссией dismiss = `null`. **В T11:** у fallback нет кнопки «Отключить» (только смахнуть/нажать/автостоп) — зафиксировать в ADR-007 §6 (T-docs). **В M3:** отозванный `USE_FULL_SCREEN_INTENT` (`canUseFullScreenIntent()`) → heads-up без экрана — статус в `PermissionHealthChecker`. **В T10:** звук fallback-канала — встроенный (критерий приёмки T10).
- **T-test (tester, 2026-10-03):** 239 unit/Robolectric — зелёные; Kover `:core:domain` 85,5 %. API 37: R1, R2, R3, R4, R5, R6, R8, R9, R11, R13, CRASH ✅; окно 5 с после reboot, «Home → шторка → тап», «snooze → сразу следующий», Hilt в ресиверах на LOCKED_BOOT/BOOT ✅. API 34: R1, R2, R3, R5, R11, без `POST_NOTIFICATIONS` (звук есть, экрана нет), отозванный FSI ✅; образ удалён. ⏭: R7 (ручной), AUTO (unit), слышимость (эмулятор без звука). Находки:
  - **R19 — открытый риск:** на каждом звонке `AudioHardening … would be muted … USAGE_ALARM … exemption: 4`, `mutedState:none`; сервис стартует из фона (FGS без while-in-use), спасает исключение «exact alarm + USAGE_ALARM» (документация: «exact alarm permission» — `USE_EXACT_ALARM` должен считаться). Режим `throw` в образе отсутствует — **перепроверить на образе/устройстве с `set-enable-hardening throw` до релиза**; в T-docs — ADR-008.
  - Два падения подряд за < 60 с → система «crashed too many times», процесс остановлен, alarms сняты — RESUME не срабатывает. Ограничение платформы → ADR-007 §7 (T-docs).
  - Enter с клавиатуры нажимает сфокусированную «Отложить» → **M5**: начальный фокус/обработка клавиш на экране звонка вместе с миссиями.
  - DND Total Silence глушит STREAM_ALARM и экран — платформа → предупреждение в `PermissionHealthChecker` (**M3**).
  - `DebugAlarmReceiver` не принимал команды до разблокировки → `directBootAware` (debug). Скилл: обход Doze для R2, `/data/local/tmp` для R3, `set_dnd priority` для R11, ожидания R19 и CRASH.
- **T15 — первый сквозной прогон на эмуляторе API 37:** будильник через `DebugAlarmReceiver` → `ALARM_FIRED` → `RINGING_STARTED` за 56–71 мс (критерий ≤ 2 с), звук `raw`; «Отключить» и `CRASH` → `CRASH_REARMED` → звонок вернулся через ~5 с; разовый после RESUME выключен. **Найден и исправлен баг T09:** уведомление звонка с `setSilent(true)` получало флаг SILENT, и система не запускала full-screen intent — экран не включался (R1). Регрессионный тест на флаг. **UI-замечание (M2/M5):** при первом показе экрана звонка Android выводит подсказку «Viewing full screen» поверх него из-за скрытых системных панелей — решить, скрывать ли панели на экране звонка. После возврата из падения при включённом разблокированном экране — только heads-up (ожидаемо до overlay в M3). `SCHEDULE_IN` округляет до целой минуты.
- **T14:** эмулятор API 37 — установка и запуск без крэшей, `RESCHEDULE_ALL reason=APP_LAUNCH`, диалог `POST_NOTIFICATIONS` один раз; `RescheduleReceiver` получил LOCKED_BOOT/BOOT — Hilt-инъекция в ресивер на устройстве подтверждена. `DebugFeatureFlagProvider` уже на DE-storage (опасение ревью T11 снято); граф Hilt без обращений к CE. `fullBackupContent` не нужен (minSdk 34).
- **Ревью T14 (⚠️ → исправлено):** `SafeRescheduler` — ошибка `rescheduleAll` на запуске/событии логируется, не роняет приложение; сторож `goAsync` (8 с) отпускает broadcast при зависшей БД; `@ApplicationScope` на IO с `CoroutineExceptionHandler`; переход на экран звонка — в `onStart`, решение — чистая функция с тестом. **Известно:** «запрос разрешения один раз» — на каждый холодный запуск без разрешения (после двух отказов система сама перестаёт показывать) — до онбординга M3.
- **T13 (ui-developer):** до первого `Ringing` экран в фазе WAITING (часы без кнопок), закрывается через 5 с; после `Ringing` → `Idle` — сразу (FSI публикуется раньше решения движка). В дизайн-систему добавлены `PrimaryButton` (пульсация) / `SecondaryButton`, токен `ButtonHeightLarge`. Подпись «Отложить (N)», `queued`/цитата/градиент не выводятся.
- **Ревью T13 (⚠️ → исправлено):** ICU-шаблон времени/даты в редких локалях может не подойти `java.time` → запасной формат (экран и уведомление звонка), тест по всем локалям. **В M5:** Home во время звонка — экран возвращается только тапом по уведомлению (`excludeFromRecents`, без переоткрытия); FR-RING-4 «экран переоткрывается» — вместе с миссиями/overlay. **В T-test:** окно ожидания 5 с против холодного старта после reboot (R3); «Home → шторка → тап»; «snooze → сразу следующий будильник» (`singleTask`, без `onNewIntent`). **В M7 (a11y):** пульсация «Отключить» без учёта системной настройки «убрать анимации».
- **T12:** dismiss/snooze/автостоп выполняются сразу, вне очереди срабатываний (закрыто замечание ревью T11). Crash re-arm: RESUME заменяет в системе обычное следующее срабатывание будильника (один PendingIntent на id) — его возвращает `dismiss` после возобновлённого звонка. Движок при RESUME выключает разовый, если тот не успел выключиться до падения.
- **Ревью T12 (❌ Request changes → исправлено):** таймер автостопа отменял собственную корутину (запись спасал только `NonCancellable` движка) → запись в отдельной корутине; снимок `rearmIds` для обработчика падения на любом потоке; снятый `CrashGuard` только делегирует; `stopIfIdle` сначала `stopSelfResult`; «пропущен» снимается при новом звонке. **В T-docs (ADR-007 §7):** повторные падения дают RESUME каждые ~3 с без ограничения — осознанно («лучше звонить»); гард ставится только после `startForeground` — падение раньше (Hilt, `onCreate`) не покрыто.
- **T11:** очередь FR-RING-7 частично сделана здесь (срабатывание сразу фиксируется движком, в очередь — готовое решение); T12 — автостоп, crash re-arm, тесты очереди. Watchdog: нет решения движка за 2 с → звук досрочно (`RINGING_STARTED degraded=true`). Фейки движка вынесены в `testFixtures` `:core:domain`; Hilt-тесты `:core:alarm` — общий `TestAlarmModule` (`@TestInstallIn`). mockk не подходит для value class `AlarmId` (случайный отрицательный id → `require`). При Skip экран звонка может мелькнуть (FSI на первом уведомлении) — осознанно.
- **Ревью T11 (❌ Request changes → исправлено):** ошибки dismiss/snooze/notify не роняют процесс (`RINGING_COMMAND_FAILED`); единая точка остановки сервиса; отказ FGS не трогает идущий звонок; повторный startForeground — с полным уведомлением; +8 тестов. **В T14:** `DebugFeatureFlagProvider` читает CE SharedPreferences — в Direct Boot (debug) упадёт → DE-контекст или дефолты до разблокировки; без `POST_NOTIFICATIONS` экрана нет — запуск приложения при `RingingState.Ringing` должен вести на экран звонка (T13/T14). **Повторное ревью T11 (⚠️ → исправлено):** сбой `onFired` → degraded-звонок; узкий `try` вокруг `startForeground`; после `onDestroy` сервис не трогает себя. **В T12:** «Отключить» ждёт в очереди команд, пока движок решает по второму срабатыванию (≤ 2 с + БД) — вынести dismiss/snooze из последовательного цикла или принять. **В T-test:** на устройстве подтвердить Hilt-инъекцию в ресиверы при холодном старте/boot.
- **T10:** звук — WAV (`alarm_default.wav`, 50 КБ, синтез `tools/sound/generate_alarm_default.py`), а не OGG: на машине нет кодировщика. Громкость `STREAM_ALARM` не трогаем — по ADR-008 §6 это M4 (FR-SND-7). **Известный риск M1:** при громкости будильника 0 звонок беззвучный; громкость пишется в `SOUND_STARTED volume=…`, статус — экран здоровья (M3). **В T-test:** на эмуляторе проверить слышимость `TONE_CDMA_ALERT_CALL_GUARD` (альтернативы — `TONE_CDMA_EMERGENCY_RINGBACK`, `TONE_CDMA_ABBR_ALERT`); перед R11 переустановить приложение (звук канала `alarm_fallback` фиксируется при создании).
- **Ревью T10 (⚠️ Approve with comments → исправлено):** утечка `MediaPlayer` при исключении в `setDataSource`; `@Singleton` у плеера и вибратора; вибратор не бросает (событие `VIBRATION_FAILED`); явный флаг фолбэка; +5 тестов (колбэки заменённого плеера, ошибка во время воспроизведения, повторный фолбэк, вибратор).
- **T08:** ~~разрешения объявлены в манифесте `:app`~~ → в T10 перенесены в `:core:alarm` (lint `MissingPermission` проверяет манифест модуля; аудит — allowlist по merged-манифесту `:app` после T14); скоуп корутин ресивера — приватный до `@ApplicationScope` (T14).
- **Metaspace:** после серии сборок демон упал с `OutOfMemoryError: Metaspace` на KSP (лимит 768 МБ из `gradle.properties`). На свежем демоне сборка проходит — перед полной сборкой после длинной серии делать `./gradlew --stop`. Поднимать лимит — только с согласия пользователя.

## Уроки

## Перенесено в следующий этап
