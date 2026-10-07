# ADR-015: Статус «не запланирован» в runtime (схема v2); флаг «звонит» (R20) — вне M3

## Status
Proposed (2026-10-07, план M3). Закрывает компромисс ADR-011 §11 Б; дополняет ADR-004 §4–§5 (схема v2) и ADR-011 §2 (`upcomingTrigger`). Перенос R20 из M2 (ADR-007, «Уточнения по итогам M1» §7) — решение ниже.

## Context
* ADR-011 §11 Б: при `scheduled = false` будильник сохранён включённым, а `alarm_runtime.next_trigger_at` хранит момент «для повтора» — список и шапка «Следующий через …» показывают будильник, которого нет в `AlarmManager`. M3 обязан показать это (баннер, экран здоровья).
* ADR-004 §4: `next_trigger_at` — «ровно то, что стоит в AlarmManager». При отказе это уже не так, а признака в БД нет.
* Причины отказа на minSdk 34: `canScheduleExactAlarms() == false` (с `USE_EXACT_ALARM` практически не бывает) или `SecurityException`. Пункт EXACT_ALARMS экрана здоровья (ADR-012) покрывает первую, но не вторую и не говорит, **какой** будильник не стоит.
* Проверить наличие alarm у системы нельзя: `PendingIntent` с `FLAG_NO_CREATE` существует и после срабатывания, `getNextAlarmClock()` отдаёт ближайший будильник любого приложения.
* R20: crash re-arm ставит RESUME тем же `PendingIntent`, а в БД остаётся обычный `next_trigger_at`; `rescheduleAll` в окне ~3 с (например, `APP_LAUNCH` при открытии приложения после падения) затирает RESUME; два падения за < 60 с — платформа снимает alarms. Лечение — персистентный признак «идёт звонок» и ветка RESUME в `restoredPlan`.

## Decision
1. **Схема v2:** `ALTER TABLE alarm_runtime ADD COLUMN schedule_failed INTEGER NOT NULL DEFAULT 0` через `@AutoMigration(from = 1, to = 2)`; тест `MigrationTestHelper` 1 → 2 (данные v1 сохраняются, флаг 0). `fallbackToDestructiveMigration` по-прежнему запрещён.
2. **Модель:** `AlarmRuntimeState.scheduleFailed: Boolean = false`.
3. **Движок** (`applySchedule`): `SCHEDULED`/`CANCELLED` → `scheduleFailed = false`; `FAILED` при `persistOnFailure = true` → runtime с моментом «для повтора» и `scheduleFailed = true`. Отказ snooze (`persistOnFailure = false`) флаг не трогает — прежнее обычное расписание осталось в системе. Любой `rescheduleAll` (boot, time, `APP_LAUNCH`, новый `USER_RETRY` с экрана здоровья) повторяет попытку и снимает флаг при успехе. Тест (`AlarmId.TEST`) в БД не пишет — флага у него нет (результат — эффект ViewModel).
4. **Чтение для UI** (ADR-011 §2 уточняется): `upcomingTrigger(now)` → `null`, если `scheduleFailed`; `nextTrigger` (шапка) такие будильники не учитывает; `isActive` не меняется (тумблер «вкл» — это намерение пользователя); подзаголовок карточки — `AlarmSubtitle.NotScheduled` («Не запланирован»). `List<AlarmWithRuntime>.unscheduledCount()` → пункт SCHEDULING отчёта здоровья (ADR-012) и баннер.
5. **R20 в M3 не входит.** Перенос в M5 (миссии — главный новый источник падений посреди звонка; там же R16/R17 и пересмотр `CrashGuard`) отдельным ADR-дополнением к ADR-007: колонка «звонит» (`ringing_since`/`ringing_for`), запись в `recordFire`/`resume`, очистка в `dismiss/snooze/автостоп`, ветка RESUME в `restoredPlan`, схема v3 (AutoMigration). Объединять с v2 незачем: AutoMigration дешёвая, а R20 — изменение цепочки звонка с собственной матрицей проверок, не связанное с онбордингом.

## Alternatives considered
* **Флаг в памяти процесса** (`StateFlow<Set<AlarmId>>`, без миграции). Теряется со смертью процесса; восстановленная Activity (`savedInstanceState != null`) не вызывает `APP_LAUNCH`, и список снова показывает «через 7 ч» при пустом `AlarmManager`. Отклонено (дешевле на одну миграцию, но нечестно ровно в том случае, ради которого делается).
* **При отказе писать `next_trigger_at = null`.** Признак «включён, но null» без новой колонки; но теряется ожидающий CATCH_UP, а `null` уже означает «выключен/нечего планировать». Отклонено.
* **Только пункт EXACT_ALARMS** (`canScheduleExactAlarms`) без признака в БД. Не покрывает `SecurityException`, не показывает, какой будильник не стоит, шапка продолжает врать. Отклонено.
* **R20 в M3 вместе со схемой** — расширяет этап изменением цепочки звонка; M3 и так меняет `RingingService` (overlay, ADR-012 §7). Отклонено, см. п. 5.

## Consequences
* (+) Инвариант ADR-004 «runtime = то, что в AlarmManager» восстановлен явным признаком; шапка и карточка не обещают несуществующий звонок; баннер видит отказ после смерти процесса.
* (−) Первая миграция схемы — проверка механизма `schemas/` + `MigrationTestHelper` на AGP 9 (риск ADR-004).
* (−) R20 остаётся известным риском до M5 (PRD §9.2, NFR-5).
* Проверка: Robolectric — миграция 1 → 2; `ShadowAlarmManager` с `canScheduleExactAlarms = false` → `save` → `scheduleFailed = true` → список: подзаголовок «Не запланирован», шапка без него, баннер; разрешение вернули → `rescheduleAll(USER_RETRY)` → флаг снят; unit — движок на фейке планировщика с отказом (save, setEnabled, rescheduleAll, snooze не трогает флаг); эмулятор — R9 (`adb install -r` поверх v1: будильники на месте, миграция без потерь), R3.

## Related
ADR-004 §4–§5, ADR-006, ADR-007 §7, ADR-010, ADR-011 §2, §11 Б, ADR-012; FR-LIST-2, FR-LIST-5, FR-EDIT-11, FR-REL-7, NFR-5; R3, R9, R12, R20.
