# ADR-020: Схема Room v4: миссия будильника и персистентный признак «звонит» (R20)

## Status
Proposed (2026-10-10, план M5). **Изменяет ADR-004 §4** в части «`isRinging` не персистится» и закрывает ограничение ADR-007 (уточнения M1, §7: `rescheduleAll` затирает RESUME). Остальное в ADR-004/ADR-007 действует.

## Context
* ADR-018: миссия хранится как `MissionSpec(type, config JSON)` в строке `alarm` — на звонке ноль дополнительных запросов, данные в DE (ADR-001).
* R20 / NFR-5: crash re-arm ставит `setAlarmClock(now + 3 с, RESUME)` поверх обычного `PendingIntent` будильника. Любой `rescheduleAll` в окне ~3 с (например, `APP_LAUNCH`, если пользователь открыл приложение) перепланирует обычное следующее срабатывание и **затирает RESUME** — звонок не возвращается. Кроме того, смерть процесса без обработчика (SIGKILL, LMK, перезагрузка посреди звонка) сейчас не возвращает звонок вовсе: `nextTriggerAt` уже указывает на следующий день, догон FR-REL-6 не срабатывает.
* PRD §6.4 и ADR-016 §6 отложили флаг «звонит» в схему v4; M4 занял v3. M5 и так меняет схему (миссия) — одна миграция дешевле двух.
* Миссии добавляют в процесс новый источник падений (UI миссии), поэтому R20 в M5 становится актуальнее.

## Decision
1. **Схема v4** (AutoMigration 3→4, тест `MigrationTestHelper`, схема в `core/data/schemas/4.json`):
   ```
   alarm          + mission_type   TEXT    NULL      -- MissionTypeId; NULL — без миссии
                  + mission_config TEXT    NULL      -- JSON плагина (ADR-018); NULL при типе → '{}' → defaultConfig
   alarm_runtime  + ringing_since  INTEGER NULL      -- epoch ms начала текущего звонка; NULL — не звонит
   ```
   Маппер: невалидный `mission_type` → `mission = null` + событие лога; чтение будильника не падает.
2. **Модель.** `Alarm.mission: MissionSpec? = null` (ADR-018); `AlarmRuntimeState.ringingSince: Instant? = null`.
3. **Кто пишет `ringing_since`** — только `AlarmEngine` (ADR-006 §8), в тех же транзакциях, что и сейчас:
   * `recordFire` (звонок REGULAR/SNOOZE/CATCH_UP) → `ringingSince = now`; `resume` → ставит, если пусто;
   * `snooze` (успешный), `dismiss(USER | AUTO_STOP)`, `skipStale` → `null`;
   * `degraded`-звонок записывает признак в `recordDegraded`; тест (`AlarmId.TEST`) не хранится — признака нет.
4. **`rescheduleAll` уважает звонок.** В `restoredPlan`: если `ringingSince != null` и `0 ≤ now − ringingSince < RingingPolicy.AUTO_STOP_AFTER` → запросить `ScheduleRequest(id, now + CRASH_RESUME_DELAY, RESUME)` **без** перезаписи `nextTriggerAt` в runtime (как crash re-arm: в БД остаётся обычное следующее срабатывание, его вернёт `dismiss`/`snooze`). Иначе (старше автостопа или часы ушли назад) → `ringingSince = null`, обычный план (уведомление о пропуске — M7). Так звонок возвращается после перезагрузки посреди звонка (LOCKED_BOOT/BOOT), после SIGKILL — при ближайшем `rescheduleAll`, и не теряется, если `APP_LAUNCH` попал в окно crash re-arm.
5. **Сервис идемпотентен к RESUME.** `RESUME` для будильника, который уже `current` или в очереди, — no-op (`FIRE_SKIPPED reason=ALREADY_RINGING`): `APP_LAUNCH` во время живого звонка (п. 4) лишь ставит безвредный RESUME. `crash re-arm` (ADR-007 §7) остаётся — он быстрее и несёт признак `skipMission` (ADR-019 §7).
6. **Без `fallbackToDestructiveMigration`** (ADR-004 §5); миграция 3→4 проверяется на данных v3 (в т.ч. SNOOZE, разовый, своя мелодия).

## Alternatives considered
* **Таблица `alarm_mission`** — см. ADR-018 (отклонено до мульти-миссий).
* **Признак «звонит» в DataStore/SharedPreferences** — без миграции, но второе хранилище состояния движка и гонки с транзакциями runtime. Отклонено.
* **Запись RESUME в `next_trigger_kind`** (новое значение `TriggerKind.RESUME`) — затирает обычное следующее срабатывание в БД; после падения посреди RESUME будильник терял бы завтрашний звонок до `dismiss`. Отклонено: маркер отдельно от плана.
* **Не делать R20 в M5** (оставить известный риск) — миссии увеличивают вероятность падения посреди звонка, а миграция всё равно нужна. Отклонено.
* **`rescheduleAll` спрашивает живой `RingingController`** — меньше лишних RESUME, но домен начинает зависеть от состояния процесса; идемпотентность в сервисе (п. 5) проще. Отклонено.

## Consequences
* (+) Звонок возвращается после перезагрузки посреди звонка (LOCKED_BOOT/BOOT) и не затирается `APP_LAUNCH` в окне crash re-arm (R20). После SIGKILL/LMK без обработчика падения — только при ближайшем `rescheduleAll` (запуск UI, загрузка, смена времени): своего будильника-«сторожа» не ставим (NFR-9).
* (+) Одна миграция на этап; схема миссии готова для любых плагинов без новых версий.
* (−) Лишние no-op RESUME при открытии приложения во время звонка.
* (−) Crash-loop (два падения < 60 с) по-прежнему снимает alarms до следующего запуска — ограничение платформы; уменьшается предохранителем ADR-019 §7.
* Проверка: Robolectric — миграция 3→4, маппер (мусорный `mission_type`), движок (`ringingSince` ставится/снимается во всех путях; `rescheduleAll` в окне звонка даёт RESUME и не трогает `nextTriggerAt`; старый маркер очищается; часы назад), сервис (RESUME для текущего — no-op); эмулятор — R20 (`CRASH` → открыть приложение в окне 3 с → звонок вернулся; `am kill`/`kill -9` посреди звонка → открыть приложение → звонок вернулся), R3 (перезагрузка посреди звонка), R9.

## Related
ADR-001, ADR-004 (изменён §4), ADR-006, ADR-007 (§7), ADR-016 §6, ADR-018, ADR-019; FR-REL-2, FR-REL-6, NFR-5; PRD §6.4; R3, R9, R20.
